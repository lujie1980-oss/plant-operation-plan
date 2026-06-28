package com.plantops.api;

import com.plantops.config.ParameterRegistry;
import com.plantops.persistence.entity.SystemParameterEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SlittingSessionResourceTest {

    @Inject
    ParameterRegistry parameterRegistry;

    @BeforeEach
    @Transactional
    void shortSolverLimits() {
        setParam("slitting_solver_seconds", "3");
        setParam("slitting_session_solver_seconds", "2");
    }

    private void setParam(String id, String value) {
        SystemParameterEntity entity = SystemParameterEntity.findByParamId(id);
        if (entity == null) {
            entity = new SystemParameterEntity();
            entity.paramId = id;
            entity.paramValue = value;
            entity.description = "test";
            entity.stampWorkspace();
            entity.persist();
        } else {
            entity.paramValue = value;
        }
        parameterRegistry.invalidate(id);
    }

    @Test
    void sessionLocalOptimizeAndConfirm() {
        String planVersionId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Session Plan",
                          "masterRollCodes": ["MR-1200-5000-A"],
                          "childOrderCodes": ["CO-001", "CO-002"]
                        }
                        """)
                .when()
                .post("/api/v1/slitting/plans")
                .then()
                .statusCode(200)
                .extract()
                .path("planVersionId");

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/slitting/plans/" + planVersionId + "/solve")
                .then()
                .statusCode(200);

        String sessionId = given()
                .contentType(ContentType.JSON)
                .body("""
                        { "planVersionId": "%s", "activeParentNodeId": null }
                        """.formatted(planVersionId))
                .when()
                .post("/api/v1/slitting/sessions")
                .then()
                .statusCode(201)
                .body("assignments.size()", greaterThan(0))
                .extract()
                .path("sessionId");

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/slitting/sessions/" + sessionId + "/local-optimize")
                .then()
                .statusCode(200)
                .body("score", notNullValue());

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/slitting/sessions/" + sessionId + "/confirm")
                .then()
                .statusCode(200)
                .body("assignments.size()", greaterThan(0));

        given()
                .when()
                .get("/api/v1/slitting/plans/" + planVersionId + "/tree")
                .then()
                .statusCode(200)
                .body("assignments.size()", greaterThan(0));
    }
}
