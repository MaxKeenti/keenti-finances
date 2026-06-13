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
            .body("bodyFont", equalTo(UserEntity.DEFAULT_BODY_FONT))
            .body("locale", equalTo(UserEntity.DEFAULT_LOCALE))
            .body("transactionPageSize", equalTo(UserEntity.DEFAULT_TRANSACTION_PAGE_SIZE))
            .body("transactionSortBy", equalTo(UserEntity.DEFAULT_TRANSACTION_SORT_BY))
            .body("transactionSortDirection", equalTo(UserEntity.DEFAULT_TRANSACTION_SORT_DIRECTION))
            .body("mobilePinnedNavItems", equalTo(UserEntity.DEFAULT_MOBILE_PINNED_NAV_ITEMS))
            .body("dockMagnification", equalTo(UserEntity.DEFAULT_DOCK_MAGNIFICATION));
    }

    @Test
    void put_validBody_persistsAndReturnsUpdated() {
        String workosId = "test-prefs-put-persists";
        String body = preferencesJson(220, "Playfair Display", "Inter", "en", 50, "amount", "asc",
            "/,/transactions,/settings", false);

        given()
            .header("X-WorkOS-User-Id", workosId)
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(220))
            .body("headingFont", equalTo("Playfair Display"))
            .body("bodyFont", equalTo("Inter"))
            .body("locale", equalTo("en"))
            .body("transactionPageSize", equalTo(50))
            .body("transactionSortBy", equalTo("amount"))
            .body("transactionSortDirection", equalTo("asc"))
            .body("mobilePinnedNavItems", equalTo("/,/transactions,/settings"))
            .body("dockMagnification", equalTo(false));

        // A subsequent GET sees the persisted values.
        given()
            .header("X-WorkOS-User-Id", workosId)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(220))
            .body("headingFont", equalTo("Playfair Display"))
            .body("bodyFont", equalTo("Inter"))
            .body("locale", equalTo("en"))
            .body("transactionPageSize", equalTo(50))
            .body("transactionSortBy", equalTo("amount"))
            .body("transactionSortDirection", equalTo("asc"))
            .body("mobilePinnedNavItems", equalTo("/,/transactions,/settings"))
            .body("dockMagnification", equalTo(false));
    }

    @Test
    void put_hueOutOfRange_returns400() {
        String body = preferencesJson(360, "Fraunces", "Geist");

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
        String body = preferencesJson(100, "Comic Sans", "Geist");

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
        String body = preferencesJson(100, "Fraunces", "Papyrus");

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
            .body(preferencesJson(220, "Playfair Display", "Geist", "en", 10, "description", "asc",
                "/,/transactions,/debts", false))
            .when().put("/api/user/preferences")
            .then().statusCode(200);

        // Bob picks red + Inter. Should not see Alice's choices.
        given()
            .header("X-WorkOS-User-Id", bob)
            .contentType(ContentType.JSON)
            .body(preferencesJson(10, "Fraunces", "Inter", "es", 100, "contactName", "desc",
                "/subscriptions,/debts,/settings", true))
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
            .body("bodyFont", equalTo("Geist"))
            .body("locale", equalTo("en"))
            .body("transactionPageSize", equalTo(10))
            .body("transactionSortBy", equalTo("description"))
            .body("transactionSortDirection", equalTo("asc"))
            .body("mobilePinnedNavItems", equalTo("/,/transactions,/debts"))
            .body("dockMagnification", equalTo(false));

        // Bob has his.
        given()
            .header("X-WorkOS-User-Id", bob)
            .when().get("/api/user/preferences")
            .then()
            .statusCode(200)
            .body("primaryHue", equalTo(10))
            .body("headingFont", equalTo("Fraunces"))
            .body("bodyFont", equalTo("Inter"))
            .body("locale", equalTo("es"))
            .body("transactionPageSize", equalTo(100))
            .body("transactionSortBy", equalTo("contactName"))
            .body("transactionSortDirection", equalTo("desc"))
            .body("mobilePinnedNavItems", equalTo("/subscriptions,/debts,/settings"))
            .body("dockMagnification", equalTo(true));
    }

    @Test
    void missingHeader_returns401() {
        given()
            .when().get("/api/user/preferences")
            .then()
            .statusCode(401);
    }

    private static String preferencesJson(int primaryHue, String headingFont, String bodyFont) {
        return preferencesJson(primaryHue, headingFont, bodyFont, "es", 25, "transactionDate", "desc",
            "/transactions,/subscriptions,/debts", true);
    }

    private static String preferencesJson(
        int primaryHue,
        String headingFont,
        String bodyFont,
        String locale,
        int transactionPageSize,
        String transactionSortBy,
        String transactionSortDirection,
        String mobilePinnedNavItems,
        boolean dockMagnification
    ) {
        return """
            {
              "primaryHue": %d,
              "headingFont": "%s",
              "bodyFont": "%s",
              "locale": "%s",
              "transactionPageSize": %d,
              "transactionSortBy": "%s",
              "transactionSortDirection": "%s",
              "mobilePinnedNavItems": "%s",
              "dockMagnification": %s
            }
            """.formatted(
                primaryHue,
                headingFont,
                bodyFont,
                locale,
                transactionPageSize,
                transactionSortBy,
                transactionSortDirection,
                mobilePinnedNavItems,
                dockMagnification
            );
    }
}
