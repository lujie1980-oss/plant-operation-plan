package com.plantops;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.apache.http.params.CoreConnectionPNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class PlantOperationPlanResourceTest {

    private static RestAssuredConfig longRunningHttp() {
        return RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam(CoreConnectionPNames.CONNECTION_TIMEOUT, 600_000)
                .setParam(CoreConnectionPNames.SO_TIMEOUT, 600_000));
    }

    @Test
    void demandPool_returnsData() {
        given()
                .when().get("/api/v1/demand/demand-pool")
                .then()
                .statusCode(200)
                .body("size()", notNullValue());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void fullPipeline_succeeds() {
        given()
                .config(longRunningHttp())
                .contentType(ContentType.JSON)
                .when().post("/api/v1/planning/run-full-pipeline")
                .then()
                .statusCode(200)
                .body("masterPlan.planVersionId", notNullValue())
                .body("kittingResults", notNullValue());
    }

    @Test
    void kitting_compute() {
        given()
                .when().post("/api/v1/kitting/compute")
                .then()
                .statusCode(200);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void workOrderGeneration_fromBom() {
        // 取演示样例中的第一条销售订单作为输入，以避免对具体编号的硬编码。
        String firstOrder = given()
                .when().get("/api/v1/master-data/sales-orders")
                .then().statusCode(200)
                .extract().jsonPath().getString("[0].salesOrderNo");
        Integer firstLineNo = given()
                .when().get("/api/v1/master-data/sales-orders")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].salesOrderLineNo");

        given()
                .config(longRunningHttp())
                .contentType(ContentType.JSON)
                .body(String.format("{\"salesOrderNo\":\"%s\",\"salesOrderLineNo\":%d,\"replaceExisting\":true}",
                        firstOrder, firstLineNo))
                .when().post("/api/v1/demand/work-orders/generate")
                .then()
                .statusCode(200)
                .body("workOrdersCreated", notNullValue())
                .body("workOrderNos.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }
}
