package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class MasterPlanPlanningPreviewResourceTest {

    @Test
    void previewRejectsPersistWithoutSolve() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"persist\":true,\"solve\":false}")
                .when()
                .post("/api/v1/planning/master-plan/preview")
                .then()
                .statusCode(400);
    }

}
