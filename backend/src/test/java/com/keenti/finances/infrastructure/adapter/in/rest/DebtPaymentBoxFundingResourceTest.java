package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * A payment on a Debt the User owes is ordinary spending, so it may be paid
 * from Boxes like any other EGRESS Transaction (ADR-0019, ADR-0023).
 */
@QuarkusTest
class DebtPaymentBoxFundingResourceTest {

    @Test
    void paymentOnADebtTheUserOwes_canBeFundedFromABox() {
        String user = "test-debt-funding-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        long categoryId = createCategory(user, "BOTH");
        int contactId = createContact(user);
        long boxId = createBox(user, "Debt box");

        createIngress(user, "1000.00", accountId);
        deposit(user, boxId, "400.00");

        int debtId = createDebt(user, contactId, "EGRESS", "300.00");

        int transactionId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "250.00",
                "paymentDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId,
                "boxFunding", List.of(Map.of("boxId", boxId, "amount", "180.00"))))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(201)
            .extract().path("transactionId");

        // The Transaction carries the funding, and the remainder came from
        // Available to Spend rather than being silently absorbed by the Box.
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/" + transactionId)
            .then()
            .statusCode(200)
            .body("direction", equalTo("EGRESS"))
            .body("boxFunding", hasSize(1))
            .body("boxFunding.boxId", contains((int) boxId))
            .body("boxFunding.amount", contains(180.00f))
            .body("availableToSpendAmount", equalTo(70.00f));

        // The Box actually paid: 400 - 180.
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/" + boxId)
            .then()
            .statusCode(200)
            .body("balance", equalTo(220.00f));
    }

    @Test
    void fundingAPaymentOnADebtOwedToTheUser_returns400() {
        String user = "test-debt-funding-ingress-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        long categoryId = createCategory(user, "BOTH");
        int contactId = createContact(user);
        long boxId = createBox(user, "Debt box");

        createIngress(user, "1000.00", accountId);
        deposit(user, boxId, "400.00");

        int debtId = createDebt(user, contactId, "INGRESS", "300.00");

        // Money coming in is not spending; there is nothing for a Box to fund.
        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "250.00",
                "paymentDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId,
                "boxFunding", List.of(Map.of("boxId", boxId, "amount", "100.00"))))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(400);

        // The rejection left nothing behind: no payment, no Box withdrawal.
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/debts/" + debtId + "/payments")
            .then().statusCode(200).body("", hasSize(0));

        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/" + boxId)
            .then().statusCode(200).body("balance", equalTo(400.00f));
    }

    /** Insufficient Box balance is a conflict, the same one ordinary spending gets. */
    @Test
    void fundingBeyondTheBoxBalance_returns409() {
        String user = "test-debt-funding-over-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        long categoryId = createCategory(user, "BOTH");
        int contactId = createContact(user);
        long boxId = createBox(user, "Debt box");

        createIngress(user, "1000.00", accountId);
        deposit(user, boxId, "50.00");

        int debtId = createDebt(user, contactId, "EGRESS", "300.00");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "250.00",
                "paymentDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId,
                "boxFunding", List.of(Map.of("boxId", boxId, "amount", "200.00"))))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(409);
    }

    @Test
    void fundingBeyondThePaymentAmount_returns400() {
        String user = "test-debt-funding-over-amount-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        long categoryId = createCategory(user, "BOTH");
        int contactId = createContact(user);
        long boxId = createBox(user, "Debt box");

        createIngress(user, "1000.00", accountId);
        deposit(user, boxId, "400.00");

        int debtId = createDebt(user, contactId, "EGRESS", "300.00");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "100.00",
                "paymentDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId,
                "boxFunding", List.of(Map.of("boxId", boxId, "amount", "150.00"))))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(400);
    }

    @Test
    void omittedFunding_stillRecordsThePayment() {
        String user = "test-debt-funding-absent-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        long categoryId = createCategory(user, "BOTH");
        int contactId = createContact(user);

        createIngress(user, "1000.00", accountId);
        int debtId = createDebt(user, contactId, "EGRESS", "300.00");

        int transactionId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "100.00",
                "paymentDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(201)
            .extract().path("transactionId");

        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/" + transactionId)
            .then()
            .statusCode(200)
            .body("boxFunding", hasSize(0))
            .body("availableToSpendAmount", equalTo(100.00f));
    }

    private static int createDebt(String user, int contactId, String direction, String totalAmount) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "contactId", contactId,
                "direction", direction,
                "description", "Debt " + UUID.randomUUID(),
                "totalAmount", totalAmount,
                "createdAt", "2026-01-01"))
            .when().post("/api/debts")
            .then().statusCode(201)
            .extract().path("id");
    }

    private static long createCategory(String user, String type) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Category " + UUID.randomUUID(), "type", type, "hue", 120))
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static int createContact(String user) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Contact " + UUID.randomUUID()))
            .when().post("/api/contacts")
            .then().statusCode(201)
            .extract().path("id");
    }

    private static long createBox(String user, String name) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name + " " + UUID.randomUUID(), "hue", 200))
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static void deposit(String user, long boxId, String amount) {
        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/" + boxId + "/deposit")
            .then().statusCode(200);
    }

    private static void createIngress(String user, String amount, long accountId) {
        long categoryId = createCategory(user, "INGRESS");
        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", amount,
                "direction", "INGRESS",
                "description", "Seed income",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId,
                "accountId", accountId))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }
}
