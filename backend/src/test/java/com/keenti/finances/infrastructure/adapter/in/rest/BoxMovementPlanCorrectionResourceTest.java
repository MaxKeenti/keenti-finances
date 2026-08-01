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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BoxMovementPlanCorrectionResourceTest {

    @Inject
    EntityManager em;

    @Test
    void backdatedCorrectionRecalculatesSavingGoalOutcomesUnderOriginalRevisions() {
        String user = user("saving-goal");
        createIngress(user, "1000.00");
        long boxId = createBox(user, "Corrected goal");
        LocalDate today = LocalDate.now();

        long planId = createGoal(user, boxId, "500.00", today, "100.00")
            .extract().jsonPath().getLong("id");
        var revised = given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(goalBody("600.00", today.plusDays(2), "100.00"))
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/revisions",
                boxId, planId)
            .then().statusCode(200)
            .extract().jsonPath();
        long firstRevisionId = revised.getLong("revisions[0].id");
        long secondRevisionId = revised.getLong("revisions[1].id");

        LocalDate firstStart = today.minusDays(2);
        LocalDate secondStart = today.minusDays(1);
        shiftPlanTimeline(planId, firstRevisionId, firstStart,
            secondRevisionId, secondStart);
        deposit(user, boxId, "100.00", secondStart);
        long movementId = movementId(user, boxId, "DEPOSIT");

        var before = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(2))
            .body("periods[0].revisionId", equalTo((int) firstRevisionId))
            .body("periods[0].status", equalTo("MISSED"))
            .body("periods[0].shortfall", equalTo(100.0f))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId))
            .body("periods[1].openingArrears", equalTo(100.0f))
            .body("periods[1].netProgress", equalTo(100.0f))
            .body("periods[1].shortfall", equalTo(100.0f))
            .extract().jsonPath();
        int firstPeriodId = before.getInt("periods[0].id");
        int secondPeriodId = before.getInt("periods[1].id");

        correctMovement(user, boxId, movementId, "100.00", firstStart)
            .statusCode(200)
            .body("box.balance", equalTo(100.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                boxId, planId)
            .then().statusCode(200)
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[0].revisionId", equalTo((int) firstRevisionId))
            .body("periods[0].status", equalTo("ACHIEVED"))
            .body("periods[0].shortfall", equalTo(0.0f))
            .body("periods[1].id", equalTo(secondPeriodId))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId))
            .body("periods[1].openingArrears", equalTo(0.0f))
            .body("periods[1].netProgress", equalTo(0.0f))
            .body("periods[1].shortfall", equalTo(100.0f));

        correctMovement(user, boxId, movementId, "1100.00", firstStart)
            .statusCode(400);
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .body("[0].id", equalTo((int) movementId))
            .body("[0].amount", equalTo(100.0f))
            .body("[0].effectiveDate", equalTo(firstStart.toString()))
            .body("[0].runningBalance", equalTo(100.0f));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                boxId, planId)
            .then().statusCode(200)
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[0].status", equalTo("ACHIEVED"))
            .body("periods[1].id", equalTo(secondPeriodId))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId));
    }

    @Test
    void backdatedCorrectionRecalculatesBudgetBreakdownUnderOriginalRevisions() {
        String user = user("spending-budget");
        createIngress(user, "1000.00");
        long boxId = createBox(user, "Corrected budget");
        LocalDate today = LocalDate.now();

        long planId = createBudget(user, boxId, "200.00")
            .extract().jsonPath().getLong("id");
        var revised = given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(budgetBody("300.00"))
            .when().post(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}/revisions",
                boxId, planId)
            .then().statusCode(200)
            .extract().jsonPath();
        long firstRevisionId = revised.getLong("revisions[0].id");
        long secondRevisionId = revised.getLong("revisions[1].id");

        LocalDate firstStart = today.minusDays(2);
        LocalDate secondStart = today.minusDays(1);
        shiftPlanTimeline(planId, firstRevisionId, firstStart,
            secondRevisionId, secondStart);
        deposit(user, boxId, "100.00", secondStart);
        long movementId = movementId(user, boxId, "DEPOSIT");

        var before = given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(2))
            .body("periods[0].revisionId", equalTo((int) firstRevisionId))
            .body("periods[0].deposits", equalTo(0))
            .body("periods[0].suggestedTopUp", equalTo(200.0f))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId))
            .body("periods[1].deposits", equalTo(100.0f))
            .body("periods[1].suggestedTopUp", equalTo(200.0f))
            .extract().jsonPath();
        int firstPeriodId = before.getInt("periods[0].id");
        int secondPeriodId = before.getInt("periods[1].id");

        correctMovement(user, boxId, movementId, "100.00", firstStart)
            .statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[0].revisionId", equalTo((int) firstRevisionId))
            .body("periods[0].deposits", equalTo(100.0f))
            .body("periods[0].closingBalance", equalTo(100.0f))
            .body("periods[0].suggestedTopUp", equalTo(100.0f))
            .body("periods[1].id", equalTo(secondPeriodId))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId))
            .body("periods[1].openingBalance", equalTo(100.0f))
            .body("periods[1].deposits", equalTo(0))
            .body("periods[1].suggestedTopUp", equalTo(200.0f));

        correctMovement(user, boxId, movementId, "100.00", today.plusDays(1))
            .statusCode(400);
        given().header("X-WorkOS-User-Id", user)
            .when().get(
                "/api/boxes/{boxId}/plans/spending-budget/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[0].deposits", equalTo(100.0f))
            .body("periods[1].id", equalTo(secondPeriodId))
            .body("periods[1].revisionId", equalTo((int) secondRevisionId));
    }

    private void shiftPlanTimeline(long planId, long firstRevisionId,
                                   LocalDate firstStart, long secondRevisionId,
                                   LocalDate secondStart) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery(
                    "UPDATE box_plan SET start_date = :start WHERE id = :planId")
                .setParameter("start", firstStart)
                .setParameter("planId", planId)
                .executeUpdate();
            em.createNativeQuery("""
                    UPDATE box_plan_revision
                    SET effective_from = :effectiveFrom
                    WHERE id = :revisionId AND plan_id = :planId
                    """)
                .setParameter("effectiveFrom", firstStart)
                .setParameter("revisionId", firstRevisionId)
                .setParameter("planId", planId)
                .executeUpdate();
            em.createNativeQuery("""
                    UPDATE box_plan_revision
                    SET effective_from = :effectiveFrom
                    WHERE id = :revisionId AND plan_id = :planId
                    """)
                .setParameter("effectiveFrom", secondStart)
                .setParameter("revisionId", secondRevisionId)
                .setParameter("planId", planId)
                .executeUpdate();
        });
    }

    private static io.restassured.response.ValidatableResponse createGoal(
            String user, long boxId, String targetAmount, LocalDate targetDate,
            String commitment) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(goalBody(targetAmount, targetDate, commitment))
            .when().post("/api/boxes/{boxId}/plans/saving-goal", boxId)
            .then().statusCode(201);
    }

    private static Map<String, Object> goalBody(
            String targetAmount, LocalDate targetDate, String commitment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetAmount", targetAmount);
        body.put("targetDate", targetDate.toString());
        body.put("cadence", "DAILY");
        body.put("regularCommitment", commitment);
        return body;
    }

    private static io.restassured.response.ValidatableResponse createBudget(
            String user, long boxId, String desiredBalance) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(budgetBody(desiredBalance))
            .when().post("/api/boxes/{boxId}/plans/spending-budget", boxId)
            .then().statusCode(201);
    }

    private static Map<String, Object> budgetBody(String desiredBalance) {
        return Map.of("desiredBalance", desiredBalance, "cadence", "DAILY");
    }

    private static void createIngress(String user, String amount) {
        long categoryId = given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Income " + UUID.randomUUID(),
                "type", "INGRESS", "hue", 120))
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "direction", "INGRESS",
                "description", "Correction funds",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }

    private static long createBox(String user, String name) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "hue", 75))
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static void deposit(
            String user, long boxId, String amount, LocalDate date) {
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amountBody(amount, date))
            .when().post("/api/boxes/{id}/deposit", boxId)
            .then().statusCode(200);
    }

    private static long movementId(String user, long boxId, String type) {
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .extract().jsonPath().getLong(
                "find { movement -> movement.type == '" + type + "' }.id");
    }

    private static io.restassured.response.ValidatableResponse correctMovement(
            String user, long boxId, long movementId, String amount,
            LocalDate effectiveDate) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amountBody(amount, effectiveDate))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                boxId, movementId)
            .then();
    }

    private static Map<String, Object> amountBody(
            String amount, LocalDate effectiveDate) {
        return Map.of("amount", amount, "effectiveDate", effectiveDate.toString());
    }

    private static String user(String suffix) {
        return "test-plan-correction-" + suffix + "-" + UUID.randomUUID();
    }
}
