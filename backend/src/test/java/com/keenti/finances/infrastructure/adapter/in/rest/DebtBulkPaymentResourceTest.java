package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class DebtBulkPaymentResourceTest {

    @Test
    void bulkPayment_appliesOldestFirstAndSettlesCoveredDebts() {
        String user = "test-bulk-oldest-first-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Bulk Category " + System.nanoTime());
        int contactId = createContact(user, "Bulk Debtor " + System.nanoTime());

        createDebt(user, contactId, "Oldest debt", "100.00", "2026-01-01");
        createDebt(user, contactId, "Middle debt", "50.00", "2026-02-01");
        createDebt(user, contactId, "Newest debt", "25.00", "2026-03-01");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(bulkPaymentBody(contactId, "120.00", categoryId, String.valueOf(accountId)))
            .when().post("/api/debts/bulk-payment")
            .then()
            .statusCode(200)
            .body("totalApplied", equalTo(120.00f))
            .body("totalUnused", equalTo(0.00f))
            .body("payments.description", contains("Oldest debt", "Middle debt"))
            .body("payments.applied", contains(100.00f, 20.00f))
            .body("payments.debtStatus", contains("PAID", "ACTIVE"));
    }

    @Test
    void bulkPayment_amountBeyondOutstandingBalance_reportsRemainderAsUnused() {
        String user = "test-bulk-unused-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Bulk Category " + System.nanoTime());
        int contactId = createContact(user, "Bulk Debtor " + System.nanoTime());

        createDebt(user, contactId, "Only debt", "40.00", "2026-01-01");

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(bulkPaymentBody(contactId, "100.00", categoryId, String.valueOf(accountId)))
            .when().post("/api/debts/bulk-payment")
            .then()
            .statusCode(200)
            .body("totalApplied", equalTo(40.00f))
            .body("totalUnused", equalTo(60.00f));
    }

    @Test
    void bulkPayment_contactWithoutActiveDebts_returns400() {
        String user = "test-bulk-no-debts-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(user);
        int categoryId = createCategory(user, "Bulk Category " + System.nanoTime());
        int contactId = createContact(user, "Debt-free Contact " + System.nanoTime());

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(bulkPaymentBody(contactId, "100.00", categoryId, String.valueOf(accountId)))
            .when().post("/api/debts/bulk-payment")
            .then()
            .statusCode(400);
    }

    /** The debtor and their debts belong to another User, so neither is reachable. */
    @Test
    void bulkPayment_contactOwnedByAnotherUser_returns404() {
        String owner = "test-bulk-owner-" + System.nanoTime();
        int ownerContactId = createContact(owner, "Owned Debtor " + System.nanoTime());
        createDebt(owner, ownerContactId, "Owned debt", "100.00", "2026-01-01");

        String intruder = "test-bulk-intruder-" + System.nanoTime();
        long accountId = AccountTrackingTestSupport.cashAccountId(intruder);
        int categoryId = createCategory(intruder, "Bulk Category " + System.nanoTime());

        given()
            .header("X-WorkOS-User-Id", intruder)
            .contentType(ContentType.JSON)
            .body(bulkPaymentBody(ownerContactId, "100.00", categoryId, String.valueOf(accountId)))
            .when().post("/api/debts/bulk-payment")
            .then()
            .statusCode(404);
    }

    private String bulkPaymentBody(int contactId, String totalAmount, int categoryId, String accountId) {
        return """
                {
                  "contactId": %d,
                  "totalAmount": %s,
                  "paymentDate": "2026-09-04",
                  "categoryId": %d,
                  "accountId": %s,
                  "notes": "Bulk settlement"
                }
                """.formatted(contactId, totalAmount, categoryId, accountId);
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

    private void createDebt(String user, int contactId, String description, String totalAmount, String createdAt) {
        String body = """
                {
                  "contactId": %d,
                  "description": "%s",
                  "totalAmount": %s,
                  "createdAt": "%s"
                }
                """.formatted(contactId, description, totalAmount, createdAt);

        given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/debts")
            .then().statusCode(201);
    }
}
