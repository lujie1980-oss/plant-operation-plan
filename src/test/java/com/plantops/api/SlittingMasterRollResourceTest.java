package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class SlittingMasterRollResourceTest {

    @Test
    void createAndListMasterRoll() {
        String body = """
                {
                  "rollCode": "MR-TEST-01",
                  "widthMm": 1200,
                  "lengthMm": 5000,
                  "kerfLongitudinalMm": 2,
                  "kerfTransverseMm": 2,
                  "status": "AVAILABLE"
                }
                """;
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/slitting/master-rolls")
                .then()
                .statusCode(200)
                .body("rollCode", equalTo("MR-TEST-01"));

        given()
                .when()
                .get("/api/v1/slitting/master-rolls")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void demoSeedDataIsPresent() {
        given()
                .when()
                .get("/api/v1/slitting/master-rolls")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2));

        given()
                .when()
                .get("/api/v1/slitting/child-orders")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(8));
    }
}
