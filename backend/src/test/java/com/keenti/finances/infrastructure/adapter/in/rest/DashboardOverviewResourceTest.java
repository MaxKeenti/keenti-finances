package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The composed dashboard read model (Phase 4).
 *
 * Each test asks one question the accounting has to answer the same way twice:
 * does the arithmetic hold, does a separately identified obligation stay out of
 * the totals, is another User's money invisible, and does reading the dashboard
 * change anything. None of them asserts a label or a sentence — copy belongs to
 * the frontend, and a test that mirrored it would only restate the fixture.
 *
 * Amounts are read as doubles rather than matched against literals, because a
 * JSON number arrives as an int or a float depending on whether it happens to
 * have decimals, and a test that cared about that difference would fail for a
 * reason that has nothing to do with the money.
 */
@QuarkusTest
class DashboardOverviewResourceTest {

    private static final double CENT = 0.0001;

    /**
     * The acceptance figures: 4,120.50 held plus 55.50 credit in the User's
     * favour is a Net Balance of 4,176.00; 5,300.00 reserved in Boxes leaves
     * Available to Spend at −1,124.00; and a 9,000.00 credit limit gives
     * 9,055.50 of capacity that enters none of those totals.
     */
    @Test
    void composesTheSignedPositionFromAssetAndCreditBalances() {
        String user = newUser("overview-position");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        // Reserved first and spent afterwards, which is the only way a real
        // ledger reaches this state: depositing more than Available to Spend is
        // refused, while spending that outruns it is recorded and leaves the
        // shortfall visible.
        long payroll = createAccount(user, "Nómina", "DEBIT", "5400.00");
        long card = createAccount(user, "Tarjeta", "CREDIT", "55.50");
        creditSettings(user, card, "9000.00").statusCode(200);
        reserve(user, "Renta", "3500.00");
        reserve(user, "Colegiatura", "1800.00");
        expense(user, payroll, "1279.50").statusCode(201);

        JsonPath body = overview(user);
        assertEquals("ok", body.getString("position.status"));
        assertTrue(body.getBoolean("position.data.trackingActive"));
        assertEquals(4120.50, amount(body, "position.data.moneyHeld"), CENT);
        assertEquals(55.50, amount(body, "position.data.creditInFavor"), CENT);
        // Credit owed and credit in the User's favour are separate sums, so one
        // overpaid card cannot mask another that is owed.
        assertEquals(0.0, amount(body, "position.data.creditDebt"), CENT);
        assertEquals(4176.00, amount(body, "position.data.netBalance"), CENT);
        assertEquals(5300.00, amount(body, "position.data.inBoxes"), CENT);
        assertEquals(-1124.00, amount(body, "position.data.availableToSpend"), CENT);
        // Limit-derived capacity: 9,000.00 + 55.50, floored at zero.
        assertEquals(9055.50, amount(body, "position.data.availableCredit"), CENT);

        createAccount(user, "Other credit account", "CREDIT", "-200.00");
        JsonPath mixed = overview(user);
        assertEquals(200.00, amount(mixed, "position.data.creditDebt"), CENT);
        assertEquals(55.50, amount(mixed, "position.data.creditInFavor"), CENT);
        assertEquals(3976.00, amount(mixed, "position.data.netBalance"), CENT);
        assertEquals(-1324.00, amount(mixed, "position.data.availableToSpend"), CENT);
    }

    /**
     * A confirmed statement is a separate obligation. The purchases behind it
     * already moved Net Balance, so subtracting it again would understate the
     * User's position by its outstanding amount.
     */
    @Test
    void reportsAConfirmedStatementWithoutSubtractingItTwice() {
        String user = newUser("overview-statement");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        createAccount(user, "Nómina", "DEBIT", "4120.50");
        long card = createAccount(user, "Tarjeta", "CREDIT", "55.50");
        creditSettings(user, card, "9000.00").statusCode(200);
        confirmStatement(user, card, "310.25").statusCode(201);

        JsonPath body = overview(user);
        assertEquals("ok", body.getString("attention.status"));
        assertEquals(1, body.getList("attention.data.statements").size());
        assertEquals(card, body.getLong("attention.data.statements[0].accountId"));
        assertEquals(310.25, amount(body, "attention.data.statements[0].officialBalance"), CENT);
        assertEquals(0.0, amount(body, "attention.data.statements[0].paidAmount"), CENT);
        assertEquals(310.25, amount(body, "attention.data.statements[0].outstandingBalance"), CENT);
        assertNotNull(body.getString("attention.data.statements[0].dueDate"));

        // Untouched by the statement.
        assertEquals(4176.00, amount(body, "position.data.netBalance"), CENT);
        assertEquals(4176.00, amount(body, "position.data.availableToSpend"), CENT);
        assertEquals(55.50, amount(body, "position.data.creditInFavor"), CENT);
    }

    /**
     * Before activation there are no signed account balances to split, and
     * decision D1 forbids guessing a breakdown for a Net Balance that is
     * recorded income minus expenses. Absent says that; zero would claim the
     * User holds nothing.
     */
    @Test
    void withholdsTheAccountBreakdownUntilTrackingIsActivated() {
        String user = newUser("overview-setup");

        JsonPath body = overview(user);
        assertFalse(body.getBoolean("position.data.trackingActive"));
        assertTrue(body.getBoolean("position.data.setupRequired"));
        assertNull(body.get("position.data.moneyHeld"));
        assertNull(body.get("position.data.creditDebt"));
        assertNull(body.get("position.data.creditInFavor"));
        assertNull(body.get("position.data.availableCredit"));
        assertEquals(0.0, amount(body, "position.data.netBalance"), CENT);
        assertTrue(body.getList("position.data.accounts").isEmpty());
    }

    /** A credit card with no configured limit has no capacity to report. */
    @Test
    void reportsNoAvailableCreditWhenNoLimitIsConfigured() {
        String user = newUser("overview-nolimit");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        createAccount(user, "Tarjeta", "CREDIT", "-200.00");

        JsonPath body = overview(user);
        assertEquals(200.0, amount(body, "position.data.creditDebt"), CENT);
        // Not 0.00: that would claim the User has no capacity left, which is a
        // different statement from having no limit recorded.
        assertNull(body.get("position.data.availableCredit"));
    }

    /**
     * Expected money: outstanding `ACTIVE` Debts and `PENDING` contributions
     * from Subscription Members. A Personal Subscription's own record has no
     * Member — it is the Owner's own charge, not money another person owes —
     * and must not be counted as an expected receipt.
     */
    @Test
    void countsOutstandingDebtsAndMemberContributionsButNotTheOwnersOwnRecord() {
        String user = newUser("overview-expected");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);

        long debt = createDebt(user, createContact(user, "Deudor"), "1200.00");

        long shared = createSubscription(user, "SHARED", "300.00", LocalDate.now().minusDays(5), false);
        addMember(user, shared, createContact(user, "Miembro"));
        generateBilling(user, shared).statusCode(200);

        long personal = createSubscription(user, "PERSONAL", "149.00", LocalDate.now().minusDays(5), true);
        generateBilling(user, personal).statusCode(200);

        JsonPath body = overview(user);
        assertEquals("ok", body.getString("expected.status"));
        assertEquals(List.of(debt), body.getList("expected.data.debts.debtId", Long.class));
        assertEquals(1200.00, amount(body, "expected.data.debts[0].remaining"), CENT);
        assertEquals(1200.00, amount(body, "expected.data.debtsOutstanding"), CENT);

        // Every listed contribution belongs to a Subscription Member, so the
        // Personal Subscription's memberId = null record is absent.
        List<Long> subscriptions = body.getList("expected.data.contributions.subscriptionId", Long.class);
        assertEquals(List.of(shared), subscriptions);
        assertFalse(subscriptions.contains(personal));
        assertTrue(body.getList("expected.data.contributions.memberId", Long.class).stream()
            .allMatch(memberId -> memberId != null));
        assertEquals(1, body.getInt("expected.data.contributionCount"));
        assertEquals(300.00, amount(body, "expected.data.contributionsOutstanding"), CENT);
    }

    /**
     * The exclusions the contribution read has to make on its own, now that it
     * asks the database for the pending rows instead of filtering a full payment
     * history in memory: a recorded payment, a Subscription in the trash, and
     * another User's Subscription entirely.
     */
    @Test
    void excludesPaidTrashedAndForeignContributionsFromExpectedMoney() {
        String user = newUser("overview-contrib-exclusions");
        String other = newUser("overview-contrib-foreign");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        activate(other, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);

        LocalDate cursor = LocalDate.now().minusDays(5);
        long owed = createSubscription(user, "SHARED", "300.00", cursor, false);
        addMember(user, owed, createContact(user, "Debe"));
        generateBilling(user, owed).statusCode(200);

        long settled = createSubscription(user, "SHARED", "120.00", cursor, false);
        addMember(user, settled, createContact(user, "Ya pagó"));
        generateBilling(user, settled).statusCode(200);

        long trashed = createSubscription(user, "SHARED", "80.00", cursor, false);
        addMember(user, trashed, createContact(user, "Suscripción borrada"));
        generateBilling(user, trashed).statusCode(200);

        long foreign = createSubscription(other, "SHARED", "999.00", cursor, false);
        addMember(other, foreign, createContact(other, "De alguien más"));
        generateBilling(other, foreign).statusCode(200);

        // Marked paid with no Transaction linked to it. A missing link is a
        // missing link; the money was still received, so it is not expected.
        long settledRecord = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/subscriptions/{id}/payments", settled)
            .then().statusCode(200).extract().jsonPath().getLong("[0].id");
        given().header("X-WorkOS-User-Id", user)
            .when().put("/api/subscriptions/{id}/payments/{paymentId}", settled, settledRecord)
            .then().statusCode(200);

        given().header("X-WorkOS-User-Id", user)
            .when().delete("/api/subscriptions/{id}", trashed)
            .then().statusCode(204);

        JsonPath body = overview(user);
        assertTrue(body.getBoolean("expected.data.contributionsAvailable"));
        assertEquals(List.of(owed),
            body.getList("expected.data.contributions.subscriptionId", Long.class));
        assertEquals(1, body.getInt("expected.data.contributionCount"));
        assertEquals(300.00, amount(body, "expected.data.contributionsOutstanding"), CENT);

        // And the other User sees only their own, at their own amount.
        JsonPath foreignBody = overview(other);
        assertEquals(List.of(foreign),
            foreignBody.getList("expected.data.contributions.subscriptionId", Long.class));
        assertEquals(999.00, amount(foreignBody, "expected.data.contributionsOutstanding"), CENT);
    }

    /**
     * A Contact in the trash withholds the name, not the money. The Member still
     * owes the contribution, so dropping the row would understate what the User
     * is expecting.
     */
    @Test
    void keepsAContributionWhoseContactWasTrashed() {
        String user = newUser("overview-contrib-contact");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);

        long subscription = createSubscription(
            user, "SHARED", "250.00", LocalDate.now().minusDays(3), false);
        long contact = createContact(user, "Contacto borrado");
        addMember(user, subscription, contact);
        generateBilling(user, subscription).statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().delete("/api/contacts/{id}", contact)
            .then().statusCode(204);

        JsonPath body = overview(user);
        assertEquals(1, body.getInt("expected.data.contributionCount"));
        assertEquals(250.00, amount(body, "expected.data.contributionsOutstanding"), CENT);
        assertNull(body.get("expected.data.contributions[0].contactName"));
    }

    /**
     * The history section reads the Transaction summary directly, so the year's
     * recorded totals stand on their own rather than depending on the account
     * and Box reads that answer the position section.
     */
    @Test
    void reportsTheYearsHistoryFromRecordedTransactionsAlone() {
        String user = newUser("overview-history");
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201).extract().jsonPath().getLong("[0].id");
        income(user, accountId, "750.00").statusCode(201);
        expense(user, accountId, "250.00").statusCode(201);

        int thisYear = LocalDate.now().getYear();
        JsonPath body = overview(user, thisYear);
        assertEquals("ok", body.getString("history.status"));
        assertEquals(750.00, amount(body, "history.data.totalIngress"), CENT);
        assertEquals(250.00, amount(body, "history.data.totalEgress"), CENT);
        // The same figures the year summary reports, from the same months.
        JsonPath summary = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/summary?year=" + thisYear)
            .then().statusCode(200).extract().jsonPath();
        assertEquals(summary.getDouble("totalIngress"),
            amount(body, "history.data.totalIngress"), CENT);
        assertEquals(summary.getDouble("totalEgress"),
            amount(body, "history.data.totalEgress"), CENT);
    }

    /** A settled Debt is owed nothing and is not expected money. */
    @Test
    void excludesFullyPaidDebtsFromExpectedMoney() {
        String user = newUser("overview-paid-debt");
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201).extract().jsonPath().getLong("[0].id");
        long debt = createDebt(user, createContact(user, "Deudor"), "500.00");

        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "500.00", "paymentDate", LocalDate.now().toString(),
                "categoryId", createIncomeCategory(user), "accountId", accountId))
            .when().post("/api/debts/{id}/payments", debt)
            .then().statusCode(201);

        JsonPath body = overview(user);
        assertEquals(0, body.getInt("expected.data.debtCount"));
        assertEquals(0.0, amount(body, "expected.data.debtsOutstanding"), CENT);
    }

    @Test
    void excludesWhatTheUserOwesFromExpectedMoney() {
        String user = newUser("overview-debt-directions");
        long contact = createContact(user, "Both directions");
        createDebt(user, contact, "300.00");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("contactId", contact, "direction", "EGRESS",
                "description", "User owes the same contact", "totalAmount", "500.00"))
            .when().post("/api/debts").then().statusCode(201);

        JsonPath body = overview(user);
        assertEquals(1, body.getInt("expected.data.debtCount"));
        assertEquals(300.00, amount(body, "expected.data.debtsOutstanding"), CENT);
        assertEquals(0.00, amount(body, "position.data.netBalance"), CENT);
    }

    /**
     * A Saving Goal's suggestion is min(current commitment, remaining amount) —
     * the same figure the Box Plan suggestion service publishes everywhere else.
     * Suggesting the full commitment would ask the User to over-fund a Goal that
     * needs less than one contribution to finish.
     */
    @Test
    void suggestsNoMoreForASavingGoalThanItStillNeeds() {
        String user = newUser("overview-goal-suggestion");
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201).extract().jsonPath().getLong("[0].id");
        income(user, accountId, "1000.00").statusCode(201);

        long boxId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Meta " + UUID.randomUUID(), "hue", 140))
            .when().post("/api/boxes").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("targetAmount", "400.00",
                "targetDate", LocalDate.now().plusDays(3).toString(),
                "cadence", "DAILY", "regularCommitment", "300.00"))
            .when().post("/api/boxes/{boxId}/plans/saving-goal", boxId)
            .then().statusCode(201);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "350.00", "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/deposit", boxId).then().statusCode(200);

        JsonPath body = overview(user);
        assertEquals("ok", body.getString("plans.status"));
        assertEquals(50.00, amount(body, "plans.data.items[0].remainingAmount"), CENT);
        assertEquals(300.00, amount(body, "plans.data.items[0].currentCommitment"), CENT);
        // 50.00 finishes it; 300.00 would be a request for money the Goal has no
        // use for.
        assertEquals(50.00, amount(body, "plans.data.items[0].suggestedContribution"), CENT);
        // The status is the plan's own, read after the lazy period evaluation the
        // detail read performs — never a copy taken before it.
        assertNotNull(body.getString("plans.data.items[0].status"));
    }

    /**
     * Reading the dashboard is read-only. Billing is generated deliberately from
     * a Subscription's own page (ADR-0019); a dashboard that quietly generated
     * records on load would create money obligations nobody asked for.
     */
    @Test
    void readingTheOverviewGeneratesNoBillingAndAdvancesNoCursor() {
        String user = newUser("overview-readonly");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        LocalDate cursor = LocalDate.now().minusDays(40);
        long subscription = createSubscription(user, "SHARED", "300.00", cursor, false);
        addMember(user, subscription, createContact(user, "Miembro"));

        assertEquals(1, overview(user).getList("attention.data.billing").size());
        assertEquals(cursor.toString(),
            overview(user).getString("attention.data.billing[0].nextBillingDate"));

        // Still no Payment Records, and the cursor has not moved: it is reported
        // as a fact, never acted on.
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/subscriptions/{id}/payments", subscription)
            .then().statusCode(200).body("size()", org.hamcrest.Matchers.equalTo(0));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/subscriptions/{id}", subscription)
            .then().statusCode(200)
            .body("nextBillingDate", org.hamcrest.Matchers.equalTo(cursor.toString()));
    }

    /** Box balances and Net Balance are unchanged by reading the overview. */
    @Test
    void readingTheOverviewMovesNoMoney() {
        String user = newUser("overview-nowrite");
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        createAccount(user, "Nómina", "DEBIT", "1000.00");
        reserve(user, "Reserva", "400.00");

        assertEquals(600.00, amount(overview(user), "position.data.availableToSpend"), CENT);

        JsonPath second = overview(user);
        assertEquals(1000.00, amount(second, "position.data.netBalance"), CENT);
        assertEquals(400.00, amount(second, "position.data.inBoxes"), CENT);
        assertEquals(600.00, amount(second, "position.data.availableToSpend"), CENT);

        JsonPath boxes = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary").then().statusCode(200).extract().jsonPath();
        assertEquals(1000.00, boxes.getDouble("netBalance"), CENT);
        assertEquals(400.00, boxes.getDouble("inBoxes"), CENT);
    }

    /**
     * User isolation (ADR-0011/0014). Every list the overview walks is read
     * through a user-scoped repository, so nothing of another User's — balances,
     * account names, statements, Debts — can reach this response.
     */
    @Test
    void oneUsersOverviewNeverContainsAnothersMoney() {
        String alice = newUser("overview-alice");
        String bob = newUser("overview-bob");
        String aliceAccount = "Alice " + UUID.randomUUID();
        String bobAccount = "Bob " + UUID.randomUUID();

        activate(alice, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        createAccount(alice, aliceAccount, "DEBIT", "4120.50");
        long aliceCard = createAccount(alice, "Alice card", "CREDIT", "0.00");
        creditSettings(alice, aliceCard, "9000.00").statusCode(200);
        confirmStatement(alice, aliceCard, "310.25").statusCode(201);
        createDebt(alice, createContact(alice, "Alice debtor"), "1200.00");

        activate(bob, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        createAccount(bob, bobAccount, "DEBIT", "25.00");

        JsonPath aliceBody = overview(alice);
        List<String> aliceNames = aliceBody.getList("position.data.accounts.name", String.class);
        assertEquals(4120.50, amount(aliceBody, "position.data.netBalance"), CENT);
        assertTrue(aliceNames.contains(aliceAccount));
        assertFalse(aliceNames.contains(bobAccount));

        JsonPath bobBody = overview(bob);
        List<String> bobNames = bobBody.getList("position.data.accounts.name", String.class);
        assertEquals(25.00, amount(bobBody, "position.data.netBalance"), CENT);
        assertTrue(bobNames.contains(bobAccount));
        assertFalse(bobNames.contains(aliceAccount));
        // Alice's confirmed statement and Debt are hers alone.
        assertTrue(bobBody.getList("attention.data.statements").isEmpty());
        assertTrue(bobBody.getList("expected.data.debts").isEmpty());
        assertEquals(0.0, amount(bobBody, "expected.data.debtsOutstanding"), CENT);
    }

    /**
     * The year scopes the history section only. The current position is all-time
     * by definition, so moving between years must not change a single total.
     */
    @Test
    void theYearParameterScopesHistoryOnly() {
        String user = newUser("overview-year");
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201).extract().jsonPath().getLong("[0].id");
        income(user, accountId, "750.00").statusCode(201);

        int thisYear = LocalDate.now().getYear();
        JsonPath current = overview(user, thisYear);
        assertEquals(750.00, amount(current, "position.data.netBalance"), CENT);
        assertEquals(thisYear, current.getInt("history.data.year"));
        assertEquals(750.00, amount(current, "history.data.totalIngress"), CENT);
        assertEquals(12, current.getList("history.data.monthly").size());

        JsonPath previous = overview(user, thisYear - 1);
        // Same all-time position, different year of history.
        assertEquals(750.00, amount(previous, "position.data.netBalance"), CENT);
        assertEquals(thisYear - 1, previous.getInt("history.data.year"));
        assertEquals(0.0, amount(previous, "history.data.totalIngress"), CENT);
    }

    @Test
    void rejectsAnUnusableYearRatherThanGuessingOne() {
        String user = newUser("overview-badyear");

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?year=ayer")
            .then().statusCode(400);
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?year=12")
            .then().statusCode(400);
    }

    /** Every section answers for itself, and each one says whether it answered. */
    @Test
    void everySectionReportsItsOwnAvailability() {
        String user = newUser("overview-sections");

        JsonPath body = overview(user);
        assertEquals("ok", body.getString("position.status"));
        assertEquals("ok", body.getString("attention.status"));
        assertEquals("ok", body.getString("plans.status"));
        assertEquals("ok", body.getString("expected.status"));
        assertEquals("ok", body.getString("history.status"));
        // The User's calendar day, resolved once from their configured zone.
        assertNotNull(body.getString("today"));
        assertNotNull(body.getString("timeZone"));
    }

    /* ------------------------------------------------------------------ */

    /** A money field as a double, so an int-shaped JSON number still matches. */
    private static double amount(JsonPath body, String path) {
        Object value = body.get(path);
        assertNotNull(value, path + " is absent");
        return ((Number) value).doubleValue();
    }

    private static String newUser(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static JsonPath overview(String user) {
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview")
            .then().statusCode(200).extract().jsonPath();
    }

    private static JsonPath overview(String user, int year) {
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?year=" + year)
            .then().statusCode(200).extract().jsonPath();
    }

    private static ValidatableResponse activate(String user, List<? extends Map<String, ?>> accounts) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("activationDate", LocalDate.now().toString(), "accounts", accounts))
            .when().post("/api/accounts/activate")
            .then();
    }

    private static Map<String, Object> account(String name, String kind, String openingBalance) {
        return Map.of("name", name, "kind", kind, "hue", 220, "openingBalance", openingBalance);
    }

    private static long createAccount(String user, String name, String kind, String openingBalance) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON).body(account(name, kind, openingBalance))
            .when().post("/api/accounts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse creditSettings(String user, long accountId, String limit) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("creditLimit", limit, "statementClosingDay", 5, "paymentDueDay", 20))
            .when().put("/api/accounts/{id}/credit-settings", accountId)
            .then();
    }

    private static ValidatableResponse confirmStatement(String user, long accountId, String officialBalance) {
        LocalDate periodEnd = LocalDate.now().minusDays(10);
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of(
                "periodStart", periodEnd.minusMonths(1).plusDays(1).toString(),
                "periodEnd", periodEnd.toString(),
                "dueDate", periodEnd.plusDays(15).toString(),
                "officialBalance", officialBalance,
                "officialMinimumPayment", "80.00",
                "officialAvoidInterest", officialBalance))
            .when().post("/api/accounts/{id}/credit-statements", accountId)
            .then();
    }

    /** Creates a Box and reserves money in it, which never changes Net Balance. */
    private static long reserve(String user, String name, String amount) {
        long boxId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", name + " " + UUID.randomUUID(), "hue", 220))
            .when().post("/api/boxes").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/deposit", boxId).then().statusCode(200);
        return boxId;
    }

    private static long createContact(String user, String name) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", name + " " + UUID.randomUUID()))
            .when().post("/api/contacts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long createDebt(String user, long contactId, String totalAmount) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("contactId", contactId, "description", "Préstamo " + UUID.randomUUID(),
                "totalAmount", totalAmount))
            .when().post("/api/debts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long createSubscription(String user, String type, String cost,
                                           LocalDate nextBillingDate, boolean ownerParticipates) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Suscripción " + UUID.randomUUID(), "cost", cost,
                "billingCycle", "MONTHLY", "type", type,
                "nextBillingDate", nextBillingDate.toString(),
                "ownerParticipates", ownerParticipates))
            .when().post("/api/subscriptions").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static void addMember(String user, long subscriptionId, long contactId) {
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("contactId", contactId))
            .when().post("/api/subscriptions/{id}/members", subscriptionId)
            .then().statusCode(201);
    }

    private static ValidatableResponse generateBilling(String user, long subscriptionId) {
        return given().header("X-WorkOS-User-Id", user)
            .when().post("/api/subscriptions/{id}/generate-billing", subscriptionId)
            .then();
    }

    private static long createIncomeCategory(String user) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Ingreso " + UUID.randomUUID(), "type", "INGRESS", "hue", 120))
            .when().post("/api/categories").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse income(String user, long accountId, String amount) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", "INGRESS", "description", "Ingreso",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", createIncomeCategory(user), "accountId", accountId))
            .when().post("/api/transactions").then();
    }

    private static ValidatableResponse expense(String user, long accountId, String amount) {
        long categoryId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("name", "Gasto " + UUID.randomUUID(), "type", "EGRESS", "hue", 20))
            .when().post("/api/categories").then().statusCode(201).extract().jsonPath().getLong("id");
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", "EGRESS", "description", "Gasto",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId, "accountId", accountId))
            .when().post("/api/transactions").then();
    }
}
