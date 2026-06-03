package com.plantops.api;

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
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
class OrderPlanningChainResourceTest {

    private static RestAssuredConfig longRunningHttp() {
        return RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam(CoreConnectionPNames.CONNECTION_TIMEOUT, 180_000)
                .setParam(CoreConnectionPNames.SO_TIMEOUT, 180_000));
    }

    @Test
    void previewNotFoundForInvalidOrder() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"salesOrderNo\":\"NO-SUCH-ORDER\",\"salesOrderLineNo\":99999}")
                .when()
                .post("/api/v1/planning/order-chain/preview")
                .then()
                .statusCode(404);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void previewReturns200ForSampleOrder() {
        String salesOrderNo = given()
                .when().get("/api/v1/master-data/sales-orders")
                .then().statusCode(200)
                .extract().jsonPath().getString("[0].salesOrderNo");
        int salesOrderLineNo = given()
                .when().get("/api/v1/master-data/sales-orders")
                .then().statusCode(200)
                .extract().jsonPath().getInt("[0].salesOrderLineNo");

        given()
                .config(longRunningHttp())
                .contentType(ContentType.JSON)
                .body(String.format(
                        "{\"salesOrderNo\":\"%s\",\"salesOrderLineNo\":%d}",
                        salesOrderNo, salesOrderLineNo))
                .when()
                .post("/api/v1/planning/order-chain/preview")
                .then()
                .statusCode(200)
                .body("overallStatus", notNullValue())
                .body("nodes.size()", greaterThan(0));
    }
}
