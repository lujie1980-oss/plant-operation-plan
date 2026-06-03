package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * preview REST 参数校验（不依赖主计划数据）。
 */
@QuarkusTest
class DetailSchedulePlanningPreviewResourceTest {

    @Test
    void previewRejectsMissingMasterPlanVersionId() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/planning/detail-schedule/preview")
                .then()
                .statusCode(400);
    }

    @Test
    void previewRejectsPersistWithoutSolve() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"masterPlanVersionId\":\"MP-TEST\",\"persist\":true,\"solve\":false}")
                .when()
                .post("/api/v1/planning/detail-schedule/preview")
                .then()
                .statusCode(400);
    }
}
