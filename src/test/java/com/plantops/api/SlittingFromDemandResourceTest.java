package com.plantops.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class SlittingFromDemandResourceTest {

    @Test
    void importChildOrdersFromDemand() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "skipExisting": true }
                        """)
                .when()
                .post("/api/v1/slitting/child-orders/from-demand")
                .then()
                .statusCode(200)
                .body("created", greaterThanOrEqualTo(0));
    }
}
