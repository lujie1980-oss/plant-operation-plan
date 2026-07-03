package com.plantops.ontology;

import com.plantops.testsupport.SpecRef;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.ontology.material.OntologyMaterialBalanceProjector;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.scenario.OntologyMaterialPlanningService;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
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
@SpecRef("AC-17")
class OntologyMaterialPlanningProjectionTest {

    private static final String SALES_ORDER_NO = "SO-MP-PROJ";
    private static final String PARENT_WO = "WO-MP-PARENT";
    private static final String CHILD_WO = "WO-MP-CHILD";
    private static final String FG_CODE = "FG-MP-100";
    private static final String COMP_CODE = "RM-MP-200";

    @Inject
    OntologyMaterialPlanningService ontologyMaterialPlanningService;

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
    void projectsMaterialBalanceFromPispp() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead(planningStart);

        MaterialRequirementReportDto report = ontologyMaterialPlanningService.balance(null);

        assertNotNull(report);
        assertFalse(report.materials().isEmpty());
        assertFalse(report.dates().isEmpty());
        assertTrue(report.kpis().stream().anyMatch(k -> "mrp_ontology_mode".equals(k.metricId())));
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
            salesLine.orderQty = new BigDecimal("40");
            salesLine.dueDate = planningStart.plusDays(7);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }
        if (WorkOrderEntity.findByNo(CHILD_WO) == null) {
            WorkOrderEntity child = new WorkOrderEntity();
            child.workOrderNo = CHILD_WO;
            child.salesOrderNo = SALES_ORDER_NO;
            child.salesOrderLineNo = 1;
            child.productCode = COMP_CODE;
            child.quantity = new BigDecimal("60");
            child.needDate = planningStart.plusDays(4);
            child.resourceId = "RES-MP-CHILD";
            child.parentWorkOrderNo = PARENT_WO;
            child.sequenceNo = WorkOrderEntity.nextSequenceNo();
            child.sourceType = WorkOrderEntity.SOURCE_MRP;
            child.stampWorkspace();
            child.persist();
        }
        if (WorkOrderEntity.findByNo(PARENT_WO) == null) {
            WorkOrderEntity parent = new WorkOrderEntity();
            parent.workOrderNo = PARENT_WO;
            parent.salesOrderNo = SALES_ORDER_NO;
            parent.salesOrderLineNo = 1;
            parent.productCode = FG_CODE;
            parent.quantity = new BigDecimal("60");
            parent.needDate = planningStart.plusDays(5);
            parent.resourceId = "RES-MP-PARENT";
            parent.sequenceNo = WorkOrderEntity.nextSequenceNo();
            parent.sourceType = WorkOrderEntity.SOURCE_MRP;
            parent.stampWorkspace();
            parent.persist();
        }
        if (WorkOrderBomDependencyEntity.findByParent(PARENT_WO).isEmpty()) {
            WorkOrderBomDependencyEntity dep = new WorkOrderBomDependencyEntity();
            dep.parentWorkOrderNo = PARENT_WO;
            dep.childWorkOrderNo = CHILD_WO;
            dep.stampWorkspace();
            dep.persist();
        }
        ensureRouting(FG_CODE, "RES-MP-PARENT", "MP-OP-A", 1);
        ensureRouting(COMP_CODE, "RES-MP-CHILD", "RM-OP-A", 1);
        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-MP-PROJ";
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
