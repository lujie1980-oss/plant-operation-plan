package com.plantops.integration;

import com.plantops.testsupport.SpecRef;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@SpecRef({"AC-INT-01", "AC-INT-02", "AC-INT-03"})
class IntegrationApiIntegrationTest {

    private static final String WORKSPACE = "default";

    @Test
    @TestTransaction
    void listBatchesAfterImport() {
        String batchId = importMinimalBundle();
        given()
                .when()
                .get("/api/v1/integration/batches?limit=50")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("importBatchId", hasItem(batchId));
    }

    @Test
    @TestTransaction
    void listAdaptersReturnsCatalog() {
        given()
                .when()
                .get("/api/v1/integration/adapters")
                .then()
                .statusCode(200)
                .body("size()", equalTo(3))
                .body("[0].adapterId", notNullValue())
                .body("[0].name", notNullValue());
    }

    @Test
    @TestTransaction
    void browseExternalStockingPointRows() {
        String batchId = importMinimalBundle();
        given()
                .queryParam("importBatchId", batchId)
                .when()
                .get("/api/v1/integration/external/master/external_stocking_point")
                .then()
                .statusCode(200)
                .body("tableName", equalTo("external_stocking_point"))
                .body("totalElements", greaterThan(0))
                .body("rows.size()", greaterThan(0));
    }

    @Test
    @TestTransaction
    void runMesAdapter() {
        enableAdapter("ADP-MES");
        given()
                .header("X-Workspace-Id", WORKSPACE)
                .when()
                .post("/api/v1/integration/adapters/ADP-MES/run")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));
    }

    @Test
    @TestTransaction
    void saveErpAdapterConfig() {
        String body =
                """
                {"config":{"connectionUrl":"https://sap.example","credentialRef":"vault:sap-01"}}
                """;
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/api/v1/integration/adapters/ADP-ERP-SAP/config")
                .then()
                .statusCode(200)
                .body("config.connectionUrl", equalTo("https://sap.example"))
                .body("config.credentialRef", equalTo("vault:sap-01"));
    }

    @Test
    @TestTransaction
    void qualityReportForBatch() {
        String batchId = importMinimalBundle();
        given()
                .queryParam("importBatchId", batchId)
                .when()
                .get("/api/v1/integration/quality")
                .then()
                .statusCode(200)
                .body("importBatchId", equalTo(batchId))
                .body("pendingCount", greaterThanOrEqualTo(0));
    }

    @Test
    @TestTransaction
    void runErpSapAdapterImportsTransactionalStaging() {
        enableAdapter("ADP-ERP-SAP");
        given()
                .header("X-Workspace-Id", WORKSPACE)
                .when()
                .post("/api/v1/integration/adapters/ADP-ERP-SAP/run")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));
    }

    private static String importMinimalBundle() {
        String body =
                """
                {
                  "sourceSystem":"INT-API-TEST",
                  "stockingPoints":[{"code":"FG-INT19","name":"成品","siteCode":"SITE-1"}],
                  "resourceGroups":[],
                  "standardResources":[],
                  "physicalResources":[],
                  "productInStockingPoints":[],
                  "routings":[],
                  "routingSteps":[],
                  "routingStepOsrs":[],
                  "routingStepInputMaterials":[]
                }
                """;
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/integration/master-data/import/routing")
                .then()
                .statusCode(200)
                .body("rowCount", greaterThan(0))
                .extract()
                .path("importBatchId");
    }

    private void enableAdapter(String adapterId) {
        String body =
                """
                {"adapters":[{"adapterId":"%s","enabled":true}]}
                """
                        .formatted(adapterId);
        given()
                .contentType(ContentType.JSON)
                .header("X-Workspace-Id", WORKSPACE)
                .body(body)
                .when()
                .put("/api/v1/iam/workspaces/" + WORKSPACE + "/adapters")
                .then()
                .statusCode(200);
    }
}
