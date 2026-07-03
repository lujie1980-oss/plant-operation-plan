package com.plantops.masterdata.external;

import com.plantops.api.dto.integration.IntegrationDtos.PhysicalResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.PispRow;
import com.plantops.api.dto.integration.IntegrationDtos.ResourceGroupRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepImRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepOsrRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepRow;
import com.plantops.api.dto.integration.IntegrationDtos.StandardResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.StockingPointRow;
import com.plantops.masterdata.quality.MasterDataQualityService;
import com.plantops.masterdata.sync.MasterDataSyncService;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.MasterPlanRoutingProjector;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.persistence.entity.MdRoutingEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterDataExternalPipelineIntegrationTest {

    private static final String PRODUCT = "FG-MD13-TEST";
    private static final String ROUTING = "RT-MD13-TEST";

    @Inject
    MasterDataExternalImportService importService;

    @Inject
    MasterDataQualityService qualityService;

    @Inject
    MasterDataSyncService syncService;

    @Inject
    MasterPlanRoutingProjector routingProjector;

    @Test
    @TestTransaction
    void importQualitySyncThenProjectorReadsMd() {
        var result = importService.importRoutingBundle(minimalBundle());
        assertTrue(result.rowCount() > 0);

        for (var row : com.plantops.persistence.entity.ExternalStockingPointEntity.listForBatch(result.importBatchId())) {
            assertEquals("PENDING", row.qualityStatus);
        }

        var quality = qualityService.checkBatch(result.importBatchId());
        assertEquals(0, quality.failedCount());
        assertTrue(quality.passedCount() > 0);

        var sync = syncService.syncPassedBatch(result.importBatchId());
        assertTrue(sync.syncedRows() > 0);

        assertTrue(MdRoutingEntity.listInWorkspace().stream().anyMatch(r -> PRODUCT.equals(r.productCode)));

        long legacyPrCount =
                ProductResourceEntity.find("productCode", PRODUCT).count();
        assertEquals(0, legacyPrCount, "M5: sync must not write legacy product_resource");

        assertTrue(routingProjector.hasRoutingForProduct(PRODUCT));
        String pispId = OntologyIds.pispId(PRODUCT, StockingPoint.FG);
        assertEquals(1, routingProjector.projectRoutingSteps(pispId, PRODUCT).size());
        assertFalse(routingProjector.projectRoutingSteps(pispId, PRODUCT).get(0).inputMaterials().isEmpty());
    }

    @Test
    @TestTransaction
    void failedOsrNotSyncedToMd() {
        var bundle = new RoutingBundleImport(
                "TEST",
                List.of(new StockingPointRow("FG", "成品", "SITE-1")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new PispRow(PRODUCT, "FG", true, null, null)),
                List.of(new RoutingRow(ROUTING, PRODUCT, "FG", 1, "Test routing")),
                List.of(new RoutingStepRow(ROUTING, 1, "OP1", "OP-1")),
                List.of(new RoutingStepOsrRow(ROUTING, 1, "MISSING-SR", 1, 0, BigDecimal.valueOf(60))),
                List.of());

        var result = importService.importRoutingBundle(bundle);
        var quality = qualityService.checkBatch(result.importBatchId());
        assertTrue(quality.failedCount() > 0);

        var sync = syncService.syncPassedBatch(result.importBatchId());
        assertTrue(sync.skippedRows() > 0);
        assertFalse(MdRoutingEntity.listInWorkspace().stream().anyMatch(r -> ROUTING.equals(r.routingCode)));
    }

    @Test
    @TestTransaction
    void externalTablesApiListsMasterDomain() {
        given()
                .when()
                .get("/api/v1/integration/external/master/tables")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(10));
    }

    @Test
    @TestTransaction
    void restImportQualitySyncRoundTrip() {
        String body =
                """
                {
                  "sourceSystem":"REST-TEST",
                  "stockingPoints":[{"code":"FG","name":"成品","siteCode":"SITE-1"}],
                  "resourceGroups":[{"code":"RG-MD13","name":"组","calendarCode":"CAL-1","resourceEfficiency":1.0}],
                  "standardResources":[{"code":"RES-MD13","name":"机台","resourceGroupCode":"RG-MD13","capacityUom":"H","bottleneck":false,"resourceEfficiency":1.0}],
                  "physicalResources":[{"code":"PR-MD13","name":"线体","standardResourceCode":"RES-MD13","productionLineCode":"LINE-MD13","status":"ACTIVE"}],
                  "productInStockingPoints":[{"productCode":"FG-MD13-REST","stockingPointCode":"FG","planningRelevant":true}],
                  "routings":[{"routingCode":"RT-MD13-REST","productCode":"FG-MD13-REST","stockingPointCode":"FG","pathPriority":1,"routingName":"REST routing"}],
                  "routingSteps":[{"routingCode":"RT-MD13-REST","sequenceNo":1,"operationCode":"OP1","operationName":"OP-REST"}],
                  "routingStepOsrs":[{"routingCode":"RT-MD13-REST","sequenceNo":1,"standardResourceCode":"RES-MD13","resourcePriority":1,"setupTimeMinutes":0,"processTimeSeconds":3600}],
                  "routingStepInputMaterials":[]
                }
                """;

        String batchId = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/integration/master-data/import/routing")
                .then()
                .statusCode(200)
                .body("rowCount", greaterThan(0))
                .extract()
                .path("importBatchId");

        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + batchId + "\"}")
                .when()
                .post("/api/v1/integration/master-data/quality/check")
                .then()
                .statusCode(200)
                .body("failedCount", equalTo(0));

        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + batchId + "\"}")
                .when()
                .post("/api/v1/integration/master-data/sync")
                .then()
                .statusCode(200)
                .body("syncedRows", greaterThan(0));
    }

    private static RoutingBundleImport minimalBundle() {
        return new RoutingBundleImport(
                "TEST",
                List.of(new StockingPointRow("FG", "成品", "SITE-1")),
                List.of(new ResourceGroupRow("RG-MD13", "组", "CAL-1", BigDecimal.ONE)),
                List.of(new StandardResourceRow(
                        "RES-MD13-A", "机台 A", "RG-MD13", "H", false, BigDecimal.ONE)),
                List.of(new PhysicalResourceRow("PR-MD13-A", "线体 A", "RES-MD13-A", "LINE-MD13", "ACTIVE")),
                List.of(new PispRow(PRODUCT, "FG", true, null, null)),
                List.of(new RoutingRow(ROUTING, PRODUCT, "FG", 1, "MD13 test routing")),
                List.of(new RoutingStepRow(ROUTING, 1, "OP1", "OP-MD13-1")),
                List.of(new RoutingStepOsrRow(
                        ROUTING, 1, "RES-MD13-A", 1, 0, BigDecimal.valueOf(3600))),
                List.of(new RoutingStepImRow(ROUTING, 1, "RM-MD13-TEST", BigDecimal.ONE, "RAW")));
    }
}
