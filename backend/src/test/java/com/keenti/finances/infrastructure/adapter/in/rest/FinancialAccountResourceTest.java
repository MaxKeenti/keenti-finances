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

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/status")
            .then().statusCode(200)
            .body("active", equalTo(false))
            .body("transactionNetBalance", equalTo(0))
            .body("accountNetBalance", equalTo(0));

        activate(user, List.of(account("BBVA", "DEBIT", "100.00")))
            .statusCode(400);

        activate(user, List.of(
            account("BBVA", "DEBIT", "0.00"),
            account("Nu", "SAVINGS", "0.00")))
            .statusCode(201)
            .body("size()", equalTo(2))
            .body("[0].balance", equalTo(0.0f))
            .body("[1].balance", equalTo(0.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/status")
            .then().statusCode(200)
            .body("active", equalTo(true))
            .body("activatedAt", equalTo(LocalDate.now().toString()))
            .body("accountNetBalance", equalTo(0.0f));

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
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        long bbva = createAccount(user, "BBVA", "DEBIT", "600.00");
        long nu = createAccount(user, "Nu", "DEBIT", "100.00");

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
    void newUsersMustSetUpAccountsBeforeRecordingActivity() {
        String user = "new-user-account-setup-" + UUID.randomUUID();
        long categoryId = createIncomeCategory(user);
        transaction(user, categoryId, null).statusCode(409);
    }

    @Test
    void archivingAZeroTransferSourceKeepsTheNetBalance() {
        String user = "archive-transfer-net-" + UUID.randomUUID();
        activate(user, List.of(account("Cash", "CASH", "0.00"))).statusCode(201);
        long source = createAccount(user, "Source", "DEBIT", "100.00");
        long destination = createAccount(user, "Destination", "DEBIT", "0.00");
        transfer(user, source, destination, "100.00").statusCode(201);
        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/accounts/{id}/archive", source).then().statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/summary").then().statusCode(200)
            .body("netBalance", equalTo(100.0f));
    }

    @Test
    void restoringActivityRequiresRestoringItsArchivedAccountFirst() {
        String user = "restore-archived-account-" + UUID.randomUUID();
        long categoryId = createIncomeCategory(user);
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201).extract().jsonPath().getLong("[0].id");
        long transactionId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "100.00", "direction", "INGRESS", "description", "Pay",
                "transactionDate", LocalDate.now().toString(), "categoryId", categoryId, "accountId", accountId))
            .when().post("/api/transactions").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).when().delete("/api/transactions/{id}", transactionId)
            .then().statusCode(204);
        given().header("X-WorkOS-User-Id", user).when().post("/api/accounts/{id}/archive", accountId)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/trash/transaction/{id}/restore", transactionId).then().statusCode(409);
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
    void zeroBalanceAccountsCanBeArchivedAndRestored() {
        String user = "account-archive-" + UUID.randomUUID();
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201)
            .extract().jsonPath().getLong("[0].id");

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/accounts/{id}/archive", accountId)
            .then().statusCode(200).body("archived", equalTo(true));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts?archived=true")
            .then().statusCode(200).body("size()", equalTo(1));

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/accounts/{id}/restore", accountId)
            .then().statusCode(200).body("archived", equalTo(false));
    }

    @Test
    void accountsWithABalanceCannotBeArchived() {
        String user = "account-archive-balance-" + UUID.randomUUID();
        long categoryId = createIncomeCategory(user);
        long accountId = activate(user, List.of(account("Cash", "CASH", "0.00")))
            .statusCode(201)
            .extract().jsonPath().getLong("[0].id");
        transaction(user, categoryId, accountId).statusCode(201);

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/accounts/{id}/archive", accountId)
            .then().statusCode(400);
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

    @Test
    void paymentRecordedBeforeStatementConfirmationIsAllocatedWhenConfirmed() {
        String user = "credit-payment-before-statement-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "100.00"), account("PLATA", "CREDIT", "-100.00")))
            .statusCode(201).extract().jsonPath();
        long cash = activation.getLong("[0].id");
        long plata = activation.getLong("[1].id");
        transfer(user, cash, plata, "60.00").statusCode(201);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of(
                "periodStart", LocalDate.now().minusDays(20).toString(),
                "periodEnd", LocalDate.now().minusDays(1).toString(),
                "dueDate", LocalDate.now().plusDays(10).toString(),
                "officialBalance", "100.00", "officialMinimumPayment", "20.00",
                "officialAvoidInterest", "100.00"))
            .when().post("/api/accounts/{id}/credit-statements", plata).then().statusCode(201)
            .body("paidAmount", equalTo(60.0f)).body("outstandingBalance", equalTo(40.0f));
    }

    @Test
    void msiPurchaseAppearsAsOneInstallmentInItsStatementEstimate() {
        String user = "credit-msi-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long plata = activation.getLong("[1].id");
        long categoryId = createEgressCategory(user);
        long purchaseId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "12000.00", "direction", "EGRESS", "description", "Laptop",
                "transactionDate", LocalDate.now().toString(), "categoryId", categoryId, "accountId", plata))
            .when().post("/api/transactions").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("transactionId", purchaseId, "installmentCount", 12,
                "firstInstallmentDate", LocalDate.now().toString()))
            .when().post("/api/accounts/{id}/msi-plans", plata).then().statusCode(201);
        given().header("X-WorkOS-User-Id", user)
            .queryParam("periodEnd", LocalDate.now().toString())
            .when().get("/api/accounts/{id}/credit-statements/estimate", plata)
            .then().statusCode(200).body("estimatedBalance", equalTo(1000.0f));
    }

    @Test
    void currentStatementEstimateUsesTheOpenCycleAndActivityRecordedSoFar() {
        String user = "current-credit-estimate-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long plata = activation.getLong("[1].id");
        int closingDay = LocalDate.now().getDayOfMonth() == LocalDate.now().lengthOfMonth()
            ? 1 : LocalDate.now().getDayOfMonth() + 1;

        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("creditLimit", "50000.00", "statementClosingDay", closingDay,
                "paymentDueDay", closingDay))
            .when().put("/api/accounts/{id}/credit-settings", plata).then().statusCode(200);

        createCreditPurchase(user, plata, "120.00", "Current-cycle purchase");

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}/credit-statements/current-estimate", plata)
            .then().statusCode(200).body("estimatedBalance", equalTo(120.0f));
    }

    @Test
    void changingOrEndingAnMsiPlanKeepsStatementDebtReconciled() {
        String user = "msi-lifecycle-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long plata = activation.getLong("[1].id");
        long categoryId = createEgressCategory(user);
        long purchaseId = given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "12000.00", "direction", "EGRESS", "description", "Laptop",
                "transactionDate", LocalDate.now().toString(), "categoryId", categoryId, "accountId", plata))
            .when().post("/api/transactions").then().statusCode(201).extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("transactionId", purchaseId, "installmentCount", 12,
                "firstInstallmentDate", LocalDate.now().toString()))
            .when().post("/api/accounts/{id}/msi-plans", plata).then().statusCode(201);

        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", "24000.00", "direction", "EGRESS", "description", "Laptop",
                "transactionDate", LocalDate.now().toString(), "categoryId", categoryId, "accountId", plata))
            .when().put("/api/transactions/{id}", purchaseId).then().statusCode(200);
        given().header("X-WorkOS-User-Id", user).queryParam("periodEnd", LocalDate.now().toString())
            .when().get("/api/accounts/{id}/credit-statements/estimate", plata)
            .then().statusCode(200).body("estimatedBalance", equalTo(2000.0f));

        long planId = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}/msi-plans", plata).then().statusCode(200)
            .extract().jsonPath().getLong("[0].id");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("reason", "CANCELLED"))
            .when().post("/api/accounts/{id}/msi-plans/{planId}/end", plata, planId).then().statusCode(200);
        given().header("X-WorkOS-User-Id", user).queryParam("periodEnd", LocalDate.now().toString())
            .when().get("/api/accounts/{id}/credit-statements/estimate", plata)
            .then().statusCode(200).body("estimatedBalance", equalTo(24000.0f));
    }

    @Test
    void deletingAnMsiPurchaseRemovesItsPlanBeforePermanentDeletion() {
        String user = "msi-delete-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long plata = activation.getLong("[1].id");
        long purchaseId = createCreditPurchase(user, plata, "12000.00", "Laptop");
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("transactionId", purchaseId, "installmentCount", 12,
                "firstInstallmentDate", LocalDate.now().toString()))
            .when().post("/api/accounts/{id}/msi-plans", plata).then().statusCode(201);

        given().header("X-WorkOS-User-Id", user).when().delete("/api/transactions/{id}", purchaseId)
            .then().statusCode(204);
        given().header("X-WorkOS-User-Id", user).when().delete("/api/trash/transaction/{id}", purchaseId)
            .then().statusCode(204);
    }

    @Test
    void archivedCreditAccountsRejectStatementAndSettingsChanges() {
        String user = "archived-credit-mutations-" + UUID.randomUUID();
        var activation = activate(user, List.of(
            account("Cash", "CASH", "0.00"), account("PLATA", "CREDIT", "0.00")))
            .statusCode(201).extract().jsonPath();
        long plata = activation.getLong("[1].id");

        given().header("X-WorkOS-User-Id", user).when().post("/api/accounts/{id}/archive", plata)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("creditLimit", "50000.00", "statementClosingDay", 20, "paymentDueDay", 28))
            .when().put("/api/accounts/{id}/credit-settings", plata).then().statusCode(409);
        given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("periodStart", LocalDate.now().minusDays(20).toString(),
                "periodEnd", LocalDate.now().minusDays(1).toString(),
                "dueDate", LocalDate.now().plusDays(10).toString(),
                "officialBalance", "100.00", "officialMinimumPayment", "20.00",
                "officialAvoidInterest", "100.00"))
            .when().post("/api/accounts/{id}/credit-statements", plata).then().statusCode(409);
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

    private static long createAccount(String user, String name, String kind, String openingBalance) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON).body(account(name, kind, openingBalance))
            .when().post("/api/accounts").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long createCreditPurchase(String user, long accountId, String amount, String description) {
        long categoryId = createEgressCategory(user);
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", "EGRESS", "description", description,
                "transactionDate", LocalDate.now().toString(), "categoryId", categoryId, "accountId", accountId))
            .when().post("/api/transactions").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static io.restassured.response.ValidatableResponse transfer(
            String user, long source, long destination, String amount) {
        return given().header("X-WorkOS-User-Id", user).contentType(ContentType.JSON)
            .body(Map.of("sourceAccountId", source, "destinationAccountId", destination,
                "amount", amount, "transferDate", LocalDate.now().toString()))
            .when().post("/api/account-transfers").then();
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

    private static long createEgressCategory(String user) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Expense " + UUID.randomUUID(), "type", "EGRESS", "hue", 20))
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
