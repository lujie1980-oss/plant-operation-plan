package com.plantops.api;

import com.plantops.persistence.entity.DeliveryDateStrategyEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class Section16BusinessRulesResourceTest {

    @Test
    @TestTransaction
    void deliveryDateStrategyCrudRoundTrip() {
        String body =
                """
                {"customerCode":"C-TODO17","productCode":"P-TODO17","deliveryGranularity":"DAILY",\
                "earlyAllowDays":1,"lateAllowDays":3,"earlyPenaltyCoef":1.0,"latePenaltyCoef":2.0}
                """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/master-data/delivery-date-strategy")
                .then()
                .statusCode(200)
                .body("customerCode", equalTo("C-TODO17"))
                .body("lateAllowDays", equalTo(3));

        given()
                .when()
                .get("/api/v1/master-data/delivery-date-strategy")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        DeliveryDateStrategyEntity row =
                DeliveryDateStrategyEntity.findByKey("C-TODO17", "P-TODO17");
        org.junit.jupiter.api.Assertions.assertNotNull(row);
        if (row != null) {
            row.delete();
        }
    }
}
