package com.plantops.transactional.external;

import com.plantops.masterdata.external.MasterDataExternalImportService;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineDeliveryRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderRow;
import com.plantops.api.dto.integration.IntegrationDtos.TransactionalBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderOperationResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderOperationRow;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderRow;
import com.plantops.api.dto.integration.IntegrationDtos.PhysicalResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.PispRow;
import com.plantops.api.dto.integration.IntegrationDtos.ResourceGroupRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepOsrRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepRow;
import com.plantops.api.dto.integration.IntegrationDtos.StandardResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.StockingPointRow;
import com.plantops.masterdata.quality.MasterDataQualityService;
import com.plantops.masterdata.sync.MasterDataSyncService;
import com.plantops.ontology.OntologyGraph;
import com.plantops.persistence.entity.MdPispEntity;
import com.plantops.persistence.entity.TxnDemandEntity;
import com.plantops.persistence.entity.TxnSupplyOrderEntity;
import com.plantops.transactional.internal.TxnOntologyLoadContributor;
import com.plantops.transactional.quality.TransactionalDataQualityService;
import com.plantops.transactional.sync.TransactionalDataSyncService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransactionalDataExternalPipelineIntegrationTest {

    private static final String PRODUCT = "FG-TX14-TEST";
    private static final String WO = "WO-TX14-TEST";
    private static final String SO = "SO-TX14-TEST";

    @Inject
    MasterDataExternalImportService masterImportService;

    @Inject
    MasterDataQualityService masterQualityService;

    @Inject
    MasterDataSyncService masterSyncService;

    @Inject
    TransactionalDataExternalImportService transactionalImportService;

    @Inject
    TransactionalDataQualityService transactionalQualityService;

    @Inject
    TransactionalDataSyncService transactionalSyncService;

    @Inject
    TxnOntologyLoadContributor txnOntologyLoadContributor;

    @Test
    @TestTransaction
    void importQualitySyncCreatesFirmSoAndDemand() {
        ensureMasterData();

        var result = transactionalImportService.importBundle(minimalBundle());
        assertTrue(result.rowCount() > 0);

        var quality = transactionalQualityService.checkBatch(result.importBatchId());
        assertEquals(0, quality.failedCount());

        var sync = transactionalSyncService.syncPassedBatch(result.importBatchId());
        assertTrue(sync.syncedRows() > 0);

        TxnSupplyOrderEntity so = TxnSupplyOrderEntity.find(
                        "workspaceId = ?1 and supplyOrderId = ?2", TxnSupplyOrderEntity.ws(), WO)
                .firstResult();
        assertTrue(so != null);
        assertEquals(TxnSupplyOrderEntity.FIRM_STATUS_FIRM, so.firmStatus);

        String coldId = com.plantops.ontology.OntologyIds.customerOrderLineDeliveryId(SO, 1, 1);
        TxnDemandEntity demand = TxnDemandEntity.find(
                        "workspaceId = ?1 and sourceId = ?2", TxnDemandEntity.ws(), coldId)
                .firstResult();
        assertTrue(demand != null);
        assertEquals("CUSTOMER_DELIVERY", demand.sourceType);

        assertTrue(txnOntologyLoadContributor.hasTransactionalDemands());
        OntologyGraph.Builder builder = OntologyGraph.builder();
        txnOntologyLoadContributor.loadDemandsFromTxn(builder);
        assertFalse(builder.demandsById().isEmpty());
    }

    @Test
    @TestTransaction
    void transactionalTablesApiListsDomain() {
        given()
                .when()
                .get("/api/v1/integration/external/transactional/tables")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(8));
    }

    @Test
    void restImportQualitySyncRoundTrip() {
        String mdBody =
                """
                {
                  "sourceSystem":"REST-MD-TX14",
                  "stockingPoints":[{"code":"FG","name":"成品","siteCode":"SITE-1"}],
                  "resourceGroups":[{"code":"RG-TX14","name":"组","calendarCode":"CAL-1","resourceEfficiency":1.0}],
                  "standardResources":[{"code":"RES-TX14","name":"机台","resourceGroupCode":"RG-TX14","capacityUom":"H","bottleneck":false,"resourceEfficiency":1.0}],
                  "physicalResources":[{"code":"PR-TX14","name":"线体","standardResourceCode":"RES-TX14","productionLineCode":"LINE-TX14","status":"ACTIVE"}],
                  "productInStockingPoints":[{"productCode":"FG-TX14-TEST","stockingPointCode":"FG","planningRelevant":true}],
                  "routings":[{"routingCode":"RT-TX14-REST","productCode":"FG-TX14-TEST","stockingPointCode":"FG","pathPriority":1,"routingName":"REST routing"}],
                  "routingSteps":[{"routingCode":"RT-TX14-REST","sequenceNo":1,"operationCode":"OP1","operationName":"OP-REST"}],
                  "routingStepOsrs":[{"routingCode":"RT-TX14-REST","sequenceNo":1,"standardResourceCode":"RES-TX14","resourcePriority":1,"setupTimeMinutes":0,"processTimeSeconds":3600}],
                  "routingStepInputMaterials":[]
                }
                """;
        String mdBatch = given()
                .contentType(ContentType.JSON)
                .body(mdBody)
                .when()
                .post("/api/v1/integration/master-data/import/routing")
                .then()
                .statusCode(200)
                .extract()
                .path("importBatchId");
        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + mdBatch + "\"}")
                .when()
                .post("/api/v1/integration/master-data/quality/check")
                .then()
                .statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + mdBatch + "\"}")
                .when()
                .post("/api/v1/integration/master-data/sync")
                .then()
                .statusCode(200);

        String body =
                """
                {
                  "sourceSystem":"REST-TX",
                  "customerOrders":[{"customerOrderNo":"SO-TX14-REST","customerCode":"C1","orderDate":"2026-07-01","orderStatus":"OPEN","priority":5}],
                  "customerOrderLines":[{"customerOrderNo":"SO-TX14-REST","lineNo":1,"productCode":"FG-TX14-TEST","orderQty":100}],
                  "customerOrderLineDeliveries":[{"customerOrderNo":"SO-TX14-REST","lineNo":1,"deliverySeq":1,"deliveryQty":100,"requestedDate":"2026-08-01","lineStatus":"OPEN"}],
                  "workOrders":[{"workOrderNo":"WO-TX14-REST","productCode":"FG-TX14-TEST","quantity":50,"needDate":"2026-08-15","firmFlag":true,"dispatchStatus":"OPEN"}],
                  "workOrderOperations":[{"workOrderNo":"WO-TX14-REST","operationSeq":1,"operationCode":"OP1","operationName":"OP-REST"}],
                  "workOrderOperationResources":[{"workOrderNo":"WO-TX14-REST","operationSeq":1,"standardResourceCode":"RES-TX14","resourcePriority":1,"setupTimeMinutes":0,"processTimeSeconds":3600}]
                }
                """;

        String batchId = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/integration/transactional-data/import")
                .then()
                .statusCode(200)
                .body("rowCount", greaterThan(0))
                .extract()
                .path("importBatchId");

        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + batchId + "\"}")
                .when()
                .post("/api/v1/integration/transactional-data/quality/check")
                .then()
                .statusCode(200)
                .body("failedCount", equalTo(0));

        given()
                .contentType(ContentType.JSON)
                .body("{\"importBatchId\":\"" + batchId + "\"}")
                .when()
                .post("/api/v1/integration/transactional-data/sync")
                .then()
                .statusCode(200)
                .body("syncedRows", greaterThan(0));
    }

    private void ensureMasterData() {
        if (!MdPispEntity.listInWorkspace().stream().anyMatch(p -> PRODUCT.equals(p.productCode))) {
            importMasterDataFixture();
        }
    }

    private void importMasterDataFixture() {
        String routing = "RT-TX14-" + PRODUCT;
        var mdResult = masterImportService.importRoutingBundle(new RoutingBundleImport(
                "TEST",
                List.of(new StockingPointRow("FG", "成品", "SITE-1")),
                List.of(new ResourceGroupRow("RG-TX14", "组", "CAL-1", BigDecimal.ONE)),
                List.of(new StandardResourceRow("RES-TX14", "机台", "RG-TX14", "H", false, BigDecimal.ONE)),
                List.of(new PhysicalResourceRow("PR-TX14", "线体", "RES-TX14", "LINE-TX14", "ACTIVE")),
                List.of(new PispRow(PRODUCT, "FG", true, null, null)),
                List.of(new RoutingRow(routing, PRODUCT, "FG", 1, "TX14 routing")),
                List.of(new RoutingStepRow(routing, 1, "OP1", "OP-TX14")),
                List.of(new RoutingStepOsrRow(routing, 1, "RES-TX14", 1, 0, BigDecimal.valueOf(3600))),
                List.of()));
        masterQualityService.checkBatch(mdResult.importBatchId());
        masterSyncService.syncPassedBatch(mdResult.importBatchId());
    }

    private static TransactionalBundleImport minimalBundle() {
        return new TransactionalBundleImport(
                "TEST",
                List.of(new CustomerOrderRow(SO, "C-TX14", LocalDate.of(2026, 7, 1), "OPEN", 5)),
                List.of(new CustomerOrderLineRow(SO, 1, PRODUCT, BigDecimal.valueOf(100), "EA")),
                List.of(new CustomerOrderLineDeliveryRow(
                        SO, 1, 1, BigDecimal.valueOf(100), LocalDate.of(2026, 8, 1), "OPEN")),
                List.of(new WorkOrderRow(
                        WO, PRODUCT, BigDecimal.valueOf(50), LocalDate.of(2026, 8, 15), true, "OPEN")),
                List.of(new WorkOrderOperationRow(WO, 1, "OP1", "OP-TX14-1")),
                List.of(new WorkOrderOperationResourceRow(
                        WO, 1, "RES-TX14", 1, 0, BigDecimal.valueOf(3600))),
                List.of(),
                List.of());
    }
}
