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
    void concurrentGenerationCreatesOnePersonalPaymentRecord() throws Exception {
        String userId = "test-billing-concurrent-" + System.nanoTime();
        long subscriptionId = createPersonalSubscriptionDueToday(userId);
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

            int generated = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, generated, "only one concurrent request creates the period");

            given()
                    .header("X-WorkOS-User-Id", userId)
                    .when().get("/api/subscriptions/{id}/payments", subscriptionId)
                    .then()
                    .statusCode(200)
                    .body("size()", org.hamcrest.Matchers.is(1));
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

    private static long createPersonalSubscriptionDueToday(String userId) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Concurrent billing",
                          "cost": 100.00,
                          "billingCycle": "MONTHLY",
                          "type": "PERSONAL",
                          "nextBillingDate": "%s",
                          "ownerParticipates": true
                        }
                        """.formatted(LocalDate.now()))
                .when().post("/api/subscriptions")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }
}
