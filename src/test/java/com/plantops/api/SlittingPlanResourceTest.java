package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SlittingPlanResourceTest {

    @Test
    void createSolveAndLoadTree() throws Exception {
        String createBody = """
                {
                  "name": "Test Plan",
                  "masterRollCodes": ["MR-1200-5000-A"],
                  "childOrderCodes": ["CO-001", "CO-002", "CO-003"]
                }
                """;
        String planVersionId = given()
                .contentType(ContentType.JSON)
                .body(createBody)
                .when()
                .post("/api/v1/slitting/plans")
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"))
                .extract()
                .path("planVersionId");

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/slitting/plans/" + planVersionId + "/solve")
                .then()
                .statusCode(200)
                .body("status", equalTo("SOLVED"))
                .body("utilizationPct", greaterThan(0f));

        given()
                .when()
                .get("/api/v1/slitting/plans/" + planVersionId + "/tree")
                .then()
                .statusCode(200)
                .body("nodes.size()", greaterThan(0))
                .body("assignments.size()", greaterThan(0))
                .body("utilizationPct", notNullValue());
    }
}
