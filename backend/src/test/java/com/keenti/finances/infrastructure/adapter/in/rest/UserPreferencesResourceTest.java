package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.infrastructure.adapter.out.persistence.UserEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class UserPreferencesResourceTest {

    @Test
    void get_newUser_returnsDefaults() {
        String workosId = "test-prefs-defaults";

        given()
            .header("X-WorkOS-User-Id", workosId)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(UserEntity.DEFAULT_PRIMARY_HUE))
            .body("headingFont", equalTo(UserEntity.DEFAULT_HEADING_FONT))
            .body("bodyFont", equalTo(UserEntity.DEFAULT_BODY_FONT));
    }

    @Test
    void put_validBody_persistsAndReturnsUpdated() {
        String workosId = "test-prefs-put-persists";
        String body = "{\"primaryHue\":220,\"headingFont\":\"Playfair Display\",\"bodyFont\":\"Inter\"}";

        given()
            .header("X-WorkOS-User-Id", workosId)
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(220))
            .body("headingFont", equalTo("Playfair Display"))
            .body("bodyFont", equalTo("Inter"));

        // A subsequent GET sees the persisted values.
        given()
            .header("X-WorkOS-User-Id", workosId)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(220))
            .body("headingFont", equalTo("Playfair Display"))
            .body("bodyFont", equalTo("Inter"));
    }

    @Test
    void put_hueOutOfRange_returns400() {
        String body = "{\"primaryHue\":360,\"headingFont\":\"Fraunces\",\"bodyFont\":\"Geist\"}";

        given()
            .header("X-WorkOS-User-Id", "test-prefs-hue-oor")
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/user/preferences")
            .then()
            .statusCode(400);
    }

    @Test
    void put_disallowedHeadingFont_returns400() {
        String body = "{\"primaryHue\":100,\"headingFont\":\"Comic Sans\",\"bodyFont\":\"Geist\"}";

        given()
            .header("X-WorkOS-User-Id", "test-prefs-bad-heading")
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/user/preferences")
            .then()
            .statusCode(400);
    }

    @Test
    void put_disallowedBodyFont_returns400() {
        String body = "{\"primaryHue\":100,\"headingFont\":\"Fraunces\",\"bodyFont\":\"Papyrus\"}";

        given()
            .header("X-WorkOS-User-Id", "test-prefs-bad-body")
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/user/preferences")
            .then()
            .statusCode(400);
    }

    @Test
    void preferences_scopedToWorkosUser_independentAcrossUsers() {
        String alice = "test-prefs-scope-alice";
        String bob = "test-prefs-scope-bob";

        // Alice picks blue + Playfair.
        given()
            .header("X-WorkOS-User-Id", alice)
            .contentType(ContentType.JSON)
            .body("{\"primaryHue\":220,\"headingFont\":\"Playfair Display\",\"bodyFont\":\"Geist\"}")
            .when().put("/api/user/preferences")
            .then().statusCode(200);

        // Bob picks red + Inter. Should not see Alice's choices.
        given()
            .header("X-WorkOS-User-Id", bob)
            .contentType(ContentType.JSON)
            .body("{\"primaryHue\":10,\"headingFont\":\"Fraunces\",\"bodyFont\":\"Inter\"}")
            .when().put("/api/user/preferences")
            .then().statusCode(200);

        // Alice still has hers.
        given()
            .header("X-WorkOS-User-Id", alice)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(220))
            .body("headingFont", equalTo("Playfair Display"))
            .body("bodyFont", equalTo("Geist"));

        // Bob has his.
        given()
            .header("X-WorkOS-User-Id", bob)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(10))
            .body("headingFont", equalTo("Fraunces"))
            .body("bodyFont", equalTo("Inter"));
    }

    @Test
    void missingHeader_returns401() {
        given()
            .when().get("/api/user/preferences")
            .then()
            .statusCode(401);
    }
}
