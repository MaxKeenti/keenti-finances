package com.keenti.finances.infrastructure.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CategoryResourceTest {

    private static Long createdId;

    @Test
    @Order(1)
    void createCategory_validBody_returns201WithColorAndId() {
        String body = "{\"name\":\"Test Salary\",\"type\":\"INGRESS\",\"color\":\"#00FF00\"}";

        Integer id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Test Salary"))
                .body("type", equalTo("INGRESS"))
                .body("color", equalTo("#00FF00"))
                .extract().path("id");

        createdId = id.longValue();
    }

    @Test
    @Order(2)
    void listCategories_returns200WithCreatedCategory() {
        given()
                .when().get("/api/categories")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("find { it.name == 'Test Salary' }.color", equalTo("#00FF00"));
    }

    @Test
    @Order(3)
    void getById_existingId_returns200WithCorrectFields() {
        given()
                .when().get("/api/categories/" + createdId)
                .then()
                .statusCode(200)
                .body("id", equalTo(createdId.intValue()))
                .body("name", equalTo("Test Salary"))
                .body("type", equalTo("INGRESS"))
                .body("color", equalTo("#00FF00"));
    }

    @Test
    @Order(4)
    void updateCategory_newColor_returns200WithUpdatedColor() {
        String body = "{\"name\":\"Test Salary\",\"type\":\"INGRESS\",\"color\":\"#0000FF\"}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().put("/api/categories/" + createdId)
                .then()
                .statusCode(200)
                .body("color", equalTo("#0000FF"));
    }

    @Test
    @Order(5)
    void deleteCategory_existingId_returns204() {
        given()
                .when().delete("/api/categories/" + createdId)
                .then()
                .statusCode(204);
    }

    @Test
    @Order(6)
    void getById_afterDelete_returns404() {
        given()
                .when().get("/api/categories/" + createdId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    void createCategory_invalidType_returns400() {
        String body = "{\"name\":\"Bad Category\",\"type\":\"INVALID_TYPE\",\"color\":\"#FF0000\"}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    void createCategory_duplicateName_returns409() {
        String body = "{\"name\":\"Duplicate Name\",\"type\":\"EGRESS\",\"color\":null}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(9)
    void getById_nonexistentId_returns404() {
        given()
                .when().get("/api/categories/999999")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(10)
    void createCategory_emptyName_returns400() {
        String body = "{\"name\":\"\",\"type\":\"INGRESS\",\"color\":null}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(11)
    void createCategory_colorExceedsMaxLength_returns400() {
        String body = "{\"name\":\"Long Color\",\"type\":\"INGRESS\",\"color\":\"#TOOLONGCOLOR\"}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/categories")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(12)
    void updateCategory_nonexistentId_returns404() {
        String body = "{\"name\":\"Ghost\",\"type\":\"EGRESS\",\"color\":null}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().put("/api/categories/999999")
                .then()
                .statusCode(404);
    }
}
