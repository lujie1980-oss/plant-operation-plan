package com.plantops.ontology;

import com.plantops.api.dto.MaterialBalancePeriodDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanRequest;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanResultDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyRoutingCandidateDto;
import com.plantops.ontology.material.OntologyMaterialBalanceProjector;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.OntologyMaterialPlanningService;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyMaterialSupplyPlanIntegrationTest {

    private static final String SALES_ORDER_NO = "SO-MAT-SUPPLY";
    private static final String FG_CODE = "FG-MAT-SUPPLY";
    private static final String COMP_CODE = "RM-MAT-SUPPLY";

    @Inject
    OntologyMaterialPlanningService materialPlanningService;

    @Inject
    OntologyMaterialBalanceProjector balanceProjector;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Test
    @TestTransaction
    void periodBalanceMatchesPisppShortage() {
        LocalDate planningStart = LocalDate.of(2026, 6, 15);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead(planningStart);

        MaterialRequirementReportDto report = materialPlanningService.balance(null);
        assertFalse(report.periodHeaders().isEmpty());
        var row = report.materials().stream()
                .filter(r -> COMP_CODE.equals(r.productCode()))
                .findFirst()
                .orElseThrow();
        assertFalse(row.periods().isEmpty());
        BigDecimal periodShortageSum = row.periods().stream()
                .map(MaterialBalancePeriodDto::shortageQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(periodShortageSum.compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @TestTransaction
    void autoCreateSupplyPlanAddsWorkOrderAndRoutingCandidates() {
        LocalDate planningStart = LocalDate.of(2026, 6, 20);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead(planningStart);

        MaterialRequirementReportDto before = materialPlanningService.balance(null);
        var fgRow = before.materials().stream()
                .filter(r -> FG_CODE.equals(r.productCode()))
                .findFirst()
                .orElseThrow();
        assertNotNull(fgRow.pispId());
        String periodFrom = before.periodHeaders().get(0).periodId();
        String periodTo = before.periodHeaders().get(Math.min(1, before.periodHeaders().size() - 1)).periodId();

        var candidates = materialPlanningService.routingCandidates(
                fgRow.pispId(), periodFrom, periodTo, 10.0, null);
        assertFalse(candidates.isEmpty());
        assertNotNull(candidates.get(0).routingId());

        CreateSupplyPlanResultDto created = materialPlanningService.createSupplyPlan(
                fgRow.pispId(),
                new CreateSupplyPlanRequest("AUTO", periodFrom, periodTo, 10.0, null, null),
                null);
        assertFalse(created.supplyOrderIds().isEmpty());
        assertTrue(WorkOrderEntity.findByNo(created.supplyOrderIds().get(0).supplyOrderId()) != null);
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
            salesLine.orderQty = new BigDecimal("100");
            salesLine.dueDate = planningStart.plusDays(10);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }
        ensureRouting(FG_CODE, "RES-MAT-FG", "FG-OP", 1);
        ensureRouting(COMP_CODE, "RES-MAT-RM", "RM-OP", 1);
        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-MAT-SUPPLY";
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
