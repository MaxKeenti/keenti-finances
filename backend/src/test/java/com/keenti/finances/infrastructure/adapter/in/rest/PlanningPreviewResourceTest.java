package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D5 slice 5B: the preview against real storage.
 *
 * <p>The 5A fixtures prove arithmetic over an already-resolved baseline. These
 * tests prove what that baseline and those receipts are when read from the
 * database: which Transactions are already inside Net Balance, which records a
 * User may select, which statements the timing list sees, and that previewing
 * changes nothing. Money is compared as exact BigDecimal.
 */
@QuarkusTest
class PlanningPreviewResourceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final JsonPathConfig EXACT =
        new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

    @Inject
    EntityManager em;

    /* ---------------------------------------------------------------------
     * Baseline
     * ------------------------------------------------------------------ */

    /**
     * FX-HORIZON-FUTURE-RECORDED-01 and ex.7/ex.10 against storage: a recorded
     * future-dated EGRESS, a full MSI purchase and credit in favor are already in
     * the dashboard's Net Balance; available credit capacity is in nothing.
     */
    @Test
    void baselineIsTheDashboardsAndAlreadyContainsRecordedFutureMsiAndCreditInFavor() {
        String user = newUser("plan-baseline");
        LocalDate today = today();
        activate(user, today.minusDays(120));
        long payroll = createAccount(user, "Nómina", "DEBIT", "10000.00");
        long favor = createAccount(user, "Tarjeta a favor", "CREDIT", "55.50");
        creditSettings(user, favor, "9000.00");
        long card = createAccount(user, "Tarjeta MSI", "CREDIT", "0.00");
        creditSettings(user, card, "20000.00");
        long rent = reserve(user, "Renta", "2500.00");
        long goal = goalBox(user, "500.00");

        long purchase = expense(user, card, "6000.00", today.minusDays(3));
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("transactionId", purchase, "installmentCount", 6,
                "firstInstallmentDate", today.plusDays(20).toString()))
            .when().post("/api/accounts/{id}/msi-plans", card).then().statusCode(201);
        expense(user, payroll, "1100.00", today.plusDays(10));

        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals("complete", body.getString("status"));
        assertEquals("accounts", body.getString("baseline.source"));
        // 10,000.00 + 55.50 − 6,000.00 − 1,100.00; nothing forecast again.
        assertMoney("2955.50", body, "baseline.netBalance");
        assertMoney("3000.00", body, "baseline.inBoxes");
        assertMoney("-44.50", body, "baseline.availableToSpend");
        assertMoney("55.50", body, "baseline.creditInFavor");
        assertMoney("2955.50", body, "projected.netBalance");
        assertMoney("-44.50", body, "projected.availableToSpend");
        assertTrue(body.getList("notes").contains("CREDIT_IN_FAVOR_IN_BASELINE"));
        assertTrue(body.getList("notes").contains("LEDGER_TOTAL_NOT_CASH"));

        // The same three figures the dashboard reports, with capacity only there.
        JsonPath dashboard = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview").then().statusCode(200)
            .extract().response().jsonPath(EXACT);
        assertEquals(0, money(dashboard, "position.data.netBalance").compareTo(money(body, "baseline.netBalance")));
        assertEquals(0, money(dashboard, "position.data.inBoxes").compareTo(money(body, "baseline.inBoxes")));
        assertNotNull(dashboard.get("position.data.availableCredit"));
        assertFalse(body.prettify().contains("availableCredit"));

        // Every active Box, the one without a plan included.
        assertEquals(List.of(rent, goal), body.getList("projected.perBox.boxId", Long.class).stream().sorted().toList());

        // A new cost partly funded from a Box: U' = U − (E − F), negative allowed.
        JsonPath scenario = preview(user, request(true,
            List.of(cost("1200.00", today.plusDays(5), goal, "500.00")), List.of()));
        assertEquals("complete", scenario.getString("status"));
        assertMoney("1755.50", scenario, "projected.netBalance");
        assertMoney("2500.00", scenario, "projected.inBoxes");
        assertMoney("-744.50", scenario, "projected.availableToSpend");
        assertMoney("0.00", scenario, "projected.perBox.find { it.boxId == " + goal + " }.projectedBalance");
        assertMoney("2500.00", scenario, "projected.perBox.find { it.boxId == " + rent + " }.projectedBalance");
    }

    @Test
    void trackingOffUsesRecordedTransactionsAndTimingIsNotApplicable() {
        String user = newUser("plan-legacy");
        given().header("X-WorkOS-User-Id", user).when().get("/api/accounts/status").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE app_user SET account_tracking_required = FALSE WHERE workos_id = :id")
            .setParameter("id", user).executeUpdate());
        legacyTransaction(user, "INGRESS", "800.00");
        legacyTransaction(user, "EGRESS", "300.00");

        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals("transactions", body.getString("baseline.source"));
        assertMoney("500.00", body, "baseline.netBalance");
        assertNull(body.get("baseline.creditInFavor"));
        assertEquals("complete", body.getString("status"));
        // Not a failed read: statements do not exist without tracking.
        assertEquals("notApplicable", body.getString("timing.status"));
        assertEquals("TRACKING_INACTIVE", body.getString("timing.reasons[0].code"));
        assertTrue(body.getList("timing.dated").isEmpty());
    }

    /** FX-BOX-NOPLAN-01 shape and the empty case: no Boxes is zero, not unavailable. */
    @Test
    void aUserWithoutBoxesOrActivityHasAnEmptyButAvailableBaseline() {
        String user = newUser("plan-empty");
        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals("complete", body.getString("status"));
        assertMoney("0.00", body, "baseline.netBalance");
        assertMoney("0.00", body, "baseline.inBoxes");
        assertTrue(body.getList("projected.perBox").isEmpty());
        assertEquals("available", body.getString("undatedDebtsStatus"));
        assertTrue(body.getList("undatedDebts").isEmpty());
        assertNotNull(body.getString("generatedAt"));
        assertEquals(today().toString(), body.getString("window.from"));
        assertEquals(today().plusDays(29).toString(), body.getString("window.to"));
    }

    /* ---------------------------------------------------------------------
     * Receipts
     * ------------------------------------------------------------------ */

    @Test
    void receiptAmountsAreReloadedFromStorageAndNeverTakenFromTheClient() {
        String user = newUser("plan-receipts");
        LocalDate today = today();
        long cash = activate(user, today.minusDays(30));
        long debt = createDebt(user, "INGRESS", "4500.00");
        payDebt(user, debt, cash, "500.00");

        long shared = createSubscription(user, "SHARED", "300.00", today.minusDays(5), false);
        addMember(user, shared);
        generateBilling(user, shared);
        long record = paymentIds(user, shared).getFirst();

        Map<String, Object> debtReceipt = new HashMap<>(receipt("DEBT", debt, today.plusDays(10)));
        debtReceipt.put("amount", "999999.00");   // unknown field: ignored, never used
        Map<String, Object> body = new HashMap<>(request(true, List.of(),
            List.of(debtReceipt, receipt("PAYMENT_RECORD", record, today.plusDays(3)))));
        body.put("baseline", Map.of("netBalance", "1000000.00"));

        JsonPath result = preview(user, body);
        assertEquals("complete", result.getString("status"));
        assertMoney("500.00", result, "baseline.netBalance");
        // Remaining 4,000.00 (4,500.00 less the recorded payment) + 300.00.
        assertMoney("4800.00", result, "projected.netBalance");
        assertMoney("4800.00", result, "projected.availableToSpend");
        assertMoney("4000.00", result, "includedReceipts.find { it.recordKind == 'DEBT' }.amount");
        assertMoney("300.00", result, "includedReceipts.find { it.recordKind == 'PAYMENT_RECORD' }.amount");
        assertTrue(result.getList("notes").contains("RECEIPTS_IF_RECEIVED"));
    }

    @Test
    void ineligibleForeignUnknownAndDuplicateReceiptsMakeTheProjectionUnavailable() {
        String user = newUser("plan-receipt-rules");
        String other = newUser("plan-receipt-other");
        LocalDate today = today();
        long cash = activate(user, today.minusDays(30));
        long egress = createDebt(user, "EGRESS", "200.00");
        long settled = createDebt(user, "INGRESS", "100.00");
        payDebt(user, settled, cash, "100.00");
        long owed = createDebt(user, "INGRESS", "50.00");
        // Storage allows DECIMAL(12,2); the preview carries at most 9,999,999.99.
        long oversized = createDebt(user, "INGRESS", "10000000.00");

        long shared = createSubscription(user, "SHARED", "90.00", today.minusDays(5), false);
        addMember(user, shared);
        generateBilling(user, shared);
        long paidRecord = paymentIds(user, shared).getFirst();
        // PAID with no linked Transaction is still received.
        given().header("X-WorkOS-User-Id", user)
            .when().put("/api/subscriptions/{id}/payments/{paymentId}", shared, paidRecord)
            .then().statusCode(200);
        long personal = createSubscription(user, "PERSONAL", "149.00", today.minusDays(5), true);
        generateBilling(user, personal);
        long ownCharge = paymentIds(user, personal).getFirst();
        long trashed = createSubscription(user, "SHARED", "80.00", today.minusDays(5), false);
        addMember(user, trashed);
        generateBilling(user, trashed);
        long trashedRecord = paymentIds(user, trashed).getFirst();
        given().header("X-WorkOS-User-Id", user).when().delete("/api/subscriptions/{id}", trashed)
            .then().statusCode(204);

        activate(other, today.minusDays(30));
        long foreignDebt = createDebt(other, "INGRESS", "700.00");
        long foreignSubscription = createSubscription(other, "SHARED", "60.00", today.minusDays(5), false);
        addMember(other, foreignSubscription);
        generateBilling(other, foreignSubscription);
        long foreignRecord = paymentIds(other, foreignSubscription).getFirst();

        LocalDate date = today.plusDays(1);
        List<Map<String, Object>> receipts = List.of(
            receipt("DEBT", egress, date),                    // 0 EGRESS
            receipt("DEBT", settled, date),                   // 1 PAID
            receipt("PAYMENT_RECORD", paidRecord, date),      // 2 PAID, no Transaction
            receipt("PAYMENT_RECORD", ownCharge, date),       // 3 Owner's own charge
            receipt("PAYMENT_RECORD", trashedRecord, date),   // 4 trashed Subscription
            receipt("DEBT", foreignDebt, date),               // 5 another User's
            receipt("PAYMENT_RECORD", foreignRecord, date),   // 6 another User's
            receipt("DEBT", 987_654_321_000L, date),          // 7 unknown
            receipt("DEBT", owed, date),                      // 8 eligible
            receipt("DEBT", owed, today.plusDays(2)),         // 9 duplicate of 8
            receipt("PAYMENT_RECORD", 987_654_321_001L, date), // 10 unknown record
            receipt("DEBT", oversized, date));                // 11 above the money bound

        JsonPath body = preview(user, request(true, List.of(), receipts));
        assertEquals("unavailable", body.getString("status"));
        assertNull(body.get("projected"));
        assertTrue(body.getList("includedReceipts").isEmpty());
        assertNotNull(body.get("baseline"));
        assertNotNull(body.getString("timing.status"));
        // A server amount beyond the bound is ineligible, never the User's INVALID_AMOUNT.
        for (int index : List.of(0, 1, 2, 3, 11)) assertReceiptReason(body, index, "RECEIPT_INELIGIBLE");
        // A trashed Subscription's record reads like a trashed Debt: not found.
        for (int index : List.of(4, 5, 6, 7, 10)) assertReceiptReason(body, index, "RECEIPT_NOT_FOUND");
        assertReceiptReason(body, 9, "DUPLICATE_RECEIPT");
        assertTrue(body.getList("missingInputs.findAll { it.receiptIndex == 8 }").isEmpty());

        // Another User's record is indistinguishable from an unknown ID.
        JsonPath foreign = preview(user, request(true, List.of(), List.of(receipt("DEBT", foreignDebt, date))));
        JsonPath unknown = preview(user, request(true, List.of(), List.of(receipt("DEBT", 987_654_321_000L, date))));
        assertEquals(foreign.getList("missingInputs"), unknown.getList("missingInputs"));
        JsonPath trashedOne = preview(user, request(true, List.of(), List.of(receipt("PAYMENT_RECORD", trashedRecord, date))));
        JsonPath unknownOne = preview(user, request(true, List.of(), List.of(receipt("PAYMENT_RECORD", 987_654_321_001L, date))));
        assertEquals(unknownOne.getList("missingInputs"), trashedOne.getList("missingInputs"));
    }

    /* ---------------------------------------------------------------------
     * Validation
     * ------------------------------------------------------------------ */

    @Test
    void structuralErrorsAreRejectedWithoutAPreview() {
        String user = newUser("plan-structure");
        LocalDate today = today();
        Map<String, Object> item = cost("1.00", today, null, null);

        post(user, request(true, Collections.nCopies(51, item), List.of())).statusCode(400);
        post(user, request(true, List.of(), Collections.nCopies(51, receipt("DEBT", 1, today)))).statusCode(400);
        Map<String, Object> longDescription = new HashMap<>(item);
        longDescription.put("description", "x".repeat(201));
        post(user, request(true, List.of(longDescription), List.of())).statusCode(400);
        Map<String, Object> wrongHorizon = new HashMap<>(request(true, List.of(), List.of()));
        wrongHorizon.put("horizonDays", 31);
        post(user, wrongHorizon).statusCode(400);
        Map<String, Object> missingReview = new HashMap<>(request(true, List.of(), List.of()));
        missingReview.remove("essentialsReviewed");
        post(user, missingReview).statusCode(400);
        Map<String, Object> missingDate = new HashMap<>(item);
        missingDate.remove("date");
        post(user, request(true, List.of(missingDate), List.of())).statusCode(400);
        Map<String, Object> badKind = new HashMap<>(receipt("DEBT", 1, today));
        badKind.put("recordKind", "LOAN");
        post(user, request(true, List.of(), List.of(badKind))).statusCode(400);
        Map<String, Object> badAmount = new HashMap<>(item);
        badAmount.put("amount", "mucho");
        post(user, request(true, List.of(badAmount), List.of())).statusCode(400);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON).body("{not json")
            .when().post("/api/planning/preview").then().statusCode(400);
        given().contentType(ContentType.JSON).body(request(true, List.of(), List.of()))
            .when().post("/api/planning/preview").then().statusCode(401);

        // 50 of each is within bounds.
        post(user, request(true, Collections.nCopies(50, item), List.of())).statusCode(200);
    }

    @Test
    void businessInvalidRowsReturnAnEnvelopeWithRowAndBoxIdentifiers() {
        String user = newUser("plan-business");
        String other = newUser("plan-business-other");
        LocalDate today = today();
        activate(user, today.minusDays(30));
        createAccount(user, "Nómina", "DEBIT", "5000.00");
        long rent = reserve(user, "Renta", "2000.00");
        activate(other, today.minusDays(30));
        createAccount(other, "Nómina", "DEBIT", "100.00");
        long foreignBox = reserve(other, "Ajena", "50.00");

        JsonPath body = preview(user, request(true, List.of(
            cost("0.001", today, null, null),                 // 0
            cost("10.00", today.plusDays(30), null, null),    // 1 after window
            cost("10.00", today.minusDays(1), null, null),    // 2 before window
            cost("10.00", today, rent, "11.00"),              // 3
            cost("1500.00", today, rent, "1500.00"),          // 4 with 5: 3,000 > 2,000
            cost("1500.00", today.plusDays(29), rent, "1500.00"),
            cost("10.00", today, foreignBox, "1.00"),         // 6
            cost("10.00", today, null, "1.00")), List.of())); // 7
        assertEquals("unavailable", body.getString("status"));
        assertNull(body.get("projected"));
        assertItemReason(body, 0, "INVALID_AMOUNT");
        assertItemReason(body, 1, "DATE_OUT_OF_WINDOW");
        assertItemReason(body, 2, "DATE_OUT_OF_WINDOW");
        assertItemReason(body, 3, "FUNDING_EXCEEDS_COST");
        assertItemReason(body, 6, "BOX_NOT_FOUND");
        assertItemReason(body, 7, "BOX_REQUIRED");
        assertEquals(rent, body.getLong("missingInputs.find { it.reason == 'BOX_CAPACITY_EXCEEDED' }.boxId"));
        // Rows 4 + 5 only: row 3's over-cost funding is its own error, never accumulated.
        assertMoney("1000.00", body,"missingInputs.find { it.reason == 'BOX_CAPACITY_EXCEEDED' }.shortfall");
        // Timing is evaluated regardless of the projection.
        assertEquals("complete", body.getString("timing.status"));
        assertMoney("5000.00", body, "baseline.netBalance");

        // Window endpoints are inside; missing reviews give a labelled partial subtotal.
        Map<String, Object> unconfirmed = new HashMap<>(cost("100.00", today.plusDays(29), null, null));
        unconfirmed.put("notYetRecordedConfirmed", false);
        JsonPath partial = preview(user, request(false, List.of(cost("100.00", today, null, null), unconfirmed), List.of()));
        assertEquals("partial", partial.getString("status"));
        assertMoney("4800.00", partial, "projected.netBalance");
        assertMoney("2800.00", partial, "projected.availableToSpend");
        assertEquals(List.of("ESSENTIALS_NOT_REVIEWED", "COST_NOT_CONFIRMED_UNRECORDED"),
            partial.getList("missingInputs.reason"));
        assertEquals(1, partial.getInt("missingInputs[1].itemIndex"));
    }

    /**
     * An 11-character JSON number with a huge exponent passes the cents check.
     * It must be refused on magnitude before any arithmetic: a fast, small
     * response with a row error, no giant Box shortfall and no projection.
     */
    @Test
    void extremeExponentFundingIsRefusedQuicklyWithoutAGiantShortfall() {
        String user = newUser("plan-exponent");
        LocalDate today = today();
        activate(user, today.minusDays(30));
        createAccount(user, "Nómina", "DEBIT", "5000.00");
        long rent = reserve(user, "Renta", "2000.00");

        String json = "{\"horizonDays\":30,\"essentialsReviewed\":true,\"expectedReceipts\":[],"
            + "\"items\":[{\"amount\":10.00,\"date\":\"" + today + "\",\"boxId\":" + rent
            + ",\"boxAmount\":1e999999999,\"notYetRecordedConfirmed\":true}]}";
        String raw = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> given()
            .header("X-WorkOS-User-Id", user).contentType(ContentType.JSON).body(json)
            .when().post("/api/planning/preview").then().statusCode(200).extract().asString());
        assertTrue(raw.length() < 10_000, "response was " + raw.length() + " characters");

        JsonPath body = new JsonPath(raw).using(EXACT);
        assertEquals("unavailable", body.getString("status"));
        assertNull(body.get("projected"));
        assertEquals(List.of("INVALID_FUNDING"), body.getList("missingInputs.reason"));
        assertEquals(0, body.getInt("missingInputs[0].itemIndex"));
        assertNull(body.get("missingInputs[0].shortfall"));
        assertMoney("5000.00", body, "baseline.netBalance");
    }

    @Test
    void anUnusableZoneMakesTheProjectionAndTimingUnavailableButNotTheBaseline() {
        String user = newUser("plan-zone");
        activate(user, today().minusDays(30));
        createAccount(user, "Nómina", "DEBIT", "700.00");
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE app_user SET time_zone = 'Mars/Olympus_Mons' WHERE workos_id = :id")
            .setParameter("id", user).executeUpdate());

        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals("unavailable", body.getString("status"));
        assertEquals(List.of("ZONE_UNAVAILABLE"), body.getList("missingInputs.reason"));
        assertNull(body.get("window"));
        assertNull(body.get("projected"));
        assertMoney("700.00", body, "baseline.netBalance");
        assertEquals("unavailable", body.getString("timing.status"));
        assertEquals("ZONE_UNAVAILABLE", body.getString("timing.reasons[0].code"));
        assertEquals("available", body.getString("undatedDebtsStatus"));
    }

    /* ---------------------------------------------------------------------
     * Timing
     * ------------------------------------------------------------------ */

    /** Past the dashboard's 50-item cap: every overdue and in-window statement, none beyond. */
    @Test
    void timingListsEveryConfirmedStatementWithoutTheAttentionCap() {
        String user = newUser("plan-statements");
        LocalDate today = today();
        activate(user, today.minusDays(400));
        long card = createAccount(user, "Tarjeta", "CREDIT", "0.00");
        creditSettings(user, card, "9000.00");
        for (int i = 0; i < 51; i++) {
            LocalDate end = today.minusDays(300 - i * 5L);
            confirmStatement(user, card, end.minusDays(4), end, end.plusDays(1), "10.00");
        }
        confirmStatement(user, card, today.minusDays(3), today.minusDays(2), today, "20.00");
        confirmStatement(user, card, today.minusDays(1), today, today.plusDays(29), "30.00");
        confirmStatement(user, card, today.plusDays(1), today.plusDays(2), today.plusDays(30), "40.00");

        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals("complete", body.getString("timing.status"));
        assertEquals(51, body.getList("timing.overdue").size());
        assertEquals(List.of(today.toString(), today.plusDays(29).toString()), body.getList("timing.dated.dueDate"));
        assertMoney("30.00", body, "timing.dated[1].outstandingBalance");
        // Statements never touch the projection.
        assertMoney("0.00", body, "baseline.netBalance");
        assertMoney("0.00", body, "projected.netBalance");

        JsonPath dashboard = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview").then().statusCode(200).extract().jsonPath();
        assertTrue(dashboard.getBoolean("attention.data.statementsTruncated"));
    }

    @Test
    void unconfirmedPeriodsAndMismatchesMakeTimingPartialWithoutNewObligations() {
        String user = newUser("plan-timing-partial");
        LocalDate today = today();
        activate(user, today.minusDays(120));
        long card = createAccount(user, "Tarjeta", "CREDIT", "0.00");
        creditSettings(user, card, "9000.00");
        expense(user, card, "700.00", today.minusDays(70));

        JsonPath unconfirmed = preview(user, request(true, List.of(), List.of()));
        assertEquals("partial", unconfirmed.getString("timing.status"));
        assertTrue(unconfirmed.getList("timing.reasons.code").contains("UNCONFIRMED_STATEMENT"));
        assertFalse(unconfirmed.getList("timing.estimates").isEmpty());
        assertTrue(unconfirmed.getList("timing.dated").isEmpty());
        assertTrue(unconfirmed.getList("timing.overdue").isEmpty());
        // The purchase is already in Net Balance; the estimate is not deducted again.
        assertMoney("-700.00", unconfirmed, "baseline.netBalance");
        assertMoney("-700.00", unconfirmed, "projected.netBalance");

        String second = newUser("plan-timing-mismatch");
        activate(second, today.minusDays(120));
        long other = createAccount(second, "Tarjeta", "CREDIT", "0.00");
        creditSettings(second, other, "9000.00");
        confirmStatement(second, other, today.minusDays(40), today.minusDays(10), today.plusDays(5), "310.25");
        JsonPath before = preview(second, request(true, List.of(), List.of()));
        assertEquals("complete", before.getString("timing.status"));
        assertFalse(before.getBoolean("timing.dated[0].reconciliationMismatch"));

        expense(second, other, "45.00", today.minusDays(20));
        JsonPath after = preview(second, request(true, List.of(), List.of()));
        assertEquals("partial", after.getString("timing.status"));
        assertTrue(after.getList("timing.reasons.code").contains("RECONCILIATION_MISMATCH"));
        assertTrue(after.getBoolean("timing.dated[0].reconciliationMismatch"));
        assertMoney("310.25", after, "timing.dated[0].outstandingBalance");
    }

    /* ---------------------------------------------------------------------
     * Undated Debts, writes, freshness
     * ------------------------------------------------------------------ */

    /** FX-DEBT-BIDIRECTIONAL-01 shape: two rows, no 200.00 net, no deduction. */
    @Test
    void undatedDebtsListBothDirectionsSeparatelyAndNeverDeduct() {
        String user = newUser("plan-debts");
        activate(user, today().minusDays(30));
        long contact = createContact(user);
        long owedToUser = createDebt(user, contact, "INGRESS", "300.00");
        long owedByUser = createDebt(user, contact, "EGRESS", "500.00");

        JsonPath body = preview(user, request(true, List.of(), List.of()));
        assertEquals(List.of(owedToUser, owedByUser), body.getList("undatedDebts.debtId", Long.class));
        assertEquals(List.of("INGRESS", "EGRESS"), body.getList("undatedDebts.direction"));
        assertMoney("300.00", body, "undatedDebts[0].remaining");
        assertMoney("500.00", body, "undatedDebts[1].remaining");
        assertMoney("0.00", body, "projected.netBalance");
        assertEquals("complete", body.getString("status"));
    }

    @Test
    void previewingWritesNothingAndEachPreviewReadsAFreshSnapshot() {
        String user = newUser("plan-readonly");
        LocalDate today = today();
        long cash = activate(user, today.minusDays(30));
        long payroll = createAccount(user, "Nómina", "DEBIT", "1000.00");
        long box = reserve(user, "Reserva", "400.00");
        LocalDate cursor = today.minusDays(40);
        long subscription = createSubscription(user, "SHARED", "300.00", cursor, false);
        addMember(user, subscription);
        long debt = createDebt(user, "INGRESS", "200.00");

        Map<String, Object> scenario = request(true,
            List.of(cost("350.00", today.plusDays(2), box, "350.00")),
            List.of(receipt("DEBT", debt, today.plusDays(4))));
        JsonPath first = preview(user, scenario);
        preview(user, scenario);

        assertEquals(0, paymentIds(user, subscription).size());
        given().header("X-WorkOS-User-Id", user).when().get("/api/subscriptions/{id}", subscription)
            .then().statusCode(200).body("nextBillingDate", org.hamcrest.Matchers.equalTo(cursor.toString()));
        given().header("X-WorkOS-User-Id", user).when().get("/api/debts/{id}/payments", debt)
            .then().statusCode(200).body("size()", org.hamcrest.Matchers.equalTo(0));
        assertEquals(0L, given().header("X-WorkOS-User-Id", user).when().get("/api/transactions?page=0&pageSize=10")
            .then().statusCode(200).extract().jsonPath().getLong("totalItems"));
        JsonPath boxes = given().header("X-WorkOS-User-Id", user).when().get("/api/boxes/summary")
            .then().statusCode(200).extract().response().jsonPath(EXACT);
        assertEquals(0, new BigDecimal("400.00").compareTo(money(boxes, "inBoxes")));
        assertEquals(0, new BigDecimal("1000.00").compareTo(money(boxes, "netBalance")));
        assertTrue(given().header("X-WorkOS-User-Id", user).when().get("/api/boxes/{id}/history", box)
            .then().statusCode(200).extract().jsonPath().getList("findAll { it.type != 'DEPOSIT' }").isEmpty());

        // A cost recorded between previews is visible to the next one.
        expense(user, payroll, "350.00", today);
        JsonPath second = preview(user, scenario);
        assertMoney("1000.00", first, "baseline.netBalance");
        assertMoney("650.00", second, "baseline.netBalance");
        assertMoney("500.00", second, "projected.netBalance");
        assertFalse(Instant.parse(second.getString("generatedAt"))
            .isBefore(Instant.parse(first.getString("generatedAt"))));
        assertTrue(cash > 0);
    }

    /* ------------------------------------------------------------------ */

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static String newUser(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static Map<String, Object> request(boolean reviewed, List<? extends Map<String, ?>> items,
                                               List<? extends Map<String, ?>> receipts) {
        return Map.of("horizonDays", 30, "essentialsReviewed", reviewed,
            "items", items, "expectedReceipts", receipts);
    }

    private static Map<String, Object> cost(String amount, LocalDate date, Long boxId, String boxAmount) {
        Map<String, Object> item = new HashMap<>();
        item.put("amount", amount);
        item.put("date", date.toString());
        item.put("notYetRecordedConfirmed", true);
        if (boxId != null) item.put("boxId", boxId);
        if (boxAmount != null) item.put("boxAmount", boxAmount);
        return item;
    }

    private static Map<String, Object> receipt(String kind, long id, LocalDate date) {
        return Map.of("recordKind", kind, "recordId", id, "date", date.toString());
    }

    private static ValidatableResponse post(String user, Object body) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON).body(body)
            .when().post("/api/planning/preview").then();
    }

    private static JsonPath preview(String user, Object body) {
        return post(user, body).statusCode(200).extract().response().jsonPath(EXACT);
    }

    private static BigDecimal money(JsonPath body, String path) {
        Object value = body.get(path);
        assertNotNull(value, path + " is absent");
        return new BigDecimal(value.toString());
    }

    private static void assertMoney(String expected, JsonPath body, String path) {
        BigDecimal actual = money(body, path);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), path + " was " + actual);
    }

    private static void assertReceiptReason(JsonPath body, int index, String reason) {
        assertEquals(List.of(reason), body.getList("missingInputs.findAll { it.receiptIndex == " + index + " }.reason"),
            "receipt " + index);
    }

    private static void assertItemReason(JsonPath body, int index, String reason) {
        assertTrue(body.getList("missingInputs.findAll { it.itemIndex == " + index + " }.reason").contains(reason),
            "item " + index + " lacks " + reason);
    }

    private static long activate(String user, LocalDate activationDate) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("activationDate", activationDate.toString(), "accounts", List.of(
                Map.of("name", "Cash", "kind", "CASH", "hue", 220, "openingBalance", "0.00"))))
            .when().post("/api/accounts/activate").then().statusCode(201).extract().jsonPath().getLong("[0].id");
    }

    private static long createAccount(String user, String name, String kind, String openingBalance) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", name + " " + UUID.randomUUID(), "kind", kind, "hue", 220,
                "openingBalance", openingBalance))
            .when().post("/api/accounts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void creditSettings(String user, long accountId, String limit) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("creditLimit", limit, "statementClosingDay", 5, "paymentDueDay", 20))
            .when().put("/api/accounts/{id}/credit-settings", accountId).then().statusCode(200);
    }

    private static void confirmStatement(String user, long accountId, LocalDate start, LocalDate end,
                                         LocalDate due, String balance) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("periodStart", start.toString(), "periodEnd", end.toString(),
                "dueDate", due.toString(), "officialBalance", balance,
                "officialMinimumPayment", "0.00", "officialAvoidInterest", balance))
            .when().post("/api/accounts/{id}/credit-statements", accountId).then().statusCode(201);
    }

    private static long reserve(String user, String name, String amount) {
        long boxId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", name + " " + UUID.randomUUID(), "hue", 220))
            .when().post("/api/boxes").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", today().toString()))
            .when().post("/api/boxes/{id}/deposit", boxId).then().statusCode(200);
        return boxId;
    }

    /** A Box with an active Saving Goal, so plan evaluation runs before the read. */
    private static long goalBox(String user, String amount) {
        long boxId = reserve(user, "Meta", amount);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("targetAmount", "5000.00", "targetDate", today().plusDays(90).toString(),
                "cadence", "DAILY", "regularCommitment", "50.00"))
            .when().post("/api/boxes/{boxId}/plans/saving-goal", boxId).then().statusCode(201);
        return boxId;
    }

    private static long category(String user, String type) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", type + " " + UUID.randomUUID(), "type", type, "hue", 20))
            .when().post("/api/categories").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long expense(String user, long accountId, String amount, LocalDate date) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", "EGRESS", "description", "Gasto",
                "transactionDate", date.toString(), "categoryId", category(user, "EGRESS"),
                "accountId", accountId))
            .when().post("/api/transactions").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void legacyTransaction(String user, String direction, String amount) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", direction, "description", "Legacy",
                "transactionDate", today().toString(), "categoryId", category(user, direction)))
            .when().post("/api/transactions").then().statusCode(201);
    }

    private static long createContact(String user) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Contacto " + UUID.randomUUID()))
            .when().post("/api/contacts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long createDebt(String user, String direction, String total) {
        return createDebt(user, createContact(user), direction, total);
    }

    private static long createDebt(String user, long contactId, String direction, String total) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("contactId", contactId, "direction", direction,
                "description", "Deuda " + UUID.randomUUID(), "totalAmount", total))
            .when().post("/api/debts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void payDebt(String user, long debtId, long accountId, String amount) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "paymentDate", today().toString(),
                "categoryId", category(user, "INGRESS"), "accountId", accountId))
            .when().post("/api/debts/{id}/payments", debtId).then().statusCode(201);
    }

    private static long createSubscription(String user, String type, String cost, LocalDate next,
                                           boolean ownerParticipates) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Suscripción " + UUID.randomUUID(), "cost", cost,
                "billingCycle", "MONTHLY", "type", type, "nextBillingDate", next.toString(),
                "ownerParticipates", ownerParticipates))
            .when().post("/api/subscriptions").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void addMember(String user, long subscriptionId) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("contactId", createContact(user)))
            .when().post("/api/subscriptions/{id}/members", subscriptionId).then().statusCode(201);
    }

    private static void generateBilling(String user, long subscriptionId) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .when().post("/api/subscriptions/{id}/generate-billing", subscriptionId).then().statusCode(200);
    }

    private static List<Long> paymentIds(String user, long subscriptionId) {
        return new ArrayList<>(given().header("X-WorkOS-User-Id", user)
            .when().get("/api/subscriptions/{id}/payments", subscriptionId)
            .then().statusCode(200).extract().jsonPath().getList("id", Long.class));
    }
}
