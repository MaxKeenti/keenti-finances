package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class TransactionResourceTest {

    @Test
    void listTransactions_withPaginationAndSorting_returnsRequestedPage() {
        String user = "test-transactions-page-" + System.nanoTime();
        String prefix = "Paged Tx " + System.nanoTime();
        int categoryId = createCategory(user, prefix + " Category");

        createTransaction(user, categoryId, "30.00", prefix + " Medium", "2026-01-02");
        createTransaction(user, categoryId, "10.00", prefix + " Small", "2026-01-01");
        createTransaction(user, categoryId, "50.00", prefix + " Large", "2026-01-03");

        given()
                .header("X-WorkOS-User-Id", user)
                .queryParam("page", 0)
                .queryParam("pageSize", 2)
                .queryParam("sortBy", "amount")
                .queryParam("sortDirection", "asc")
                .when().get("/api/transactions")
                .then()
                .statusCode(200)
                .body("pageIndex", equalTo(0))
                .body("pageSize", equalTo(2))
                .body("totalItems", equalTo(3))
                .body("totalPages", equalTo(2))
                .body("sortBy", equalTo("amount"))
                .body("sortDirection", equalTo("asc"))
                .body("items.description", contains(prefix + " Small", prefix + " Medium"));
    }

    @Test
    void listTransactions_invalidSortBy_returns400() {
        given()
                .header("X-WorkOS-User-Id", "test-transactions-invalid-sort")
                .queryParam("page", 0)
                .queryParam("pageSize", 25)
                .queryParam("sortBy", "amount desc")
                .when().get("/api/transactions")
                .then()
                .statusCode(400);
    }

    private int createCategory(String user, String name) {
        return given()
                .header("X-WorkOS-User-Id", user)
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\",\"type\":\"BOTH\",\"hue\":120}")
                .when().post("/api/categories")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private void createTransaction(String user, int categoryId, String amount, String description, String date) {
        String body = """
                {
                  "amount": %s,
                  "direction": "EGRESS",
                  "description": "%s",
                  "transactionDate": "%s",
                  "categoryId": %d,
                  "contactId": null
                }
                """.formatted(amount, description, date, categoryId);

        given()
                .header("X-WorkOS-User-Id", user)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/transactions")
                .then()
                .statusCode(201);
    }
}
