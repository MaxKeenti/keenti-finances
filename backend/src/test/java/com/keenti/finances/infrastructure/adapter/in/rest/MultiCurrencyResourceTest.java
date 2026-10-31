package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/** ADR-0024: currency belongs to the Financial Account; totals are per currency. */
@QuarkusTest
class MultiCurrencyResourceTest {

    private static final String TODAY = LocalDate.now().toString();

    @Test
    void accountsDefaultToThePrimaryCurrencyAndKeepTheirOwn() {
        String user = user();
        activate(user);
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(account("Santander ES", "DEBIT", "EUR", "250.00"))
            .when().post("/api/accounts")
            .then().statusCode(201)
            .body("currency", equalTo("EUR"))
            .body("balance", equalTo(250.0f));

        setPrimaryCurrency(user, "EUR");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(account("Cash EUR", "CASH", null, "0.00"))
            .when().post("/api/accounts")
            .then().statusCode(201)
            .body("currency", equalTo("EUR"));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(account("Yen", "CASH", "JPY", "0.00"))
            .when().post("/api/accounts")
            .then().statusCode(400);
    }

    @Test
    void headlineFiguresAreStatedPerCurrency() {
        String user = user();
        long mxn = activate(user);
        long eur = createAccount(user, "Wise EUR", "EUR", "0.00");
        long income = category(user, "INGRESS");
        long expense = category(user, "EGRESS");
        transaction(user, mxn, income, "INGRESS", "1000.00").statusCode(201).body("currency", equalTo("MXN"));
        transaction(user, eur, income, "INGRESS", "300.00").statusCode(201).body("currency", equalTo("EUR"));
        transaction(user, eur, expense, "EGRESS", "45.50").statusCode(201);

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary")
            .then().statusCode(200)
            .body("currency", equalTo("MXN"))
            .body("netBalance", equalTo(1000.0f))
            .body("currencies", contains("MXN", "EUR"));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary?currency=EUR")
            .then().statusCode(200)
            .body("currency", equalTo("EUR"))
            .body("netBalance", equalTo(254.5f))
            .body("availableToSpend", equalTo(254.5f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?currency=EUR")
            .then().statusCode(200)
            .body("currency", equalTo("EUR"))
            .body("position.data.netBalance", equalTo(254.5f))
            .body("position.data.accounts.size()", equalTo(1))
            .body("history.data.totalIngress", equalTo(300.0f))
            .body("history.data.totalEgress", equalTo(45.5f))
            // 1000 MXN + 254.50 EUR at 20 MXN/EUR
            .body("convertedTotal.data.currency", equalTo("MXN"))
            .body("convertedTotal.data.total", equalTo(6090.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary?currency=XYZ")
            .then().statusCode(400);
    }

    @Test
    void anExchangeTransferRecordsWhatLeftAndWhatArrived() {
        String user = user();
        long mxn = activate(user);
        long bbva = createAccount(user, "BBVA", "MXN", "25000.00");
        long wise = createAccount(user, "Wise", "EUR", "0.00");

        transfer(user, bbva, wise, "21480.00", null).statusCode(400);

        transfer(user, bbva, wise, "21480.00", "1000.00").statusCode(201)
            .body("sourceCurrency", equalTo("MXN"))
            .body("destinationCurrency", equalTo("EUR"))
            .body("amount", equalTo(21480.0f))
            .body("destinationAmount", equalTo(1000.0f));

        balance(user, bbva).body("balance", equalTo(3520.0f));
        balance(user, wise).body("balance", equalTo(1000.0f));

        // Same-currency Transfers move one amount.
        transfer(user, bbva, mxn, "100.00", "90.00").statusCode(400);
        transfer(user, bbva, mxn, "100.00", null).statusCode(201).body("destinationAmount", equalTo(100.0f));
    }

    @Test
    void aTransactionKeepsItsCurrencyAndBoxesMustMatchIt() {
        String user = user();
        long mxn = activate(user);
        long eur = createAccount(user, "Wise", "EUR", "500.00");
        long expense = category(user, "EGRESS");

        long eurBox = box(user, "Groceries Madrid", "EUR");
        long mxnBox = box(user, "Ahorro", null);
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}", mxnBox)
            .then().statusCode(200).body("currency", equalTo("MXN"));

        // A EUR Box is funded from EUR Available to Spend only.
        deposit(user, eurBox, "200.00").statusCode(200)
            .body("summary.currency", equalTo("EUR"))
            .body("summary.availableToSpend", equalTo(300.0f));
        deposit(user, mxnBox, "1.00").statusCode(400);

        Map<String, Object> funded = transactionBody(eur, expense, "EGRESS", "50.00");
        funded.put("boxFunding", List.of(Map.of("boxId", mxnBox, "amount", "10.00")));
        post(user, "/api/transactions", funded).statusCode(400);

        funded.put("boxFunding", List.of(Map.of("boxId", eurBox, "amount", "50.00")));
        long txId = post(user, "/api/transactions", funded).statusCode(201)
            .extract().jsonPath().getLong("id");

        // Moving it to an MXN Account would change what the amount means.
        Map<String, Object> moved = transactionBody(mxn, expense, "EGRESS", "50.00");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(moved)
            .when().put("/api/transactions/{id}", txId)
            .then().statusCode(400);

        post(user, "/api/boxes/" + eurBox + "/transfer", Map.of(
                "targetBoxId", mxnBox, "amount", "1.00", "effectiveDate", TODAY))
            .statusCode(400);
    }

    @Test
    void debtsSettleOnlyThroughAnAccountInTheirCurrency() {
        String user = user();
        long mxn = activate(user);
        long eur = createAccount(user, "Wise", "EUR", "0.00");
        long income = category(user, "INGRESS");
        long contact = contact(user, "Lucía");

        long debtId = post(user, "/api/debts", Map.of(
                "contactId", contact, "direction", "INGRESS", "currency", "EUR",
                "description", "Utilities split", "totalAmount", "60.00"))
            .statusCode(201).body("currency", equalTo("EUR"))
            .extract().jsonPath().getLong("id");

        post(user, "/api/debts/" + debtId + "/payments", Map.of(
                "amount", "20.00", "paymentDate", TODAY, "categoryId", income, "accountId", mxn))
            .statusCode(400);
        post(user, "/api/debts/" + debtId + "/payments", Map.of(
                "amount", "20.00", "paymentDate", TODAY, "categoryId", income, "accountId", eur))
            .statusCode(201);
        balance(user, eur).body("balance", equalTo(20.0f));

        // Recorded payments freeze the currency.
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("contactId", contact, "direction", "INGRESS", "currency", "MXN",
                "description", "Utilities split", "totalAmount", "60.00"))
            .when().put("/api/debts/{id}", debtId)
            .then().statusCode(400);

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?currency=EUR")
            .then().statusCode(200)
            .body("expected.data.debtsOutstanding", equalTo(40.0f));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/overview?currency=MXN")
            .then().statusCode(200)
            .body("expected.data.debtsOutstanding", equalTo(0));
    }

    @Test
    void subscriptionsCarryTheirCurrencyToThePublicView() {
        String user = user();
        activate(user);
        post(user, "/api/subscriptions", Map.of(
                "name", "Flat internet", "cost", "30.00", "currency", "EUR",
                "billingCycle", "MONTHLY", "type", "SHARED", "nextBillingDate", TODAY))
            .statusCode(201)
            .body("currency", equalTo("EUR"));
    }

    @Test
    void primaryCurrencyIsAPreferenceFromTheSupportedSet() {
        String user = user();
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/user/preferences")
            .then().statusCode(200)
            .body("primaryCurrency", equalTo("MXN"))
            .body("supportedCurrencies", hasItem("EUR"));

        Map<String, Object> preferences = preferences(user);
        preferences.put("primaryCurrency", "JPY");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(preferences)
            .when().put("/api/user/preferences")
            .then().statusCode(400);

        setPrimaryCurrency(user, "EUR");
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/user/preferences")
            .then().statusCode(200).body("primaryCurrency", equalTo("EUR"));
    }

    /* ------------------------------------------------------------------ */

    private static String user() {
        return "multi-currency-" + UUID.randomUUID();
    }

    private static long activate(String user) {
        return AccountTrackingTestSupport.cashAccountId(user);
    }

    private static Map<String, Object> account(String name, String kind, String currency, String opening) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("kind", kind);
        body.put("hue", 200);
        body.put("openingBalance", opening);
        if (currency != null) {
            body.put("currency", currency);
        }
        return body;
    }

    private static long createAccount(String user, String name, String currency, String opening) {
        return post(user, "/api/accounts", account(name, "DEBIT", currency, opening))
            .statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long category(String user, String direction) {
        return post(user, "/api/categories", Map.of(
                "name", direction + " " + UUID.randomUUID(), "type", direction, "hue", 120))
            .statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long contact(String user, String name) {
        return post(user, "/api/contacts", Map.of("name", name))
            .statusCode(201).extract().jsonPath().getLong("id");
    }

    private static long box(String user, String name, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("hue", 90);
        if (currency != null) {
            body.put("currency", currency);
        }
        return post(user, "/api/boxes", body).statusCode(201).extract().jsonPath().getLong("id");
    }

    private static ValidatableResponse deposit(String user, long boxId, String amount) {
        return post(user, "/api/boxes/" + boxId + "/deposit", Map.of("amount", amount, "effectiveDate", TODAY));
    }

    private static Map<String, Object> transactionBody(long accountId, long categoryId, String direction,
                                                       String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("direction", direction);
        body.put("description", "Test " + direction);
        body.put("transactionDate", TODAY);
        body.put("categoryId", categoryId);
        body.put("accountId", accountId);
        return body;
    }

    private static ValidatableResponse transaction(String user, long accountId, long categoryId,
                                                   String direction, String amount) {
        return post(user, "/api/transactions", transactionBody(accountId, categoryId, direction, amount));
    }

    private static ValidatableResponse transfer(String user, long source, long destination, String amount,
                                                String destinationAmount) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceAccountId", source);
        body.put("destinationAccountId", destination);
        body.put("amount", amount);
        body.put("transferDate", TODAY);
        if (destinationAmount != null) {
            body.put("destinationAmount", destinationAmount);
        }
        return post(user, "/api/account-transfers", body);
    }

    private static ValidatableResponse balance(String user, long accountId) {
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/accounts/{id}", accountId)
            .then().statusCode(200);
    }

    private static Map<String, Object> preferences(String user) {
        return new HashMap<>(given().header("X-WorkOS-User-Id", user)
            .when().get("/api/user/preferences")
            .then().statusCode(200)
            .extract().jsonPath().getMap(""));
    }

    private static void setPrimaryCurrency(String user, String currency) {
        Map<String, Object> preferences = preferences(user);
        preferences.remove("supportedCurrencies");
        preferences.put("primaryCurrency", currency);
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(preferences)
            .when().put("/api/user/preferences")
            .then().statusCode(200);
    }

    private static ValidatableResponse post(String user, String path, Object body) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(path)
            .then();
    }
}
