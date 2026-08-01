package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class BoxResourceTest {

    @Inject
    EntityManager em;

    @Test
    void movementDatesUseTheUsersLocalToday() {
        String user = user("local-date");
        createIngress(user, "100.00");
        long boxId = createBox(user, "Local date", 105);
        Instant now = Instant.now();
        LocalDate systemDate = LocalDate.ofInstant(now, ZoneId.systemDefault());
        ZoneId userZone = ZoneId.getAvailableZoneIds().stream()
            .map(ZoneId::of)
            .filter(zone -> !LocalDate.ofInstant(now, zone).equals(systemDate))
            .findFirst()
            .orElseThrow();
        LocalDate userDate = LocalDate.ofInstant(now, userZone);
        setTimeZone(user, userZone);

        if (systemDate.isAfter(userDate)) {
            depositRequest(user, boxId, amount("10.00", systemDate), 400);
        }
        depositRequest(user, boxId, amount("10.00", userDate), 200);
        assertBalance(user, boxId, 10);
    }

    @Test
    void crudReorderAndDuplicateNamesFollowTheBoxLifecycleContract() {
        String user = user("crud");
        long first = createBox(user, "Vacation", 145);
        long second = createBox(user, "Emergency", 25);

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes")
            .then().statusCode(200)
            .body("id", hasItems((int) first, (int) second));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {"name":"  Travel  ","hue":200,"icon":"✈️","description":"  Main trip  "}
                """)
            .when().put("/api/boxes/{id}", first)
            .then().statusCode(200)
            .body("name", equalTo("Travel"))
            .body("description", equalTo("Main trip"))
            .body("version", notNullValue());

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("boxIds", List.of(second, first)))
            .when().put("/api/boxes/reorder")
            .then().statusCode(200)
            .body("[0].id", equalTo((int) second))
            .body("[0].displayOrder", equalTo(0))
            .body("[1].id", equalTo((int) first))
            .body("[1].displayOrder", equalTo(1));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"travel\",\"hue\":10}")
            .when().post("/api/boxes")
            .then().statusCode(409);
    }

    @Test
    void depositsWithdrawalsAndSummaryReconcileWithoutTransactionsForMovements() {
        String user = user("money");
        createIngress(user, "1000.00");
        long boxId = createBox(user, "House", 80);
        LocalDate depositDate = LocalDate.now().minusDays(10);

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("300.00", depositDate))
            .when().post("/api/boxes/{id}/deposit", boxId)
            .then().statusCode(200)
            .body("box.balance", equalTo(300.0f))
            .body("summary.netBalance", equalTo(1000.0f))
            .body("summary.inBoxes", equalTo(300.0f))
            .body("summary.availableToSpend", equalTo(700.0f));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("75.00", LocalDate.now()))
            .when().post("/api/boxes/{id}/withdraw", boxId)
            .then().statusCode(200)
            .body("box.balance", equalTo(225.0f))
            .body("summary.netBalance", equalTo(1000.0f))
            .body("summary.availableToSpend", equalTo(775.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .body("[0].type", equalTo("WITHDRAWAL"))
            .body("[0].runningBalance", equalTo(225.0f))
            .body("[1].type", equalTo("DEPOSIT"))
            .body("[1].runningBalance", equalTo(300.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/dashboard/summary")
            .then().statusCode(200)
            .body("netBalance", equalTo(1000.0f))
            .body("inBoxes", equalTo(225.0f))
            .body("availableToSpend", equalTo(775.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/transactions")
            .then().statusCode(200)
            .body("size()", equalTo(1));
    }

    @Test
    void directTransferIsAtomicAndAppearsInBothHistories() {
        String user = user("transfer");
        createIngress(user, "500.00");
        long source = createBox(user, "Source", 10);
        long target = createBox(user, "Target", 20);
        deposit(user, source, "200.00", LocalDate.now().minusDays(5), 200);

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "targetBoxId", target,
                "amount", "80.00",
                "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/transfer", source)
            .then().statusCode(200)
            .body("sourceBox.balance", equalTo(120.0f))
            .body("targetBox.balance", equalTo(80.0f))
            .body("summary.inBoxes", equalTo(200.0f));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", source)
            .then().statusCode(200)
            .body("[0].type", equalTo("TRANSFER_OUT"))
            .body("[0].relatedBoxId", equalTo((int) target));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", target)
            .then().statusCode(200)
            .body("[0].type", equalTo("TRANSFER_IN"))
            .body("[0].relatedBoxId", equalTo((int) source));

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "targetBoxId", target,
                "amount", "500.00",
                "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/transfer", source)
            .then().statusCode(400);

        assertBalance(user, source, 120);
        assertBalance(user, target, 80);
    }

    @Test
    void backdatedDebitCannotInvalidateAnIntermediateRunningBalance() {
        String user = user("backdate");
        createIngress(user, "500.00");
        long boxId = createBox(user, "Timeline", 100);
        deposit(user, boxId, "100.00", LocalDate.now().minusDays(20), 100);
        withdraw(user, boxId, "80.00", LocalDate.now().minusDays(10), 20);
        deposit(user, boxId, "100.00", LocalDate.now(), 120);

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("50.00", LocalDate.now().minusDays(15)))
            .when().post("/api/boxes/{id}/withdraw", boxId)
            .then().statusCode(400);

        assertBalance(user, boxId, 120);
    }

    @Test
    void movementCorrectionRecalculatesBothTimelinesAndRejectsLaterNegatives() {
        String user = user("correction");
        createIngress(user, "500.00");
        long source = createBox(user, "Correction source", 101);
        long target = createBox(user, "Correction target", 102);
        LocalDate depositDate = LocalDate.now().minusDays(20);
        LocalDate transferDate = LocalDate.now().minusDays(10);
        deposit(user, source, "200.00", depositDate, 200);

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "targetBoxId", target,
                "amount", "100.00",
                "effectiveDate", transferDate.toString()))
            .when().post("/api/boxes/{id}/transfer", source)
            .then().statusCode(200);
        withdraw(user, target, "80.00", LocalDate.now().minusDays(5), 20);

        long transferMovementId = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", source)
            .then().statusCode(200)
            .extract().jsonPath()
            .getLong("find { it.type == 'TRANSFER_OUT' }.id");

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("50.00", transferDate))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                source, transferMovementId)
            .then().statusCode(400);
        assertBalance(user, source, 100);
        assertBalance(user, target, 20);

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("120.00", transferDate))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                source, transferMovementId)
            .then().statusCode(200)
            .body("box.balance", equalTo(80.0f))
            .body("summary.inBoxes", equalTo(120.0f));
        assertBalance(user, target, 40);

        long depositMovementId = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", source)
            .then().statusCode(200)
            .extract().jsonPath()
            .getLong("find { it.type == 'DEPOSIT' }.id");
        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount("100.00", depositDate))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                source, depositMovementId)
            .then().statusCode(400);
        assertBalance(user, source, 80);
    }

    @Test
    void concurrentCorrectionsAcrossBoxesSerializeAvailableToSpendIncreases()
            throws Exception {
        String user = user("correction-concurrency");
        createIngress(user, "100.00");
        long firstBox = createBox(user, "Concurrent first", 103);
        long secondBox = createBox(user, "Concurrent second", 104);
        LocalDate today = LocalDate.now();
        deposit(user, firstBox, "10.00", today, 10);
        deposit(user, secondBox, "10.00", today, 10);
        long firstMovement = movementId(user, firstBox, "DEPOSIT");
        long secondMovement = movementId(user, secondBox, "DEPOSIT");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return correctionStatus(
                    user, firstBox, firstMovement, "60.00", today);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return correctionStatus(
                    user, secondBox, secondMovement, "60.00", today);
            });
            start.countDown();

            assertEquals(List.of(200, 400),
                List.of(first.get(), second.get()).stream().sorted().toList());
        } finally {
            executor.shutdownNow();
        }

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/summary")
            .then().statusCode(200)
            .body("netBalance", equalTo(100.0f))
            .body("inBoxes", equalTo(70.0f))
            .body("availableToSpend", equalTo(30.0f));
    }

    @Test
    void invalidAmountsDatesAndInsufficientAvailableBalanceAreRejected() {
        String user = user("validation");
        createIngress(user, "100.00");
        long boxId = createBox(user, "Validation", 250);

        depositRequest(user, boxId, amount("100.01", LocalDate.now()), 400);
        depositRequest(user, boxId, amount("0", LocalDate.now()), 400);
        depositRequest(user, boxId, amount("1.001", LocalDate.now()), 400);
        depositRequest(user, boxId, amount("1.00", LocalDate.now().plusDays(1)), 400);
        assertBalance(user, boxId, 0);
    }

    @Test
    void archiveRequiresZeroAndArchivedNamesAreReusableUntilRestore() {
        String user = user("archive");
        createIngress(user, "100.00");
        long original = createBox(user, "Trip", 140);
        deposit(user, original, "25.00", LocalDate.now(), 25);

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", original)
            .then().statusCode(400);

        withdraw(user, original, "25.00", LocalDate.now(), 0);
        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", original)
            .then().statusCode(200)
            .body("archived", equalTo(true));

        given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", original)
            .then().statusCode(200)
            .body("size()", equalTo(2));

        depositRequest(user, original, amount("1.00", LocalDate.now()), 404);
        long replacement = createBox(user, "Trip", 141);

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/restore", original)
            .then().statusCode(409);

        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", replacement)
            .then().statusCode(200);
        given().header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/restore", original)
            .then().statusCode(200)
            .body("archived", equalTo(false));
    }

    @Test
    void boxesMovementsAndTransferTargetsAreCallerScoped() {
        String alice = user("alice");
        String bob = user("bob");
        createIngress(alice, "100.00");
        createIngress(bob, "100.00");
        long aliceBox = createBox(alice, "Alice only", 1);
        long bobBox = createBox(bob, "Bob only", 2);
        deposit(alice, aliceBox, "50.00", LocalDate.now(), 50);

        given().header("X-WorkOS-User-Id", bob)
            .when().get("/api/boxes/{id}", aliceBox)
            .then().statusCode(404);
        given().header("X-WorkOS-User-Id", bob)
            .when().get("/api/boxes/{id}/history", aliceBox)
            .then().statusCode(404);
        depositRequest(bob, aliceBox, amount("1.00", LocalDate.now()), 404);

        long aliceMovement = given().header("X-WorkOS-User-Id", alice)
            .when().get("/api/boxes/{id}/history", aliceBox)
            .then().statusCode(200)
            .extract().jsonPath().getLong("[0].id");
        given().header("X-WorkOS-User-Id", bob)
            .contentType(ContentType.JSON)
            .body(amount("25.00", LocalDate.now()))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                bobBox, aliceMovement)
            .then().statusCode(404);

        given().header("X-WorkOS-User-Id", bob)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "targetBoxId", aliceBox,
                "amount", "1.00",
                "effectiveDate", LocalDate.now().toString()))
            .when().post("/api/boxes/{id}/transfer", bobBox)
            .then().statusCode(404);

        assertBalance(alice, aliceBox, 50);
        assertBalance(bob, bobBox, 0);
    }

    private static String user(String prefix) {
        return "test-box-" + prefix + "-" + UUID.randomUUID();
    }

    private void setTimeZone(String user, ZoneId timeZone) {
        QuarkusTransaction.requiringNew().run(() ->
            em.createNativeQuery("""
                    UPDATE app_user
                    SET time_zone = :timeZone
                    WHERE workos_id = :workosId
                    """)
                .setParameter("timeZone", timeZone.getId())
                .setParameter("workosId", user)
                .executeUpdate());
    }

    private static long movementId(String user, long boxId, String type) {
        return given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}/history", boxId)
            .then().statusCode(200)
            .extract().jsonPath().getLong(
                "find { it.type == '" + type + "' }.id");
    }

    private static int correctionStatus(
            String user, long boxId, long movementId, String amount,
            LocalDate effectiveDate) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount(amount, effectiveDate))
            .when().put("/api/boxes/{boxId}/movements/{movementId}",
                boxId, movementId)
            .statusCode();
    }

    private static long createBox(String user, String name, int hue) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of("name", name, "hue", hue))
            .when().post("/api/boxes")
            .then().statusCode(201)
            .body("id", notNullValue())
            .body("balance", equalTo(0))
            .extract().jsonPath().getLong("id");
    }

    private static void createIngress(String user, String amount) {
        long categoryId = given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "name", "Income " + UUID.randomUUID(),
                "type", "INGRESS",
                "hue", 120))
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");

        given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "amount", amount,
                "direction", "INGRESS",
                "description", "Test income",
                "transactionDate", LocalDate.now().toString(),
                "categoryId", categoryId))
            .when().post("/api/transactions")
            .then().statusCode(201);
    }

    private static Map<String, Object> amount(String amount, LocalDate effectiveDate) {
        return Map.of("amount", amount, "effectiveDate", effectiveDate.toString());
    }

    private static void deposit(String user, long boxId, String amount,
                                LocalDate effectiveDate, float expectedBalance) {
        float actual = depositRequest(user, boxId, amount(amount, effectiveDate), 200)
            .extract().jsonPath().getFloat("box.balance");
        assertEquals(expectedBalance, actual, 0.001f);
    }

    private static void withdraw(String user, long boxId, String amount,
                                 LocalDate effectiveDate, float expectedBalance) {
        float actual = given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(amount(amount, effectiveDate))
            .when().post("/api/boxes/{id}/withdraw", boxId)
            .then().statusCode(200)
            .extract().jsonPath().getFloat("box.balance");
        assertEquals(expectedBalance, actual, 0.001f);
    }

    private static io.restassured.response.ValidatableResponse depositRequest(
            String user, long boxId, Object body, int status) {
        return given().header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/boxes/{id}/deposit", boxId)
            .then().statusCode(status);
    }

    private static void assertBalance(String user, long boxId, float balance) {
        float actual = given().header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{id}", boxId)
            .then().statusCode(200)
            .extract().jsonPath().getFloat("balance");
        assertEquals(balance, actual, 0.001f);
    }
}
