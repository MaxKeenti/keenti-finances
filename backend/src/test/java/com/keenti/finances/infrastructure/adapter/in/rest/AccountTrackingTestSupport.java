package com.keenti.finances.infrastructure.adapter.in.rest;

import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.restassured.RestAssured.given;

/** Exercises the same Financial Account activation contract used by a new client. */
final class AccountTrackingTestSupport {

    private static final ConcurrentMap<String, Long> CASH_ACCOUNT_IDS = new ConcurrentHashMap<>();

    private AccountTrackingTestSupport() {
    }

    static long cashAccountId(String workosUserId) {
        return CASH_ACCOUNT_IDS.computeIfAbsent(workosUserId, AccountTrackingTestSupport::activateCashAccount);
    }

    private static long activateCashAccount(String workosUserId) {
        return given()
            .header("X-WorkOS-User-Id", workosUserId)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "activationDate", "2000-01-01",
                "accounts", List.of(Map.of(
                    "name", "Test cash account",
                    "kind", "CASH",
					"hue", 220,
                    "openingBalance", "0.00"))))
            .when().post("/api/accounts/activate")
            .then().statusCode(201)
            .extract().jsonPath().getLong("[0].id");
    }
}
