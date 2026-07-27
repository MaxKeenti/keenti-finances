package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Regression test for the cross-user data leak surfaced in prod on 2026-05-25.
 *
 * <p>
 * Before the fix: {@code UserScopeFilter} enabled the Hibernate
 * {@code userScope} filter inside its own {@code @Transactional} scope, which
 * ended before the resource method's transaction opened — leaving filters
 * unset and every authenticated user able to read every other user's rows.
 * After the fix (see {@link UserScopedInterceptor}), filters activate inside
 * each resource's transaction.
 */
@QuarkusTest
class CrossUserIsolationTest {

    private static final String ALICE = "test-xuser-alice";
    private static final String BOB = "test-xuser-bob";

    @Test
    void categories_areNotVisibleAcrossUsers() {
        // Alice creates a uniquely-named category.
        String aliceCatName = "Alice's Salary " + System.nanoTime();
        given()
                .header("X-WorkOS-User-Id", ALICE)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + aliceCatName + "\",\"type\":\"INGRESS\",\"hue\":100}")
                .when().post("/api/categories")
                .then().statusCode(201);

        // Bob creates a uniquely-named category.
        String bobCatName = "Bob's Rent " + System.nanoTime();
        given()
                .header("X-WorkOS-User-Id", BOB)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + bobCatName + "\",\"type\":\"EGRESS\",\"hue\":10}")
                .when().post("/api/categories")
                .then().statusCode(201);

        // Alice's list contains Alice's category, NOT Bob's.
        given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().get("/api/categories")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("name", everyItem(not(is(bobCatName))))
                .body("name", org.hamcrest.Matchers.hasItem(aliceCatName));

        // Bob's list contains Bob's category, NOT Alice's.
        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().get("/api/categories")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("name", everyItem(not(is(aliceCatName))))
                .body("name", org.hamcrest.Matchers.hasItem(bobCatName));
    }

    @Test
    void contacts_areNotVisibleAcrossUsers() {
        String aliceName = "Alice Contact " + System.nanoTime();
        given()
                .header("X-WorkOS-User-Id", ALICE)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + aliceName + "\"}")
                .when().post("/api/contacts")
                .then().statusCode(201);

        String bobName = "Bob Contact " + System.nanoTime();
        given()
                .header("X-WorkOS-User-Id", BOB)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + bobName + "\"}")
                .when().post("/api/contacts")
                .then().statusCode(201);

        given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().get("/api/contacts")
                .then()
                .statusCode(200)
                .body("name", everyItem(not(is(bobName))))
                .body("name", org.hamcrest.Matchers.hasItem(aliceName));

        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().get("/api/contacts")
                .then()
                .statusCode(200)
                .body("name", everyItem(not(is(aliceName))))
                .body("name", org.hamcrest.Matchers.hasItem(bobName));
    }

    @Test
    void subscriptionMembers_areNotVisibleOrDeletableAcrossUsers() {
        long aliceContactId = createContact(ALICE, "Alice Member " + System.nanoTime());
        long aliceSubscriptionId = createSharedSubscription(ALICE, "Alice Shared " + System.nanoTime());
        long aliceMemberId = addMember(ALICE, aliceSubscriptionId, aliceContactId);
        long bobContactId = createContact(BOB, "Bob Member " + System.nanoTime());
        long bobSubscriptionId = createSharedSubscription(BOB, "Bob Shared " + System.nanoTime());
        long bobMemberId = addMember(BOB, bobSubscriptionId, bobContactId);

        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().get("/api/subscriptions/{id}/members", aliceSubscriptionId)
                .then().statusCode(404);

        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().delete("/api/subscriptions/{id}/members/{memberId}",
                        bobSubscriptionId, aliceMemberId)
                .then().statusCode(404);

        given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().get("/api/subscriptions/{id}/members", aliceSubscriptionId)
                .then()
                .statusCode(200)
                .body("id", hasItem((int) aliceMemberId));

        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().get("/api/subscriptions/{id}/members", bobSubscriptionId)
                .then()
                .statusCode(200)
                .body("id", hasItem((int) bobMemberId));

        given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().delete("/api/subscriptions/{id}/members/{memberId}",
                        aliceSubscriptionId, aliceMemberId)
                .then().statusCode(204);

        given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().get("/api/subscriptions/{id}/members", aliceSubscriptionId)
                .then()
                .statusCode(200)
                .body("id", not(hasItem((int) aliceMemberId)));

        given()
                .header("X-WorkOS-User-Id", BOB)
                .when().get("/api/subscriptions/{id}/members", bobSubscriptionId)
                .then()
                .statusCode(200)
                .body("id", hasItem((int) bobMemberId));
    }

    @Test
    void publicSubscription_membersRemainAccessibleByCapabilityToken() {
        long contactId = createContact(ALICE, "Public Member " + System.nanoTime());
        long subscriptionId = createSharedSubscription(ALICE, "Public Shared " + System.nanoTime());
        addMember(ALICE, subscriptionId, contactId);

        String token = given()
                .header("X-WorkOS-User-Id", ALICE)
                .when().get("/api/subscriptions/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("tokenUuid");

        given()
                .when().get("/api/public/subscriptions/{token}", token)
                .then()
                .statusCode(200)
                .body("members.size()", is(1));
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

    private static long createSharedSubscription(String userId, String name) {
        return given()
                .header("X-WorkOS-User-Id", userId)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "%s",
                          "cost": 100.00,
                          "billingCycle": "MONTHLY",
                          "type": "SHARED",
                          "nextBillingDate": "2026-07-26",
                          "ownerParticipates": true
                        }
                        """.formatted(name))
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
}
