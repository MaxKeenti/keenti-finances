package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransactionBoxDistributionResourceTest {

    @Inject
    EntityManager em;

    @Test
    void ingressSupportsPartialFullAndDismissedMultiBoxDistributions() {
        String user = uniqueUser("amounts");
        long categoryId = createCategory(user);
        long groceriesId = createBox(user, "Groceries");
        long emergencyId = createBox(user, "Emergency");

        Response partial = transaction(
            user, categoryId, "1000.00", "INGRESS", "2026-02-01", "Salary",
            """
            [
              {"boxId": %d, "amount": 300.00},
              {"boxId": %d, "amount": 200.00}
            ]
            """.formatted(groceriesId, emergencyId));
        partial.then().statusCode(201);
        long partialId = partial.jsonPath().getLong("id");
        assertEquals(List.of(groceriesId, emergencyId),
            partial.jsonPath().getList("boxDistributions.boxId", Long.class));
        assertEquals(List.of(0, 1),
            partial.jsonPath().getList("boxDistributions.lineOrder", Integer.class));
        assertEquals(List.of("2026-02-01", "2026-02-01"),
            partial.jsonPath().getList("boxDistributions.effectiveDate", String.class));
        assertMoney("300.00", getBox(user, groceriesId).jsonPath().get("balance"));
        assertMoney("200.00", getBox(user, emergencyId).jsonPath().get("balance"));
        assertSummary(user, "1000.00", "500.00", "500.00");

        Response dismissed = transaction(
            user, categoryId, "100.00", "INGRESS", "2026-02-02", "Bonus", null);
        dismissed.then().statusCode(201);
        assertTrue(dismissed.jsonPath().getList("boxDistributions").isEmpty());
        assertSummary(user, "1100.00", "500.00", "600.00");

        Response full = transaction(
            user, categoryId, "600.00", "INGRESS", "2026-02-03", "Second salary",
            "[{\"boxId\":%d,\"amount\":600.00}]".formatted(emergencyId));
        full.then().statusCode(201);
        assertMoney("600.00", full.jsonPath().get("boxDistributions[0].amount"));
        assertSummary(user, "1700.00", "1100.00", "600.00");

        Response detail = getTransaction(user, partialId);
        detail.then().statusCode(200);
        assertEquals(2, detail.jsonPath().getList("boxDistributions").size());

        Map<String, Object> linkedDeposit = linkedDeposit(user, groceriesId, partialId);
        assertEquals("Salary", linkedDeposit.get("relatedTransactionDescription"));
        assertFalse((Boolean) linkedDeposit.get("relatedTransactionChanged"));
        assertFalse((Boolean) linkedDeposit.get("relatedTransactionRemoved"));
    }

    @Test
    void excessiveDistributionUsesPostIngressAvailableAndRollsBackEverything() {
        String user = uniqueUser("excessive");
        long categoryId = createCategory(user);
        long boxId = createBox(user, "Limited");
        transaction(user, categoryId, "50.00", "EGRESS", "2026-02-01", "Overspent", null)
            .then().statusCode(201);
        int before = transactionCount(user);

        transaction(
            user, categoryId, "100.00", "INGRESS", "2026-02-02", "Too allocated",
            "[{\"boxId\":%d,\"amount\":60.00}]".formatted(boxId))
            .then().statusCode(400);

        assertEquals(before, transactionCount(user));
        assertMoney("0.00", getBox(user, boxId).jsonPath().get("balance"));
        assertTrue(history(user, boxId).isEmpty());
        assertSummary(user, "-50.00", "0.00", "-50.00");
    }

    @Test
    void sourceEditsAndDeletionNeverReverseDepositsAndHistoryRetainsState() {
        String user = uniqueUser("lifecycle");
        long categoryId = createCategory(user);
        long boxId = createBox(user, "Independent");
        Response created = transaction(
            user, categoryId, "100.00", "INGRESS", "2026-03-01", "Original salary",
            "[{\"boxId\":%d,\"amount\":100.00}]".formatted(boxId));
        created.then().statusCode(201);
        long transactionId = created.jsonPath().getLong("id");

        updateTransaction(
            user, transactionId, categoryId, "80.00", "INGRESS", "2026-03-01",
            "Changed salary", null)
            .then().statusCode(200);
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));
        Map<String, Object> changed = linkedDeposit(user, boxId, transactionId);
        assertTrue((Boolean) changed.get("relatedTransactionChanged"));
        assertFalse((Boolean) changed.get("relatedTransactionRemoved"));
        assertEquals("Changed salary", changed.get("relatedTransactionDescription"));

        updateTransaction(
            user, transactionId, categoryId, "80.00", "INGRESS", "2026-03-01",
            "Changed salary", "[{\"boxId\":%d,\"amount\":1.00}]".formatted(boxId))
            .then().statusCode(400);
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));

        deleteTransaction(user, transactionId).then().statusCode(204);
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));
        Map<String, Object> removed = linkedDeposit(user, boxId, transactionId);
        assertTrue((Boolean) removed.get("relatedTransactionChanged"));
        assertTrue((Boolean) removed.get("relatedTransactionRemoved"));
        assertEquals("Changed salary", removed.get("relatedTransactionDescription"));

        restoreTransaction(user, transactionId).then().statusCode(204);
        assertFalse((Boolean) linkedDeposit(user, boxId, transactionId)
            .get("relatedTransactionRemoved"));

        deleteTransaction(user, transactionId).then().statusCode(204);
        permanentlyDeleteTransaction(user, transactionId).then().statusCode(204);
        Map<String, Object> permanentlyRemoved = linkedDeposit(user, boxId, transactionId);
        assertEquals(transactionId,
            ((Number) permanentlyRemoved.get("relatedTransactionId")).longValue());
        assertTrue((Boolean) permanentlyRemoved.get("relatedTransactionRemoved"));
        assertNull(permanentlyRemoved.get("relatedTransactionDescription"));
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));
    }

    @Test
    void linkedIngressDepositCorrectionRemainsLinkedAndUsesAvailableToSpend() {
        String user = uniqueUser("movement-correction");
        long categoryId = createCategory(user);
        long boxId = createBox(user, "Corrected distribution");
        LocalDate originalDate = LocalDate.now().minusDays(2);
        LocalDate correctedDate = originalDate.minusDays(1);
        Response created = transaction(
            user, categoryId, "200.00", "INGRESS", originalDate.toString(),
            "Correction salary",
            "[{\"boxId\":%d,\"amount\":100.00}]".formatted(boxId));
        created.then().statusCode(201);
        long transactionId = created.jsonPath().getLong("id");
        long movementId = ((Number) linkedDeposit(user, boxId, transactionId)
            .get("id")).longValue();

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", "150.00",
                "effectiveDate", correctedDate.toString()))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                boxId, movementId)
            .then().statusCode(200);

        getTransaction(user, transactionId)
            .then().statusCode(200)
            .body("boxDistributions[0].boxId", org.hamcrest.Matchers.equalTo((int) boxId))
            .body("boxDistributions[0].amount", org.hamcrest.Matchers.equalTo(150.0f))
            .body("boxDistributions[0].effectiveDate",
                org.hamcrest.Matchers.equalTo(correctedDate.toString()));
        Map<String, Object> corrected = linkedDeposit(user, boxId, transactionId);
        assertEquals(movementId, ((Number) corrected.get("id")).longValue());
        assertEquals("Correction salary", corrected.get("relatedTransactionDescription"));
        assertMoney("150.00", corrected.get("amount"));
        assertEquals(correctedDate.toString(), corrected.get("effectiveDate"));
        assertSummary(user, "200.00", "150.00", "50.00");

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", "250.00",
                "effectiveDate", correctedDate.toString()))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                boxId, movementId)
            .then().statusCode(400);
        Map<String, Object> unchanged = linkedDeposit(user, boxId, transactionId);
        assertMoney("150.00", unchanged.get("amount"));
        assertEquals(correctedDate.toString(), unchanged.get("effectiveDate"));
        assertSummary(user, "200.00", "150.00", "50.00");
    }

    @Test
    void distributionDateUsesTheUsersLocalToday() {
        String user = uniqueUser("local-date");
        long categoryId = createCategory(user);
        long boxId = createBox(user, "Local distribution");
        Instant now = Instant.now();
        LocalDate systemDate = LocalDate.ofInstant(now, ZoneId.systemDefault());
        ZoneId userZone = zoneWithDifferentDate(now, systemDate);
        LocalDate userDate = LocalDate.ofInstant(now, userZone);
        setTimeZone(user, userZone);

        transaction(
            user, categoryId, "100.00", "INGRESS", userDate.toString(),
            "Local salary", "[{\"boxId\":%d,\"amount\":10.00}]".formatted(boxId))
            .then().statusCode(201);
        if (systemDate.isAfter(userDate)) {
            transaction(
                user, categoryId, "100.00", "INGRESS", systemDate.toString(),
                "Locally future salary",
                "[{\"boxId\":%d,\"amount\":10.00}]".formatted(boxId))
                .then().statusCode(400);
        }

        assertMoney("10.00", getBox(user, boxId).jsonPath().get("balance"));
    }

    @Test
    void invalidDirectionFutureArchivedAndCrossUserDistributionsAreRejected() {
        String alice = uniqueUser("alice");
        String bob = uniqueUser("bob");
        long aliceBoxId = createBox(alice, "Alice private");
        long archivedBoxId = createBox(bob, "Archived");
        given()
            .header("X-WorkOS-User-Id", bob)
            .when().post("/api/boxes/{id}/archive", archivedBoxId)
            .then().statusCode(200);
        long bobCategoryId = createCategory(bob);
        int before = transactionCount(bob);

        transaction(
            bob, bobCategoryId, "100.00", "INGRESS", "2026-04-01", "Foreign",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(aliceBoxId))
            .then().statusCode(404);
        transaction(
            bob, bobCategoryId, "100.00", "INGRESS", "2026-04-01", "Archived",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(archivedBoxId))
            .then().statusCode(404);
        transaction(
            bob, bobCategoryId, "100.00", "EGRESS", "2026-04-01", "Wrong direction",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(archivedBoxId))
            .then().statusCode(400);
        transaction(
            bob, bobCategoryId, "100.00", "INGRESS", "2099-04-01", "Future",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(archivedBoxId))
            .then().statusCode(400);
        transaction(
            bob, bobCategoryId, "100.00", "INGRESS", "2026-04-01", "Duplicate",
            """
            [
              {"boxId": %d, "amount": 10.00},
              {"boxId": %d, "amount": 10.00}
            ]
            """.formatted(archivedBoxId, archivedBoxId))
            .then().statusCode(400);

        assertEquals(before, transactionCount(bob));
    }

    private static String uniqueUser(String suffix) {
        return "test-box-distribution-" + suffix + "-" + System.nanoTime();
    }

    private static long createCategory(String user) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Distribution " + System.nanoTime()
                + "\",\"type\":\"BOTH\",\"hue\":220}")
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static long createBox(String user, String label) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + label + " " + System.nanoTime()
                + "\",\"hue\":200}")
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static Response transaction(
            String user, long categoryId, String amount, String direction,
            String date, String description, String distributionsJson) {
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        String distributions = distributionsJson == null
            ? ""
            : ",\"boxDistributions\":" + distributionsJson;
        String body = """
            {
              "amount": %s,
              "direction": "%s",
              "description": "%s",
              "transactionDate": "%s",
              "categoryId": %d,
              "contactId": null,
              "accountId": %d%s
            }
            """.formatted(amount, direction, description, date, categoryId, accountId, distributions);
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/transactions");
    }

    private static Response updateTransaction(
            String user, long transactionId, long categoryId, String amount,
            String direction, String date, String description, String distributionsJson) {
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        String distributions = distributionsJson == null
            ? ""
            : ",\"boxDistributions\":" + distributionsJson;
        String body = """
            {
              "amount": %s,
              "direction": "%s",
              "description": "%s",
              "transactionDate": "%s",
              "categoryId": %d,
              "contactId": null,
              "accountId": %d%s
            }
            """.formatted(amount, direction, description, date, categoryId, accountId, distributions);
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/transactions/{id}", transactionId);
    }

    private static Response getTransaction(String user, long transactionId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/{id}", transactionId);
    }

    private static Response deleteTransaction(String user, long transactionId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().delete("/api/transactions/{id}", transactionId);
    }

    private static Response restoreTransaction(String user, long transactionId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().post("/api/trash/transaction/{id}/restore", transactionId);
    }

    private static Response permanentlyDeleteTransaction(String user, long transactionId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().delete("/api/trash/transaction/{id}", transactionId);
    }

    private static Response getBox(String user, long boxId) {
        Response response = given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}", boxId);
        response.then().statusCode(200);
        return response;
    }

    private static List<Map<String, Object>> history(String user, long boxId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .extract().as(new TypeRef<>() {});
    }

    private static Map<String, Object> linkedDeposit(
            String user, long boxId, long transactionId) {
        return history(user, boxId).stream()
            .filter(entry -> entry.get("relatedTransactionId") != null)
            .filter(entry -> ((Number) entry.get("relatedTransactionId")).longValue()
                == transactionId)
            .findFirst()
            .orElseThrow();
    }

    private static int transactionCount(String user) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions")
            .then().statusCode(200)
            .extract().jsonPath().getList("$").size();
    }

    private void setTimeZone(String user, ZoneId zone) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery(
                    "UPDATE app_user SET time_zone = :zone WHERE workos_id = :user")
                .setParameter("zone", zone.getId())
                .setParameter("user", user)
                .executeUpdate();
            em.clear();
        });
    }

    private static ZoneId zoneWithDifferentDate(Instant now, LocalDate systemDate) {
        return ZoneId.getAvailableZoneIds().stream()
            .map(ZoneId::of)
            .filter(zone -> !LocalDate.ofInstant(now, zone).equals(systemDate))
            .findFirst()
            .orElseThrow();
    }

    private static void assertSummary(
            String user, String netBalance, String inBoxes, String available) {
        Response summary = given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary");
        summary.then().statusCode(200);
        assertMoney(netBalance, summary.jsonPath().get("netBalance"));
        assertMoney(inBoxes, summary.jsonPath().get("inBoxes"));
        assertMoney(available, summary.jsonPath().get("availableToSpend"));
    }

    private static void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        BigDecimal value = new BigDecimal(actual.toString());
        assertTrue(new BigDecimal(expected).compareTo(value) == 0,
            () -> "expected " + expected + " but was " + actual);
    }
}
