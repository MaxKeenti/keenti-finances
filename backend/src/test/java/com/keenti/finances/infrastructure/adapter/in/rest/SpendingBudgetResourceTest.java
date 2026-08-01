package com.keenti.finances.infrastructure.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpendingBudgetResourceTest {

    @Inject
    EntityManager em;

    @Test
    void rolloverUsesCurrentBalanceAndProspectiveRevisionKeepsCurrentTerms() {
        String user = user("rollover");
        createIngress(user, "3000.00");
        long boxId = createBox(user, "Weekly groceries");
        deposit(user, boxId, "400.00", LocalDate.now());

        long planId = createBudget(user, boxId, "2000.00", "DAILY", null, null)
            .statusCode(201)
            .body("type", equalTo("SPENDING_BUDGET"))
            .body("boxBalance", equalTo(400.0f))
            .body("suggestedTopUp", equalTo(1600.0f))
            .body("currentPeriod.openingBalance", equalTo(400.0f))
            .extract().jsonPath().getLong("id");

        Map<String, Object> revised = budgetBody(
            "2500.00", "DAILY", null, null);
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(revised)
            .when().post(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}/revision-preview",
                boxId, planId)
            .then().statusCode(200)
            .body("effectiveFrom", equalTo(LocalDate.now().plusDays(1).toString()))
            .body("desiredBalance", equalTo(2500.0f))
            .body("suggestedTopUp", equalTo(2100.0f));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(revised)
            .when().post(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}/revisions",
                boxId, planId)
            .then().statusCode(200)
            .body("desiredBalance", equalTo(2000.0f))
            .body("revisions", hasSize(2))
            .body("revisions[1].desiredBalance", equalTo(2500.0f))
            .body("revisions[1].scheduled", equalTo(true));
    }

    @Test
    void skippedPeriodsAreIdempotentAndCorrectionsRecalculateTheirBreakdown() {
        String user = user("catchup");
        createIngress(user, "5000.00");
        long boxId = createBox(user, "Daily spending");
        long planId = createBudget(user, boxId, "2000.00", "DAILY", null, null)
            .statusCode(201)
            .extract().jsonPath().getLong("id");
        LocalDate shiftedStart = LocalDate.now().minusDays(2);
        shiftBudgetTimeline(planId, shiftedStart);

        deposit(user, boxId, "2000.00", shiftedStart);
        createFundedEgress(user, boxId, "1600.00", shiftedStart);

        Integer firstPeriodId = given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(2))
            .body("periods[0].deposits", equalTo(2000.0f))
            .body("periods[0].fundedSpending", equalTo(1600.0f))
            .body("periods[0].closingBalance", equalTo(400.0f))
            .body("periods[0].suggestedTopUp", equalTo(1600.0f))
            .body("currentPeriod.openingBalance", equalTo(400.0f))
            .extract().jsonPath().getInt("periods[0].id");

        given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(2))
            .body("periods[0].id", equalTo(firstPeriodId));

        deposit(user, boxId, "100.00", shiftedStart.plusDays(1));
        given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[1].deposits", equalTo(100.0f))
            .body("periods[1].closingBalance", equalTo(500.0f))
            .body("suggestedTopUp", equalTo(1500.0f));
    }

    @Test
    void overTargetHasZeroSuggestionAndEndingAllowsAnotherPlan() {
        String user = user("lifecycle");
        createIngress(user, "3000.00");
        long boxId = createBox(user, "Household");
        deposit(user, boxId, "2500.00", LocalDate.now());
        long planId = createBudget(user, boxId, "2000.00", "WEEKLY", 5, null)
            .statusCode(201)
            .body("suggestedTopUp", equalTo(0))
            .extract().jsonPath().getLong("id");

        createBudget(user, boxId, "1000.00", "DAILY", null, null)
            .statusCode(409);
        given().header("X-WorkOS-User-Id", user)
            .when().post(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}/end", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("ENDED"));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", "500.00",
                "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/withdraw", boxId)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("ENDED"))
            .body("boxBalance", equalTo(2500.0f))
            .body("suggestedTopUp", equalTo(0));

        createBudget(user, boxId, "1000.00", "MONTHLY", null, 31)
            .statusCode(201);
    }

    @Test
    void callerScopedBoxAndPlanIdentifiersDoNotLeak() {
        String alice = user("alice");
        String bob = user("bob");
        long boxId = createBox(alice, "Alice budget");
        long planId = createBudget(
            alice, boxId, "1000.00", "DAILY", null, null)
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{id}/archive", boxId)
            .then().statusCode(409);

        given().header("X-WorkOS-User-Id", bob)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(404);
        createBudget(bob, boxId, "1000.00", "DAILY", null, null)
            .statusCode(404);

        given().header("X-WorkOS-User-Id", alice)
            .when().post(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}/end", boxId, planId)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{id}/archive", boxId)
            .then().statusCode(200)
            .body("archived", equalTo(true));
    }

    private void shiftBudgetTimeline(long planId, LocalDate start) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE box_plan SET start_date = :start WHERE id = :id")
                .setParameter("start", start)
                .setParameter("id", planId)
                .executeUpdate();
            em.createNativeQuery("""
                    UPDATE box_plan_revision
                    SET effective_from = :start
                    WHERE plan_id = :id
                    """)
                .setParameter("start", start)
                .setParameter("id", planId)
                .executeUpdate();
        });
    }

    private static String user(String suffix) {
        return "test-spending-budget-" + suffix + "-" + UUID.randomUUID();
    }

    private static long createBox(String user, String name) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "hue", 210))
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static io.restassured.response.ValidatableResponse createBudget(
            String user, long boxId, String desiredBalance, String cadence,
            Integer weekday, Integer monthDay) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(budgetBody(desiredBalance, cadence, weekday, monthDay))
            .when().post("/api/boxes/{boxId}/plans/spending-budget", boxId)
            .then();
    }

    private static Map<String, Object> budgetBody(
            String desiredBalance, String cadence, Integer weekday,
            Integer monthDay) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("desiredBalance", desiredBalance);
        body.put("cadence", cadence);
        if (weekday != null) body.put("anchorWeekday", weekday);
        if (monthDay != null) body.put("anchorDayOfMonth", monthDay);
        return body;
    }

    private static void createIngress(String user, String amount) {
        long categoryId = createCategory(user, "INGRESS");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", amount,
                "direction", "INGRESS",
                "description", "Budget funding",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }

    private static void createFundedEgress(
            String user, long boxId, String amount, LocalDate date) {
        long categoryId = createCategory(user, "EGRESS");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", amount,
                "direction", "EGRESS",
                "description", "Groceries",
                "transactionDate", date.toString(),
                "categoryId", categoryId,
                "boxFunding", List.of(Map.of("boxId", boxId, "amount", amount))))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }

    private static long createCategory(String user, String type) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "name", type + " " + UUID.randomUUID(),
                "type", type,
                "hue", 120))
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static void deposit(
            String user, long boxId, String amount, LocalDate date) {
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", date.toString()))
            .when().post("/api/boxes/{id}/deposit", boxId)
            .then().statusCode(200);
    }
}
