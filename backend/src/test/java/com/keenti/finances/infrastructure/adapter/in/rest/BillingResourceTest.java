package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BillingResourceTest {

    @Test
    void concurrentGenerationCreatesOnePersonalPaymentRecordAndAdvancesOnce() throws Exception {
        String userId = "test-billing-concurrent-" + System.nanoTime();
        long subscriptionId = createPersonalSubscriptionDueToday(userId);
        LocalDate expectedNextBillingDate = LocalDate.now().plusMonths(1);

        int generated = generateConcurrently(userId, subscriptionId);

        assertEquals(1, generated, "only one concurrent request creates the period");
        assertPaymentRecordCount(userId, subscriptionId, 1);
        assertNextBillingDate(userId, subscriptionId, expectedNextBillingDate);
    }

    @Test
    void concurrentGenerationCreatesOnePaymentRecordPerSubscriptionMemberAndAdvancesOnce()
            throws Exception {
        String userId = "test-billing-concurrent-shared-" + System.nanoTime();
        long subscriptionId = createSharedSubscriptionDueToday(userId);
        addSubscriptionMember(userId, subscriptionId, createContact(userId, "Alex"));
        addSubscriptionMember(userId, subscriptionId, createContact(userId, "Sam"));
        LocalDate expectedNextBillingDate = LocalDate.now().plusMonths(1);

        int generated = generateConcurrently(userId, subscriptionId);

        assertEquals(2, generated,
                "only one concurrent request creates one record per Subscription Member");
        assertPaymentRecordCount(userId, subscriptionId, 2);
        assertNextBillingDate(userId, subscriptionId, expectedNextBillingDate);
    }

    private static int generateConcurrently(String userId, long subscriptionId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() ->
                generateWhenReleased(userId, subscriptionId, ready, start));
            Future<Integer> second = executor.submit(() ->
                generateWhenReleased(userId, subscriptionId, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "both requests reached the start barrier");
            start.countDown();

            return first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static int generateWhenReleased(String userId, long subscriptionId,
                                            CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .when().post("/api/subscriptions/{id}/generate-billing", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath().getInt("generated");
    }

    private static void assertPaymentRecordCount(String userId, long subscriptionId, int expected) {
        given()
                .header("X-WorkOS-User-Id", userId)
                .when().get("/api/subscriptions/{id}/payments", subscriptionId)
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.is(expected));
    }

    private static void assertNextBillingDate(
            String userId, long subscriptionId, LocalDate expectedNextBillingDate) {
        given()
                .header("X-WorkOS-User-Id", userId)
                .when().get("/api/subscriptions/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .body("nextBillingDate",
                        org.hamcrest.Matchers.is(expectedNextBillingDate.toString()));
    }

    private static long createPersonalSubscriptionDueToday(String userId) {
        return createSubscriptionDueToday(userId, "PERSONAL", true);
    }

    private static long createSharedSubscriptionDueToday(String userId) {
        return createSubscriptionDueToday(userId, "SHARED", false);
    }

    private static long createSubscriptionDueToday(
            String userId, String type, boolean ownerParticipates) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Concurrent %s billing",
                          "cost": 100.00,
                          "billingCycle": "MONTHLY",
                          "type": "%s",
                          "nextBillingDate": "%s",
                          "ownerParticipates": %s
                        }
                        """.formatted(type.toLowerCase(), type, LocalDate.now(), ownerParticipates))
                .when().post("/api/subscriptions")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static long createContact(String userId, String name) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "%s"
                        }
                        """.formatted(name))
                .when().post("/api/contacts")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static void addSubscriptionMember(
            String userId, long subscriptionId, long contactId) {
        given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "contactId": %d
                        }
                        """.formatted(contactId))
                .when().post("/api/subscriptions/{id}/members", subscriptionId)
                .then()
                .statusCode(201);
    }
}
