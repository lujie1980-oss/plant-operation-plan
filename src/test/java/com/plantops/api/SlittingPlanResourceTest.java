package com.plantops.api;

import com.plantops.config.ParameterRegistry;
import com.plantops.persistence.entity.SystemParameterEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SlittingPlanResourceTest {

    @Inject
    ParameterRegistry parameterRegistry;

    @BeforeEach
    @Transactional
    void shortSlittingSolverLimit() {
        SystemParameterEntity entity = SystemParameterEntity.findByParamId("slitting_solver_seconds");
        if (entity == null) {
            entity = new SystemParameterEntity();
            entity.paramId = "slitting_solver_seconds";
            entity.paramValue = "3";
            entity.description = "test override";
            entity.stampWorkspace();
            entity.persist();
        } else {
            entity.paramValue = "3";
        }
        parameterRegistry.invalidate("slitting_solver_seconds");
    }

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
                .get("/api/v1/slitting/plans/" + planVersionId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SOLVED"));

        given()
                .when()
                .get("/api/v1/slitting/plans/" + planVersionId + "/tree")
                .then()
                .statusCode(200)
                .body("nodes.size()", greaterThan(0))
                .body("assignments.size()", greaterThan(0))
                .body("utilizationPct", notNullValue());
    }

    @Test
    void saveAssignmentsRoundTrip() {
        String planVersionId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Save Test",
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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = given()
                .when()
                .get("/api/v1/slitting/plans/" + planVersionId + "/tree")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("assignments");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("assignments", assignments))
                .when()
                .put("/api/v1/slitting/plans/" + planVersionId + "/assignments")
                .then()
                .statusCode(200)
                .body("assignments.size()", greaterThan(0));

        given()
                .when()
                .get("/api/v1/slitting/plans/" + planVersionId + "/tree")
                .then()
                .statusCode(200)
                .body("assignments.size()", equalTo(assignments.size()));
    }
}
