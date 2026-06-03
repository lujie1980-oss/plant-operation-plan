package com.plantops.api;

import com.plantops.api.dto.execution.CreateScheduleSessionRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ScheduleSessionResourceTest {

    @Test
    void createSessionRejectsEmptyBody() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/planning/schedule-sessions")
                .then()
                .statusCode(400);
    }

    @Test
    void confirmUnknownSessionReturns404() {
        given()
                .when()
                .post("/api/v1/planning/schedule-sessions/SS-unknown/confirm")
                .then()
                .statusCode(404);
    }
}
