package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class FinancialAccountResourceTest {

    @Test
    void activationRequiresAReconciledOpeningBalanceAndCreatesTheLedger() {
        String user = "accounts-" + UUID.randomUUID();
        createIngress(user, "700.00");

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/status")
            .then().statusCode(200)
            .body("active", equalTo(false))
            .body("transactionNetBalance", equalTo(700.0f))
            .body("accountNetBalance", equalTo(0));

        activate(user, List.of(account("BBVA", "DEBIT", "100.00")))
            .statusCode(400);

        activate(user, List.of(
            account("BBVA", "DEBIT", "100.00"),
            account("Nu", "SAVINGS", "600.00")))
            .statusCode(201)
            .body("size()", equalTo(2))
            .body("[0].balance", equalTo(100.0f))
            .body("[1].balance", equalTo(600.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/status")
            .then().statusCode(200)
            .body("active", equalTo(true))
            .body("activatedAt", equalTo(LocalDate.now().toString()))
            .body("accountNetBalance", equalTo(700.0f));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(account("Cash", "CASH", "0.00"))
            .when().post("/api/accounts")
            .then().statusCode(201)
            .body("id", notNullValue())
            .body("openingBalance", equalTo(0.0f));
    }

    @Test
    void transferMovesMoneyBetweenAccountsWithoutChangingNetBalance() {
        String user = "account-transfer-" + UUID.randomUUID();
        createIngress(user, "700.00");
        var activation = activate(user, List.of(
            account("BBVA", "DEBIT", "600.00"),
            account("Nu", "DEBIT", "100.00")))
            .statusCode(201)
            .extract().jsonPath();
        long bbva = activation.getLong("[0].id");
        long nu = activation.getLong("[1].id");

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "sourceAccountId", bbva,
                "destinationAccountId", nu,
                "amount", "500.00",
                "transferDate", LocalDate.now().toString(),
                "notes", "BBVA to Nu"))
            .when().post("/api/account-transfers")
            .then().statusCode(201)
            .body("sourceAccountName", equalTo("BBVA"))
            .body("destinationAccountName", equalTo("Nu"));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}", bbva)
            .then().statusCode(200).body("balance", equalTo(100.0f));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}", nu)
            .then().statusCode(200).body("balance", equalTo(600.0f));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/summary")
            .then().statusCode(200).body("netBalance", equalTo(700.0f));
    }

    @Test
    void postActivationTransactionsRequireAndUpdateTheirFinancialAccount() {
        String user = "account-transaction-" + UUID.randomUUID();
        long categoryId = createIncomeCategory(user);
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201)
            .extract().jsonPath().getLong("[0].id");

        transaction(user, categoryId, null).statusCode(400);
        transaction(user, categoryId, accountId).statusCode(201);

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}", accountId)
            .then().statusCode(200).body("balance", equalTo(100.0f));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/summary")
            .then().statusCode(200).body("netBalance", equalTo(100.0f));
    }

    @Test
    void creditTransfersAllocateToTheOldestConfirmedStatement() {
        String user = "credit-statement-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long cash = activation.getLong("[0].id");
        long plata = activation.getLong("[1].id");
        long incomeCategory = createIncomeCategory(user);
        transaction(user, incomeCategory, cash, "100.00").statusCode(201);

        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of(
                "periodStart", LocalDate.now().minusDays(20).toString(),
                "periodEnd", LocalDate.now().minusDays(1).toString(),
                "dueDate", LocalDate.now().plusDays(10).toString(),
                "officialBalance", "100.00", "officialMinimumPayment", "20.00",
                "officialAvoidInterest", "100.00"))
            .when().post("/api/accounts/{id}/credit-statements", plata)
            .then().statusCode(201).body("estimatedBalance", equalTo(0));

        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("sourceAccountId", cash, "destinationAccountId", plata,
                "amount", "60.00", "transferDate", LocalDate.now().toString()))
            .when().post("/api/account-transfers")
            .then().statusCode(201);

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}/credit-statements", plata)
            .then().statusCode(200).body("size()", equalTo(1))
            .body("[0].paidAmount", equalTo(60.0f))
            .body("[0].outstandingBalance", equalTo(40.0f));
    }

    private static io.restassured.response.ValidatableResponse activate(
            String user, List<Map<String, String>> accounts) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("activationDate", LocalDate.now().toString(), "accounts", accounts))
            .when().post("/api/accounts/activate")
            .then();
    }

    private static Map<String, String> account(String name, String kind, String openingBalance) {
        return Map.of("name", name, "kind", kind, "openingBalance", openingBalance);
    }

    private static void createIngress(String user, String amount) {
        long categoryId = createIncomeCategory(user);

        transaction(user, categoryId, null, amount).statusCode(201);
    }

    private static long createIncomeCategory(String user) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Income " + UUID.randomUUID(), "type", "INGRESS", "hue", 120))
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static io.restassured.response.ValidatableResponse transaction(
            String user, long categoryId, Long accountId) {
        return transaction(user, categoryId, accountId, "100.00");
    }

    private static io.restassured.response.ValidatableResponse transaction(
            String user, long categoryId, Long accountId, String amount) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("amount", amount);
        body.put("direction", "INGRESS");
        body.put("description", "Account setup income");
        body.put("transactionDate", LocalDate.now().toString());
        body.put("categoryId", categoryId);
        if (accountId != null) body.put("accountId", accountId);
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/transactions")
            .then();
    }
}
