package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
}
