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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransactionBoxFundingResourceTest {

    @Inject
    EntityManager em;

    @Test
    void create_withMultipleBoxes_exposesFundingAndFreeRemainderAcrossBothRoots() {
        String user = uniqueUser("multi");
        long categoryId = createCategory(user, "Funding category");
        createTransaction(user, categoryId, "1000.00", "INGRESS", "2026-01-01",
            "Funding income", null);
        long vacationId = createBox(user, "Vacation");
        long groceriesId = createBox(user, "Groceries");
        deposit(user, vacationId, "300.00", "2026-01-01");
        deposit(user, groceriesId, "250.00", "2026-01-01");

        Response created = createTransaction(
            user, categoryId, "500.00", "EGRESS", "2026-01-02", "Split expense",
            """
            [
              {"boxId": %d, "amount": 200.00},
              {"boxId": %d, "amount": 150.00}
            ]
            """.formatted(vacationId, groceriesId));
        long transactionId = created.jsonPath().getLong("id");

        assertMoney("150.00", created.jsonPath().get("availableToSpendAmount"));
        assertEquals(List.of(vacationId, groceriesId),
            created.jsonPath().getList("boxFunding.boxId", Long.class));
        assertEquals(List.of(0, 1),
            created.jsonPath().getList("boxFunding.lineOrder", Integer.class));
        assertMoney("200.00", created.jsonPath().get("boxFunding[0].amount"));
        assertMoney("150.00", created.jsonPath().get("boxFunding[1].amount"));

        assertMoney("100.00", getBox(user, vacationId).jsonPath().get("balance"));
        assertMoney("100.00", getBox(user, groceriesId).jsonPath().get("balance"));

        Response summary = given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary");
        summary.then().statusCode(200);
        assertMoney("500.00", summary.jsonPath().get("netBalance"));
        assertMoney("200.00", summary.jsonPath().get("inBoxes"));
        assertMoney("300.00", summary.jsonPath().get("availableToSpend"));

        Response fetched = given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/{id}", transactionId);
        fetched.then().statusCode(200);
        assertMoney("150.00", fetched.jsonPath().get("availableToSpendAmount"));
        assertEquals(2, fetched.jsonPath().getList("boxFunding").size());

        List<Map<String, Object>> vacationHistory = boxHistory(user, vacationId);
        Map<String, Object> spending = vacationHistory.stream()
            .filter(entry -> "SPENDING".equals(entry.get("type")))
            .findFirst()
            .orElseThrow();
        assertEquals(transactionId, ((Number) spending.get("relatedTransactionId")).longValue());
        assertMoney("200.00", spending.get("amount"));
    }

    @Test
    void update_failure_rollsBackTransactionAndFunding_thenValidEditUsesNewAmount() {
        String user = uniqueUser("edit");
        long categoryId = createCategory(user, "Edit category");
        createTransaction(user, categoryId, "500.00", "INGRESS", "2026-01-01",
            "Edit income", null);
        long boxId = createBox(user, "Edit Box");
        deposit(user, boxId, "300.00", "2026-01-01");
        long transactionId = createTransaction(
            user, categoryId, "250.00", "EGRESS", "2026-01-03", "Original expense",
            "[{\"boxId\":%d,\"amount\":200.00}]".formatted(boxId))
            .jsonPath().getLong("id");

        updateTransaction(
            user, transactionId, categoryId, "400.00", "EGRESS", "2026-01-03",
            "Rejected edit", "[{\"boxId\":%d,\"amount\":350.00}]".formatted(boxId))
            .then().statusCode(409);

        Response unchanged = getTransaction(user, transactionId);
        assertMoney("250.00", unchanged.jsonPath().get("amount"));
        assertEquals("Original expense", unchanged.jsonPath().getString("description"));
        assertMoney("200.00", unchanged.jsonPath().get("boxFunding[0].amount"));
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));

        Response edited = updateTransaction(
            user, transactionId, categoryId, "250.00", "EGRESS", "2026-01-03",
            "Accepted edit", "[{\"boxId\":%d,\"amount\":150.00}]".formatted(boxId));
        edited.then().statusCode(200);
        assertMoney("150.00", edited.jsonPath().get("boxFunding[0].amount"));
        assertMoney("100.00", edited.jsonPath().get("availableToSpendAmount"));
        assertMoney("150.00", getBox(user, boxId).jsonPath().get("balance"));

        // An older client that omits boxFunding preserves the current assignment.
        Response legacyEdit = updateTransaction(
            user, transactionId, categoryId, "250.00", "EGRESS", "2026-01-03",
            "Description only", null);
        legacyEdit.then().statusCode(200);
        assertMoney("150.00", legacyEdit.jsonPath().get("boxFunding[0].amount"));
        assertMoney("150.00", getBox(user, boxId).jsonPath().get("balance"));
    }

    @Test
    void deleteRefunds_restoreReapplies_andInterveningBackdatedDebitBlocksRestore() {
        String user = uniqueUser("restore");
        long categoryId = createCategory(user, "Restore category");
        createTransaction(user, categoryId, "200.00", "INGRESS", "2026-01-01",
            "Restore income", null);
        long boxId = createBox(user, "Restore Box");
        deposit(user, boxId, "100.00", "2026-01-01");
        long transactionId = createTransaction(
            user, categoryId, "100.00", "EGRESS", "2026-01-03", "Funded expense",
            "[{\"boxId\":%d,\"amount\":100.00}]".formatted(boxId))
            .jsonPath().getLong("id");
        assertMoney("0.00", getBox(user, boxId).jsonPath().get("balance"));

        deleteTransaction(user, transactionId).then().statusCode(204);
        assertMoney("100.00", getBox(user, boxId).jsonPath().get("balance"));

        restoreTransaction(user, transactionId).then().statusCode(204);
        assertMoney("0.00", getBox(user, boxId).jsonPath().get("balance"));
        assertMoney("100.00", getTransaction(user, transactionId)
            .jsonPath().get("boxFunding[0].amount"));

        deleteTransaction(user, transactionId).then().statusCode(204);
        withdraw(user, boxId, "100.00", "2026-01-02").then().statusCode(200);
        assertMoney("0.00", getBox(user, boxId).jsonPath().get("balance"));

        restoreTransaction(user, transactionId).then().statusCode(409);
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/{id}", transactionId)
            .then().statusCode(404);
        assertMoney("0.00", getBox(user, boxId).jsonPath().get("balance"));
    }

    @Test
    void archivedBox_blocksFundingCorrectionsButAllowsNonFundingEdit() {
        String user = uniqueUser("archived");
        long categoryId = createCategory(user, "Archived category");
        createTransaction(user, categoryId, "100.00", "INGRESS", "2026-01-01",
            "Archived income", null);
        long boxId = createBox(user, "Archived Box");
        deposit(user, boxId, "100.00", "2026-01-01");
        long transactionId = createTransaction(
            user, categoryId, "100.00", "EGRESS", "2026-01-02", "Spent all",
            "[{\"boxId\":%d,\"amount\":100.00}]".formatted(boxId))
            .jsonPath().getLong("id");

        given()
            .header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", boxId)
            .then().statusCode(200);

        Response descriptionOnly = updateTransaction(
            user, transactionId, categoryId, "100.00", "EGRESS", "2026-01-02",
            "Still funded", null);
        descriptionOnly.then().statusCode(200);
        assertMoney("100.00", descriptionOnly.jsonPath().get("boxFunding[0].amount"));

        deleteTransaction(user, transactionId).then().statusCode(409);
        getTransaction(user, transactionId).then().statusCode(200);

        updateTransaction(
            user, transactionId, categoryId, "100.00", "EGRESS", "2026-01-02",
            "Remove funding", "[]")
            .then().statusCode(409);
    }

    @Test
    void fundingDateUsesTheUsersLocalToday() {
        String user = uniqueUser("local-date");
        long categoryId = createCategory(user, "Local funding category");
        createTransaction(user, categoryId, "200.00", "INGRESS", "2026-01-01",
            "Local funding income", null);
        long boxId = createBox(user, "Local funding Box");
        deposit(user, boxId, "100.00", "2026-01-01");
        Instant now = Instant.now();
        LocalDate systemDate = LocalDate.ofInstant(now, ZoneId.systemDefault());
        ZoneId userZone = zoneWithDifferentDate(now, systemDate);
        LocalDate userDate = LocalDate.ofInstant(now, userZone);
        setTimeZone(user, userZone);

        createTransactionRequest(
            user, categoryId, "10.00", "EGRESS", userDate.toString(),
            "Local expense", "[{\"boxId\":%d,\"amount\":10.00}]".formatted(boxId))
            .then().statusCode(201);
        if (systemDate.isAfter(userDate)) {
            createTransactionRequest(
                user, categoryId, "10.00", "EGRESS", systemDate.toString(),
                "Locally future expense",
                "[{\"boxId\":%d,\"amount\":10.00}]".formatted(boxId))
                .then().statusCode(400);
        }

        assertMoney("90.00", getBox(user, boxId).jsonPath().get("balance"));
    }

    @Test
    void invalidOrForeignFunding_isRejectedWithoutCreatingTransaction() {
        String alice = uniqueUser("owner");
        String bob = uniqueUser("foreign");
        long aliceBoxId = createBox(alice, "Private Box");
        long bobCategoryId = createCategory(bob, "Bob category");
        createTransaction(bob, bobCategoryId, "500.00", "INGRESS", "2026-01-01",
            "Bob income", null);
        int before = listTransactionCount(bob);

        createTransactionExpecting(
            bob, bobCategoryId, "100.00", "EGRESS", "2026-01-02", "Foreign box",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(aliceBoxId), 404);
        createTransactionExpecting(
            bob, bobCategoryId, "100.00", "INGRESS", "2026-01-02", "Income funding",
            "[{\"boxId\":%d,\"amount\":10.00}]".formatted(aliceBoxId), 400);
        createTransactionExpecting(
            bob, bobCategoryId, "100.00", "EGRESS", "2026-01-02", "Overfunded",
            "[{\"boxId\":%d,\"amount\":110.00}]".formatted(aliceBoxId), 400);
        createTransactionExpecting(
            bob, bobCategoryId, "100.00", "EGRESS", "2026-01-02", "Duplicate",
            """
            [
              {"boxId": %d, "amount": 10.00},
              {"boxId": %d, "amount": 10.00}
            ]
            """.formatted(aliceBoxId, aliceBoxId), 400);

        assertEquals(before, listTransactionCount(bob));
    }

    private static String uniqueUser(String suffix) {
        return "test-box-funding-" + suffix + "-" + System.nanoTime();
    }

    private static long createCategory(String user, String label) {
        String name = label + " " + System.nanoTime();
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\",\"type\":\"BOTH\",\"hue\":120}")
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static long createBox(String user, String label) {
        String name = label + " " + System.nanoTime();
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\",\"hue\":180,\"icon\":\"box\"}")
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static Response deposit(String user, long boxId, String amount, String date) {
        return boxAmountCommand(user, boxId, "deposit", amount, date)
            .then().statusCode(200)
            .extract().response();
    }

    private static Response withdraw(String user, long boxId, String amount, String date) {
        return boxAmountCommand(user, boxId, "withdraw", amount, date);
    }

    private static Response boxAmountCommand(
            String user, long boxId, String command, String amount, String date) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"amount\":" + amount + ",\"effectiveDate\":\"" + date + "\"}")
            .when().post("/api/boxes/{id}/{command}", boxId, command);
    }

    private static Response createTransaction(
            String user, long categoryId, String amount, String direction, String date,
            String description, String boxFundingJson) {
        return createTransactionRequest(
            user, categoryId, amount, direction, date, description, boxFundingJson)
            .then().statusCode(201)
            .extract().response();
    }

    private static void createTransactionExpecting(
            String user, long categoryId, String amount, String direction, String date,
            String description, String boxFundingJson, int status) {
        createTransactionRequest(
            user, categoryId, amount, direction, date, description, boxFundingJson)
            .then().statusCode(status);
    }

    private static Response createTransactionRequest(
            String user, long categoryId, String amount, String direction, String date,
            String description, String boxFundingJson) {
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        String fundingProperty = boxFundingJson == null
            ? ""
            : ",\"boxFunding\":" + boxFundingJson;
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
            """.formatted(amount, direction, description, date, categoryId, accountId, fundingProperty);
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/transactions");
    }

    private static Response updateTransaction(
            String user, long transactionId, long categoryId, String amount,
            String direction, String date, String description, String boxFundingJson) {
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        String fundingProperty = boxFundingJson == null
            ? ""
            : ",\"boxFunding\":" + boxFundingJson;
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
            """.formatted(amount, direction, description, date, categoryId, accountId, fundingProperty);
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

    private static Response getBox(String user, long boxId) {
        Response response = given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}", boxId);
        response.then().statusCode(200);
        return response;
    }

    private static List<Map<String, Object>> boxHistory(String user, long boxId) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .extract().as(new TypeRef<>() {});
    }

    private static int listTransactionCount(String user) {
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

    private static void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        BigDecimal value = new BigDecimal(actual.toString());
        assertTrue(new BigDecimal(expected).compareTo(value) == 0,
            () -> "expected " + expected + " but was " + actual);
    }
}
