package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SimulationProfileResourceTest {

    @Test
    void listIncludesDefaultProfile() {
        given()
                .when()
                .get("/api/v1/planning/simulation-profiles")
                .then()
                .statusCode(200)
                .body("find { it.profileId == 'SP-DEFAULT' }.profileId", equalTo("SP-DEFAULT"));
    }

    @Test
    void getDefaultProfile() {
        given()
                .when()
                .get("/api/v1/planning/simulation-profiles/SP-DEFAULT")
                .then()
                .statusCode(200)
                .body("profileId", equalTo("SP-DEFAULT"))
                .body("configJson", notNullValue());
    }
}
