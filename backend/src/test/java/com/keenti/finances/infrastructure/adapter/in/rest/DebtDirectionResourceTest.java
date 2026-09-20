package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * A Debt is bidirectional (ADR-0023): its Direction is the Direction of the
 * Transaction each of its Debt Payments creates.
 */
@QuarkusTest
class DebtDirectionResourceTest {

    @Test
    void debtRecordedBeforeDirectionExisted_isStillMoneyOwedToTheUser() {
        String user = "test-direction-default-" + System.nanoTime();
        int contactId = createContact(user, "Legacy Debtor " + System.nanoTime());

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "contactId": %d,
                  "description": "Omitted direction",
                  "totalAmount": 100.00,
                  "createdAt": "2026-01-01"
                }
                """.formatted(contactId))
            .when().post("/api/debts")
            .then()
            .statusCode(201)
            .body("direction", equalTo("INGRESS"));
    }

    @Test
    void unknownDirection_returns400() {
        String user = "test-direction-invalid-" + System.nanoTime();
        int contactId = createContact(user, "Debtor " + System.nanoTime());

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "SIDEWAYS", "Bad direction", "100.00"))
            .when().post("/api/debts")
            .then()
            .statusCode(400);
    }

    @Test
    void paymentOnADebtTheUserOwes_createsAnEgressTransaction() {
        String user = "test-direction-egress-payment-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Payment Category " + System.nanoTime());
        int contactId = createContact(user, "Creditor " + System.nanoTime());
        String description = "Owed to a friend " + System.nanoTime();

        int debtId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "EGRESS", description, "300.00"))
            .when().post("/api/debts")
            .then()
            .statusCode(201)
            .body("direction", equalTo("EGRESS"))
            .extract().path("id");

        int transactionId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "amount": 120.00,
                  "paymentDate": "2026-09-04",
                  "categoryId": %d,
                  "accountId": %d,
                  "notes": "Partial repayment"
                }
                """.formatted(categoryId, accountId))
            .when().post("/api/debts/" + debtId + "/payments")
            .then()
            .statusCode(201)
            .body("transactionId", notNullValue())
            .extract().path("transactionId");

        // ADR-0005 still holds — one payment, one Transaction — but the money
        // now moves out rather than in.
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/" + transactionId)
            .then()
            .statusCode(200)
            .body("direction", equalTo("EGRESS"))
            .body("amount", equalTo(120.00f));
    }

    @Test
    void paymentOnADebtOwedToTheUser_stillCreatesAnIngressTransaction() {
        String user = "test-direction-ingress-payment-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Payment Category " + System.nanoTime());
        int contactId = createContact(user, "Debtor " + System.nanoTime());

        int debtId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "INGRESS", "Owed to me " + System.nanoTime(), "300.00"))
            .when().post("/api/debts")
            .then().statusCode(201)
            .extract().path("id");

        int transactionId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "amount": 120.00,
                  "paymentDate": "2026-09-04",
                  "categoryId": %d,
                  "accountId": %d,
                  "notes": null
                }
                """.formatted(categoryId, accountId))
            .when().post("/api/debts/" + debtId + "/payments")
            .then().statusCode(201)
            .extract().path("transactionId");

        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions/" + transactionId)
            .then()
            .statusCode(200)
            .body("direction", equalTo("INGRESS"));
    }

    /**
     * Flipping the Direction would contradict Transactions that already moved
     * money the other way, and those are not reversed here.
     */
    @Test
    void changingDirectionAfterAPayment_returns400() {
        String user = "test-direction-locked-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Payment Category " + System.nanoTime());
        int contactId = createContact(user, "Debtor " + System.nanoTime());

        int debtId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "INGRESS", "Locked once paid", "300.00"))
            .when().post("/api/debts")
            .then().statusCode(201)
            .extract().path("id");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "amount": 50.00,
                  "paymentDate": "2026-09-04",
                  "categoryId": %d,
                  "accountId": %d,
                  "notes": null
                }
                """.formatted(categoryId, accountId))
            .when().post("/api/debts/" + debtId + "/payments")
            .then().statusCode(201);

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "EGRESS", "Locked once paid", "300.00"))
            .when().put("/api/debts/" + debtId)
            .then()
            .statusCode(400);
    }

    @Test
    void changingDirectionBeforeAnyPayment_isAllowed() {
        String user = "test-direction-unlocked-" + System.nanoTime();
        int contactId = createContact(user, "Debtor " + System.nanoTime());

        int debtId = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "INGRESS", "Recorded on the wrong side", "300.00"))
            .when().post("/api/debts")
            .then().statusCode(201)
            .extract().path("id");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(debtBody(contactId, "EGRESS", "Recorded on the wrong side", "300.00"))
            .when().put("/api/debts/" + debtId)
            .then()
            .statusCode(200)
            .body("direction", equalTo("EGRESS"));
    }

    private String debtBody(int contactId, String direction, String description, String totalAmount) {
        return """
                {
                  "contactId": %d,
                  "direction": "%s",
                  "description": "%s",
                  "totalAmount": %s,
                  "createdAt": "2026-01-01"
                }
                """.formatted(contactId, direction, description, totalAmount);
    }

    private int createCategory(String user, String name) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\",\"type\":\"BOTH\",\"hue\":120}")
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().path("id");
    }

    private int createContact(String user, String name) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
            .when().post("/api/contacts")
            .then().statusCode(201)
            .extract().path("id");
    }
}
