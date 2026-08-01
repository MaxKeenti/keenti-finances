package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FundingTriggerResourceTest {

    @Test
    void crudSupportsFixedPercentageAndExplicitEnableDisable() {
        String user = uniqueUser("crud");
        long boxId = createBox(user, "Rules");
        long salaryId = createCategory(user, "Salary", "INGRESS");
        long bonusId = createCategory(user, "Bonus", "BOTH");

        Response created = createTrigger(user, boxId, """
            {
              "categoryId": %d,
              "strategy": "FIXED_AMOUNT",
              "fixedAmount": 500.00
            }
            """.formatted(salaryId));
        created.then().statusCode(201);
        long triggerId = created.jsonPath().getLong("id");
        assertEquals("FIXED_AMOUNT", created.jsonPath().getString("strategy"));
        assertTrue(created.jsonPath().getBoolean("enabled"));
        assertMoney("500.00", created.jsonPath().get("fixedAmount"));

        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/funding-triggers", boxId)
            .then().statusCode(200)
            .body("size()", org.hamcrest.Matchers.equalTo(1));

        Response updated = given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("""
                {
                  "categoryId": %d,
                  "strategy": "PERCENTAGE",
                  "percentage": 12.5
                }
                """.formatted(bonusId))
            .when().put("/api/boxes/{boxId}/funding-triggers/{triggerId}", boxId, triggerId);
        updated.then().statusCode(200);
        assertEquals("PERCENTAGE", updated.jsonPath().getString("strategy"));
        assertEquals(bonusId, updated.jsonPath().getLong("categoryId"));
        assertMoney("12.5000", updated.jsonPath().get("percentage"));
        assertTrue(updated.jsonPath().getBoolean("enabled"));

        setEnabled(user, boxId, triggerId, false).then().statusCode(200);
        suggestion(user, bonusId, "10.05")
            .then().statusCode(200)
            .body("suggestions.size()", org.hamcrest.Matchers.equalTo(0));
        setEnabled(user, boxId, triggerId, true).then().statusCode(200);
        Response suggestions = suggestion(user, bonusId, "10.05");
        suggestions.then().statusCode(200);
        assertMoney("1.26", suggestions.jsonPath().get("combinedTotal"));

        given()
            .header("X-WorkOS-User-Id", user)
            .when().delete("/api/boxes/{boxId}/funding-triggers/{triggerId}", boxId, triggerId)
            .then().statusCode(204);
        suggestion(user, bonusId, "10.05")
            .then().statusCode(200)
            .body("suggestions.size()", org.hamcrest.Matchers.equalTo(0));
    }

    @Test
    void suggestionsCombineMatchingRulesAndNeverCreateMovements() {
        String user = uniqueUser("suggestions");
        long salaryId = createCategory(user, "Salary", "INGRESS");
        long otherIncomeId = createCategory(user, "Other income", "INGRESS");
        long groceriesId = createBox(user, "Groceries");
        long emergencyId = createBox(user, "Emergency");
        long ignoredId = createBox(user, "Ignored");

        createTrigger(user, groceriesId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":2.00}
            """.formatted(salaryId)).then().statusCode(201);
        createTrigger(user, emergencyId, """
            {"categoryId":%d,"strategy":"PERCENTAGE","percentage":12.5}
            """.formatted(salaryId)).then().statusCode(201);
        createTrigger(user, ignoredId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":99.00}
            """.formatted(otherIncomeId)).then().statusCode(201);

        Response response = suggestion(user, salaryId, "10.05");
        response.then().statusCode(200);
        assertEquals(2, response.jsonPath().getList("suggestions").size());
        assertMoney("2.00", response.jsonPath().get("suggestions[0].suggestedAmount"));
        assertMoney("1.26", response.jsonPath().get("suggestions[1].suggestedAmount"));
        assertMoney("3.26", response.jsonPath().get("combinedTotal"));

        Response transactionAlias = given()
            .header("X-WorkOS-User-Id", user)
            .queryParam("categoryId", salaryId)
            .queryParam("ingressAmount", "10.05")
            .when().get("/api/transactions/funding-suggestions");
        transactionAlias.then().statusCode(200);
        assertMoney("3.26", transactionAlias.jsonPath().get("combinedTotal"));

        assertHistoryEmpty(user, groceriesId);
        assertHistoryEmpty(user, emergencyId);
        assertHistoryEmpty(user, ignoredId);
    }

    @Test
    void archivedDisabledDeletedAndDifferentCategoryRulesNeverSuggest() {
        String user = uniqueUser("excluded");
        long salaryId = createCategory(user, "Salary", "INGRESS");
        long boxId = createBox(user, "Temporary");
        long triggerId = createTrigger(user, boxId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":25.00}
            """.formatted(salaryId))
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .header("X-WorkOS-User-Id", user)
            .when().post("/api/boxes/{id}/archive", boxId)
            .then().statusCode(200);
        suggestion(user, salaryId, "100.00")
            .then().statusCode(200)
            .body("suggestions.size()", org.hamcrest.Matchers.equalTo(0));

        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/funding-triggers/{triggerId}", boxId, triggerId)
            .then().statusCode(404);
    }

    @Test
    void invalidStrategyCategoryValuesDuplicatesAndCrossUserLinksAreRejected() {
        String alice = uniqueUser("alice");
        String bob = uniqueUser("bob");
        long aliceBoxId = createBox(alice, "Alice Box");
        long aliceCategoryId = createCategory(alice, "Alice Salary", "INGRESS");
        long bobBoxId = createBox(bob, "Bob Box");
        long bobIngressId = createCategory(bob, "Bob Salary", "INGRESS");
        long bobEgressId = createCategory(bob, "Bob Spending", "EGRESS");

        createTrigger(bob, bobBoxId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":10.00}
            """.formatted(aliceCategoryId)).then().statusCode(404);
        createTrigger(bob, aliceBoxId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":10.00}
            """.formatted(bobIngressId)).then().statusCode(404);
        createTrigger(bob, bobBoxId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":10.00}
            """.formatted(bobEgressId)).then().statusCode(400);
        createTrigger(bob, bobBoxId, """
            {"categoryId":%d,"strategy":"PERCENTAGE","percentage":100.0001}
            """.formatted(bobIngressId)).then().statusCode(400);
        createTrigger(bob, bobBoxId, """
            {"categoryId":%d,"strategy":"PLAN_DERIVED"}
            """.formatted(bobIngressId)).then().statusCode(400);

        createTrigger(alice, aliceBoxId, """
            {"categoryId":%d,"strategy":"FIXED_AMOUNT","fixedAmount":10.00}
            """.formatted(aliceCategoryId)).then().statusCode(201);
        createTrigger(alice, aliceBoxId, """
            {"categoryId":%d,"strategy":"PERCENTAGE","percentage":10.0}
            """.formatted(aliceCategoryId)).then().statusCode(409);

        suggestion(bob, bobEgressId, "100.00").then().statusCode(400);
        suggestion(bob, aliceCategoryId, "100.00").then().statusCode(404);
    }

    private static String uniqueUser(String suffix) {
        return "test-funding-trigger-" + suffix + "-" + System.nanoTime();
    }

    private static long createBox(String user, String label) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + label + " " + System.nanoTime()
                + "\",\"hue\":140}")
            .when().post("/api/boxes")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static long createCategory(String user, String label, String type) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + label + " " + System.nanoTime()
                + "\",\"type\":\"" + type + "\",\"hue\":80}")
            .when().post("/api/categories")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    private static Response createTrigger(String user, long boxId, String body) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/boxes/{boxId}/funding-triggers", boxId);
    }

    private static Response setEnabled(
            String user, long boxId, long triggerId, boolean enabled) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .contentType(ContentType.JSON)
            .body("{\"enabled\":" + enabled + "}")
            .when().put(
                "/api/boxes/{boxId}/funding-triggers/{triggerId}/enabled",
                boxId, triggerId);
    }

    private static Response suggestion(String user, long categoryId, String amount) {
        return given()
            .header("X-WorkOS-User-Id", user)
            .queryParam("categoryId", categoryId)
            .queryParam("ingressAmount", amount)
            .when().get("/api/funding-triggers/suggestions");
    }

    private static void assertHistoryEmpty(String user, long boxId) {
        given()
            .header("X-WorkOS-User-Id", user)
            .when().get("/api/boxes/{boxId}/history", boxId)
            .then().statusCode(200)
            .body("size()", org.hamcrest.Matchers.equalTo(0));
    }

    private static void assertMoney(String expected, Object actual) {
        assertNotNull(actual);
        BigDecimal value = new BigDecimal(actual.toString());
        assertTrue(new BigDecimal(expected).compareTo(value) == 0,
            () -> "expected " + expected + " but was " + actual);
    }
}
