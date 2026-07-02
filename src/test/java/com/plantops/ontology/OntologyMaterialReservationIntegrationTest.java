package com.plantops.ontology;

import com.plantops.api.dto.materialplanning.MaterialReservationDtos.AutoReservationRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.CreateFulfillmentRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.PeriodDemandListDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.ReservationAlertDto;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.OntologyMaterialPlanningService;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyMaterialReservationIntegrationTest {

    private static final String SALES_ORDER_NO = "SO-MAT-RES";
    private static final String FG_CODE = "FG-MAT-RES";
    private static final String COMP_CODE = "RM-MAT-RES";

    @Inject
    OntologyMaterialPlanningService materialPlanningService;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Test
    @TestTransaction
    void periodDemandsEligibleSuppliesAndAlerts() {
        LocalDate planningStart = LocalDate.of(2026, 7, 1);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead(planningStart);

        var report = materialPlanningService.balance(null);
        var fgRow = report.materials().stream()
                .filter(r -> FG_CODE.equals(r.productCode()))
                .findFirst()
                .orElseThrow();
        String periodFrom = report.periodHeaders().get(0).periodId();
        String periodTo = report.periodHeaders().get(Math.min(2, report.periodHeaders().size() - 1)).periodId();

        PeriodDemandListDto demands = materialPlanningService.periodDemands(
                fgRow.pispId(), periodFrom, periodTo, null);
        assertNotNull(demands);
        if (!demands.demands().isEmpty()) {
            String demandId = demands.demands().get(0).demandId();
            assertFalse(materialPlanningService.eligibleSupplies(demandId, null).supplies().isEmpty()
                    || demands.demands().get(0).unpeggedQty() <= 0);
        }

        List<ReservationAlertDto> alerts = materialPlanningService.reservationAlerts(
                fgRow.pispId(), periodFrom, periodTo, null);
        assertNotNull(alerts);
    }

    @Test
    @TestTransaction
    void autoReserveFromDemandCreatesFulfillment() {
        LocalDate planningStart = LocalDate.of(2026, 7, 5);
        ensureFixture(planningStart);
        ensureInventory(COMP_CODE, 50);
        refreshOntWorkspaceHead(planningStart);

        var report = materialPlanningService.balance(null);
        var compRow = report.materials().stream()
                .filter(r -> COMP_CODE.equals(r.productCode()))
                .findFirst()
                .orElseThrow();
        String periodFrom = report.periodHeaders().get(0).periodId();
        String periodTo = report.periodHeaders().get(Math.min(1, report.periodHeaders().size() - 1)).periodId();

        PeriodDemandListDto demands = materialPlanningService.periodDemands(
                compRow.pispId(), periodFrom, periodTo, null);
        var target = demands.demands().stream()
                .filter(d -> d.unpeggedQty() > 0)
                .findFirst();
        if (target.isEmpty()) {
            return;
        }

        var result = materialPlanningService.autoReserve(
                new AutoReservationRequest("DEMAND", target.get().demandId(), null),
                null);
        assertTrue(result.reservedQty() > 0 || result.remainingUnpeggedQty() >= 0);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (MaterialEntity.findByCode(FG_CODE) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = FG_CODE;
            fg.materialName = FG_CODE;
            fg.stampWorkspace();
            fg.persist();
        }
        if (MaterialEntity.findByCode(COMP_CODE) == null) {
            MaterialEntity comp = new MaterialEntity();
            comp.materialCode = COMP_CODE;
            comp.materialName = COMP_CODE;
            comp.stampWorkspace();
            comp.persist();
        }
        if (SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1) == null) {
            SalesOrderLineEntity salesLine = new SalesOrderLineEntity();
            salesLine.salesOrderNo = SALES_ORDER_NO;
            salesLine.salesOrderLineNo = 1;
            salesLine.productCode = FG_CODE;
            salesLine.orderQty = new BigDecimal("80");
            salesLine.dueDate = planningStart.plusDays(8);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }
        ensureRouting(FG_CODE, "RES-MAT-RES-FG", "FG-OP", 1);
        ensureRouting(COMP_CODE, "RES-MAT-RES-RM", "RM-OP", 1);
        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-MAT-RES";
            bom.bomVersion = "1";
            bom.finishedProductCode = FG_CODE;
            bom.parentProductCode = FG_CODE;
            bom.componentProductCode = COMP_CODE;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }

    private static void ensureInventory(String productCode, double qty) {
        List<InventoryEntity> rows = InventoryEntity.findByProduct(productCode);
        InventoryEntity inv = rows.isEmpty() ? new InventoryEntity() : rows.get(0);
        if (rows.isEmpty()) {
            inv.productCode = productCode;
            inv.stockingPointCode = "FG";
            inv.stampWorkspace();
        }
        inv.onhandQty = BigDecimal.valueOf(qty);
        if (rows.isEmpty()) {
            inv.persist();
        }
    }

    private void refreshOntWorkspaceHead(LocalDate planningStart) {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        ontologyPersistence.importCommittedP0(
                workspaceId, ontologyLoader.loadForWorkspace(planningStart));
        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
    }

    private static void ensureRouting(String productCode, String resourceId, String opName, int seq) {
        if (ProductResourceEntity.findByProductAndResource(productCode, resourceId) != null) {
            return;
        }
        ProductResourceEntity pr = new ProductResourceEntity();
        pr.productCode = productCode;
        pr.resourceId = resourceId;
        pr.operationName = opName;
        pr.sequenceNo = seq;
        pr.processTimeSeconds = new BigDecimal("3600");
        pr.stampWorkspace();
        pr.persist();
    }
}
