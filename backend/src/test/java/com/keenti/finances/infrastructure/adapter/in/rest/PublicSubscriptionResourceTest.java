package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PublicSubscriptionResourceTest {

    private final List<String> logMessages = new CopyOnWriteArrayList<>();
    private final Handler logHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logMessages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void capturePublicSubscriptionLogs() {
        java.util.logging.Logger.getLogger(PublicSubscriptionResource.class.getName())
            .addHandler(logHandler);
    }

    @AfterEach
    void stopCapturingPublicSubscriptionLogs() {
        java.util.logging.Logger.getLogger(PublicSubscriptionResource.class.getName())
            .removeHandler(logHandler);
    }

    @Test
    void validTokenReturnsSubscriptionMembersContactsAndPaymentRecords() {
        String userId = "test-public-subscription-" + System.nanoTime();
        String contactName = "Public Subscription Member " + System.nanoTime();
        long contactId = createContact(userId, contactName);
        long subscriptionId = createSharedSubscription(userId);
        long memberId = addMember(userId, subscriptionId, contactId);
        String token = getToken(userId, subscriptionId);
        logMessages.clear();

        given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .when().post("/api/subscriptions/{id}/generate-billing", subscriptionId)
                .then()
                .statusCode(200)
                .body("generated", is(1));

        given()
                .when().get("/api/public/subscriptions/{token}", token)
                .then()
                .statusCode(200)
                .body("subscriptionName", is("Public Subscription"))
                .body("members.size()", is(1))
                .body("members[0].memberId", is((int) memberId))
                .body("members[0].contactId", is((int) contactId))
                .body("members[0].contactName", is(contactName))
                .body("members[0].payments.size()", is(1))
                .body("members[0].payments[0].status", is("PENDING"));

        assertFalse(logMessages.stream().anyMatch(message -> message.contains(token)));
        assertTrue(logMessages.stream().anyMatch(message -> message.equals(
            "public.subscription.lookup result=found status=200 subscriptionId=" + subscriptionId)));
    }

    @Test
    void invalidTokenReturnsNotFound() {
        String token = UUID.randomUUID().toString();

        given()
                .when().get("/api/public/subscriptions/{token}", token)
                .then()
                .statusCode(404)
                .body("error", is("Subscription not found"));

        assertFalse(logMessages.stream().anyMatch(message -> message.contains(token)));
        assertTrue(logMessages.contains(
            "public.subscription.lookup result=not_found status=404"));
    }

    private static long createContact(String userId, String name) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\"}")
                .when().post("/api/contacts")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static long createSharedSubscription(String userId) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Public Subscription",
                          "cost": 100.00,
                          "billingCycle": "MONTHLY",
                          "type": "SHARED",
                          "nextBillingDate": "%s",
                          "ownerParticipates": true
                        }
                        """.formatted(LocalDate.now()))
                .when().post("/api/subscriptions")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static long addMember(String userId, long subscriptionId, long contactId) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("{\"contactId\":" + contactId + "}")
                .when().post("/api/subscriptions/{id}/members", subscriptionId)
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static String getToken(String userId, long subscriptionId) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .when().get("/api/subscriptions/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("tokenUuid");
    }
}
