package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SavingGoalResourceTest {

    @Inject
    EntityManager em;

    @Test
    void schedulingStartsOnTheUsersLocalDate() {
        String user = user("time-zone");
        long boxId = createBox(user, "Local-date goal");
        Instant now = Instant.now();
        LocalDate systemDate = LocalDate.ofInstant(now, ZoneId.systemDefault());
        ZoneId userZone = ZoneId.getAvailableZoneIds().stream()
            .map(ZoneId::of)
            .filter(zone -> !LocalDate.ofInstant(now, zone).equals(systemDate))
            .findFirst()
            .orElseThrow();
        LocalDate userDate = LocalDate.ofInstant(now, userZone);
        QuarkusTransaction.requiringNew().run(() ->
            em.createNativeQuery("""
                    UPDATE app_user
                    SET time_zone = :timeZone
                    WHERE workos_id = :workosId
                    """)
                .setParameter("timeZone", userZone.getId())
                .setParameter("workosId", user)
                .executeUpdate());

        createGoal(user, boxId, "100.00", userDate,
            "DAILY", null, null, null)
            .statusCode(201)
            .body("targetDate", equalTo(userDate.toString()))
            .body("currentPeriod.startDate", equalTo(userDate.toString()))
            .body("currentPeriod.endDate", equalTo(userDate.toString()));
    }

    @Test
    void createAndProspectiveRevisionExposeStableRoundedCommitments() {
        String user = user("revision");
        createIngress(user, "1000.00");
        long boxId = createBox(user, "Vacation");
        deposit(user, boxId, "100.00", LocalDate.now());

        long planId = createGoal(user, boxId, "400.00",
            LocalDate.now().plusDays(2), "DAILY", null, null, null)
            .body("status", equalTo("ACTIVE"))
            .body("regularCommitment", equalTo(100.0f))
            .body("currentCommitment", equalTo(100.0f))
            .body("progressPercent", equalTo(25.0f))
            .body("currentPeriod.status", equalTo("OPEN"))
            .extract().jsonPath().getLong("id");

        Map<String, Object> revision = goalBody(
            "500.00", LocalDate.now().plusDays(3), "DAILY",
            null, null, null);
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(revision)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/revision-preview",
                boxId, planId)
            .then().statusCode(200)
            .body("effectiveFrom", equalTo(LocalDate.now().plusDays(1).toString()))
            .body("remainingPeriods", equalTo(3))
            .body("regularCommitment", equalTo(133.34f));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(revision)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/revisions",
                boxId, planId)
            .then().statusCode(200)
            .body("targetAmount", equalTo(400.0f))
            .body("revisions", hasSize(2))
            .body("revisions[1].targetAmount", equalTo(500.0f))
            .body("revisions[1].scheduled", equalTo(true));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans", boxId)
            .then().statusCode(200)
            .body("[0].id", equalTo((int) planId))
            .body("[0].type", equalTo("SAVING_GOAL"));
    }

    @Test
    void catchUpIsIdempotentAndBackdatedMoneyRecomputesOutcomesNotTerms() {
        String user = user("catchup");
        createIngress(user, "1000.00");
        long boxId = createBox(user, "Catch up");
        long planId = createGoal(user, boxId, "500.00", LocalDate.now(),
            "DAILY", null, null, "100.00")
            .extract().jsonPath().getLong("id");

        LocalDate shiftedStart = LocalDate.now().minusDays(3);
        shiftGoalTimeline(planId, shiftedStart, LocalDate.now());
        deposit(user, boxId, "50.00", shiftedStart);

        var first = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(3))
            .body("periods[0].shortfall", equalTo(50.0f))
            .body("periods[1].openingArrears", equalTo(50.0f))
            .body("periods[2].shortfall", equalTo(250.0f))
            .body("currentPeriod.openingArrears", equalTo(250.0f))
            .extract().jsonPath();
        Integer firstPeriodId = first.getInt("periods[0].id");
        Integer revisionId = first.getInt("periods[1].revisionId");

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(3))
            .body("periods[0].id", equalTo(firstPeriodId));

        deposit(user, boxId, "100.00", shiftedStart.plusDays(1));
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(3))
            .body("periods[0].id", equalTo(firstPeriodId))
            .body("periods[1].revisionId", equalTo(revisionId))
            .body("periods[1].shortfall", equalTo(50.0f))
            .body("periods[2].openingArrears", equalTo(50.0f));
    }

    @Test
    void concurrentPlanListsMaterializePeriodsOnce() throws Exception {
        String user = user("concurrent-list");
        long boxId = createBox(user, "Concurrent goal list");
        long planId = createGoal(user, boxId, "500.00", LocalDate.now(),
            "DAILY", null, null, "10.00")
            .statusCode(201)
            .extract().jsonPath().getLong("id");
        shiftGoalTimeline(planId, LocalDate.now().minusDays(30), LocalDate.now());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(() -> planListStatus(user, boxId, start));
        Future<Integer> second = executor.submit(() -> planListStatus(user, boxId, start));
        try {
            start.countDown();
            assertEquals(List.of(200, 200), List.of(first.get(), second.get()));
        } finally {
            executor.shutdownNow();
        }

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("periods", hasSize(30));
    }

    @Test
    void reachingTargetRequiresConfirmationAndCompletionSurvivesLaterWithdrawal() {
        String user = user("complete");
        createIngress(user, "500.00");
        long boxId = createBox(user, "Emergency");
        deposit(user, boxId, "100.00", LocalDate.now());
        long planId = createGoal(user, boxId, "100.00", LocalDate.now(),
            "DAILY", null, null, null)
            .body("status", equalTo("READY_TO_COMPLETE"))
            .body("regularCommitment", equalTo(0.0f))
            .extract().jsonPath().getLong("id");

        createGoalRevision(user, boxId, planId, "200.00",
            LocalDate.now().plusDays(2))
            .body("targetAmount", equalTo(100.0f))
            .body("revisions[1].scheduled", equalTo(true));

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/complete",
                boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("completionAmount", equalTo(100.0f))
            .body("targetAmount", equalTo(100.0f))
            .body("revisions[1].scheduled", equalTo(false))
            .body("revisions[1].supersededAt", notNullValue());

        withdraw(user, boxId, "100.00", LocalDate.now());
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("targetAmount", equalTo(100.0f))
            .body("boxBalance", equalTo(100.0f))
            .body("progressPercent", equalTo(100.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", boxId)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("boxBalance", equalTo(100.0f))
            .body("completionAmount", equalTo(100.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal", boxId)
            .then().statusCode(404);
    }

    @Test
    void overdueGoalSuggestsAnExtensionAndUserMayAbandonIt() {
        String user = user("overdue");
        createIngress(user, "500.00");
        long boxId = createBox(user, "Overdue");
        long planId = createGoal(user, boxId, "300.00", LocalDate.now(),
            "DAILY", null, null, "100.00")
            .extract().jsonPath().getLong("id");
        createGoalRevision(user, boxId, planId, "400.00",
            LocalDate.now().plusDays(2))
            .body("revisions[1].scheduled", equalTo(true));
        shiftGoalTimeline(planId, LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("OVERDUE"))
            .body("currentPeriod", nullValue())
            .body("remainingAmount", equalTo(300.0f))
            .body("suggestedExtensionDate", notNullValue());

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/abandon",
                boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"))
            .body("targetAmount", equalTo(300.0f))
            .body("revisions[1].scheduled", equalTo(false))
            .body("revisions[1].supersededAt", notNullValue());

        createGoal(user, boxId, "400.00", LocalDate.now().plusDays(2),
            "DAILY", null, null, null).statusCode(201);
    }

    @Test
    void abandonedGoalKeepsItsClosingBalanceAfterLaterBoxUse() {
        String user = user("abandoned-snapshot");
        createIngress(user, "100.00");
        long boxId = createBox(user, "Abandoned snapshot");
        deposit(user, boxId, "40.00", LocalDate.now());
        long planId = createGoal(user, boxId, "100.00", LocalDate.now().plusDays(2),
            "DAILY", null, null, "20.00")
            .extract().jsonPath().getLong("id");

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/abandon",
                boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"))
            .body("boxBalance", equalTo(40.0f))
            .body("completionAmount", equalTo(40.0f));

        withdraw(user, boxId, "40.00", LocalDate.now());
        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}", boxId, planId)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"))
            .body("boxBalance", equalTo(40.0f))
            .body("completionAmount", equalTo(40.0f));
    }

    @Test
    void onlyOnePlanAndCallerScopedActiveBoxAccessAreEnforced() {
        String alice = user("alice");
        String bob = user("bob");
        long aliceBox = createBox(alice, "Alice goal");
        long planId = createGoal(alice, aliceBox, "100.00",
            LocalDate.now().plusDays(1), "DAILY", null, null, null)
            .extract().jsonPath().getLong("id");

        createGoal(alice, aliceBox, "200.00", LocalDate.now().plusDays(2),
            "DAILY", null, null, null).statusCode(409);
        given().header("X-WorkOS-User-Id", bob)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                aliceBox, planId)
            .then().statusCode(404);
        createGoal(bob, aliceBox, "100.00", LocalDate.now().plusDays(1),
            "DAILY", null, null, null).statusCode(404);

        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/abandon",
                aliceBox, planId)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"));
        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{id}/archive", aliceBox)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", alice)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                aliceBox, planId)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"));
        given().header("X-WorkOS-User-Id", alice)
            .when().get("/api/boxes/{boxId}/plans", aliceBox)
            .then().statusCode(200)
            .body("[0].status", equalTo("ABANDONED"));
        createGoal(alice, aliceBox, "200.00", LocalDate.now().plusDays(2),
            "DAILY", null, null, null).statusCode(404);

        long activePlanBox = createBox(alice, "Active goal blocks archive");
        long activePlan = createGoal(alice, activePlanBox, "100.00",
            LocalDate.now().plusDays(1), "DAILY", null, null, null)
            .extract().jsonPath().getLong("id");
        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{id}/archive", activePlanBox)
            .then().statusCode(409);
        given().header("X-WorkOS-User-Id", alice)
            .when().get("/api/boxes/{boxId}/plans/saving-goal/{planId}",
                activePlanBox, activePlan)
            .then().statusCode(200)
            .body("status", equalTo("ACTIVE"));
        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/abandon",
                activePlanBox, activePlan)
            .then().statusCode(200)
            .body("status", equalTo("ABANDONED"));
        given().header("X-WorkOS-User-Id", alice)
            .when().post("/api/boxes/{id}/archive", activePlanBox)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", alice)
            .when().get("/api/boxes/{boxId}/plans", activePlanBox)
            .then().statusCode(200)
            .body("[0].status", equalTo("ABANDONED"));
    }

    private void shiftGoalTimeline(long planId, LocalDate start, LocalDate targetDate) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE box_plan SET start_date = :start WHERE id = :id")
                .setParameter("start", start)
                .setParameter("id", planId)
                .executeUpdate();
            em.createNativeQuery("""
                    UPDATE box_plan_revision
                    SET effective_from = :start
                    WHERE id = (
                        SELECT id
                        FROM box_plan_revision
                        WHERE plan_id = :id
                        ORDER BY effective_from, created_at, id
                        LIMIT 1
                    )
                    """)
                .setParameter("start", start)
                .setParameter("id", planId)
                .executeUpdate();
            em.createNativeQuery("""
                    UPDATE saving_goal_revision goal
                    SET target_date = :target
                    WHERE goal.revision_id = (
                        SELECT id
                        FROM box_plan_revision
                        WHERE plan_id = :id
                        ORDER BY effective_from, created_at, id
                        LIMIT 1
                    )
                    """)
                .setParameter("target", targetDate)
                .setParameter("id", planId)
                .executeUpdate();
        });
    }

    private static String user(String suffix) {
        return "test-saving-goal-" + suffix + "-" + UUID.randomUUID();
    }

    private static int planListStatus(
            String user, long boxId, CountDownLatch start) throws InterruptedException {
        start.await();
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/plans", boxId)
            .statusCode();
    }

    private static long createBox(String user, String name) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "hue", 140))
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static io.restassured.response.ValidatableResponse createGoal(
            String user, long boxId, String targetAmount, LocalDate targetDate,
            String cadence, Integer weekday, Integer monthDay, String commitment) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(goalBody(targetAmount, targetDate, cadence,
                weekday, monthDay, commitment))
            .when().post("/api/boxes/{boxId}/plans/saving-goal", boxId)
            .then();
    }

    private static io.restassured.response.ValidatableResponse createGoalRevision(
            String user, long boxId, long planId, String targetAmount,
            LocalDate targetDate) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(goalBody(targetAmount, targetDate, "DAILY",
                null, null, null))
            .when().post("/api/boxes/{boxId}/plans/saving-goal/{planId}/revisions",
                boxId, planId)
            .then().statusCode(200);
    }

    private static Map<String, Object> goalBody(
            String targetAmount, LocalDate targetDate, String cadence,
            Integer weekday, Integer monthDay, String commitment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetAmount", targetAmount);
        body.put("targetDate", targetDate.toString());
        body.put("cadence", cadence);
        if (weekday != null) body.put("anchorWeekday", weekday);
        if (monthDay != null) body.put("anchorDayOfMonth", monthDay);
        if (commitment != null) body.put("regularCommitment", commitment);
        return body;
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
                "description", "Goal funding",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }

    private static void deposit(String user, long boxId, String amount, LocalDate date) {
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", date.toString()))
            .when().post("/api/boxes/{id}/deposit", boxId)
            .then().statusCode(200);
    }

    private static void withdraw(String user, long boxId, String amount, LocalDate date) {
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", amount, "effectiveDate", date.toString()))
            .when().post("/api/boxes/{id}/withdraw", boxId)
            .then().statusCode(200);
    }
}
