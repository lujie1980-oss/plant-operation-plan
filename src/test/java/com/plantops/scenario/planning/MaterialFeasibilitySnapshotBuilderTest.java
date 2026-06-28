package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MaterialFeasibilityService;
import com.plantops.scenario.planning.InventorySnapshot;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MaterialFeasibilitySnapshotBuilderTest {

    private static final String PRODUCT_CODE = "FG-OTD-MAT-SNAP";
    private static final String WORK_ORDER_NO = "WO-OTD-MAT-SNAP";

    @Inject
    MaterialFeasibilitySnapshotBuilder snapshotBuilder;

    @Inject
    MaterialFeasibilityService materialFeasibilityService;

    @Inject
    OntologyLoader ontologyLoader;

    @Test
    void fromGraphBuildsClosingSeriesAndBomIndexes() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        OntologyGraph graph = graphWithSinglePeriodSupply(planningStart, 80, 30);

        MaterialFeasibilitySnapshot snapshot = snapshotBuilder.fromGraph(graph);
        MaterialFeasibilityContext context = snapshot.toContext();

        assertNotNull(context.closingOn(PRODUCT_CODE, planningStart));
        assertTrue(context.closingOn(PRODUCT_CODE, planningStart).compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(snapshot.bomByParent());
        assertNotNull(snapshot.manufacturedProducts());
    }

    @Test
    @TestTransaction
    void graphSnapshotClosingAlignsWithEntityServiceOnInventoryOnlyProduct() {
        LocalDate today = LocalDate.now();
        ensureInventoryOnlyFixture(today);

        MaterialFeasibilityContext entityContext = materialFeasibilityService.prepareContext(
                InventorySnapshot.loadFromWorkspace());
        MaterialFeasibilityContext graphContext = snapshotBuilder.toContext(
                ontologyLoader.loadForWorkspace(today));

        BigDecimal entityClosing = entityContext.closingOn(PRODUCT_CODE, today);
        BigDecimal graphClosing = graphContext.closingOn(PRODUCT_CODE, today);
        assertEquals(0, entityClosing.compareTo(graphClosing),
                "opening inventory should match on planning start when only on-hand contributes");
    }

    @Test
    @TestTransaction
    void simulateSupplyChangeReflectsInGraphSnapshot() {
        LocalDate today = LocalDate.now();
        ensureInventoryOnlyFixture(today);

        OntologyGraph graph = ontologyLoader.loadForWorkspace(today);
        String pispId = OntologyIds.pispId(PRODUCT_CODE);
        ProductInStockingPointPeriod p0 = graph.pispPeriod(OntologyIds.pisppId(pispId, 0));
        p0.setPlannedSupplyTotal(p0.getPlannedSupplyTotal() + 50);
        p0.recalculatePlanningFields();

        BigDecimal before = snapshotBuilder.toContext(graph).closingOn(PRODUCT_CODE, today);
        p0.setPlannedSupplyTotal(p0.getPlannedSupplyTotal() + 100);
        p0.recalculatePlanningFields();
        BigDecimal after = snapshotBuilder.toContext(graph).closingOn(PRODUCT_CODE, today);

        assertTrue(after.compareTo(before) > 0, "snapshot should read simulated PISPP supply from graph");
    }

    private static OntologyGraph graphWithSinglePeriodSupply(
            LocalDate planningStart,
            double onHand,
            double supply) {
        String pispId = OntologyIds.pispId(PRODUCT_CODE);
        Period period = new Period(OntologyIds.periodId(0), 0, planningStart, planningStart);
        ProductInStockingPointPeriod pispp = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(pispId, 0), pispId, period.getId());
        pispp.setOnHand(onHand);
        pispp.setPlannedSupplyTotal(supply);
        pispp.recalculatePlanningFields();

        return OntologyGraph.builder()
                .pisp(new ProductInStockingPoint(pispId, PRODUCT_CODE, OntologyIds.DEFAULT_FG, PRODUCT_CODE))
                .periodsOrdered(List.of(period))
                .pispPeriod(pispp)
                .build();
    }

    private void ensureInventoryOnlyFixture(LocalDate planningStart) {
        if (InventoryEntity.find(
                        "workspaceId = ?1 and productCode = ?2",
                        com.plantops.workspace.WorkspaceResolver.currentWorkspaceId(),
                        PRODUCT_CODE)
                .firstResult() == null) {
            InventoryEntity inventory = new InventoryEntity();
            inventory.stockingPointCode = OntologyIds.DEFAULT_FG;
            inventory.productCode = PRODUCT_CODE;
            inventory.onhandQty = new BigDecimal("25");
            inventory.reservedQty = BigDecimal.ZERO;
            inventory.qualityHoldQty = BigDecimal.ZERO;
            inventory.stampWorkspace();
            inventory.persist();
        }
        if (SalesOrderLineEntity.findByKey("SO-MAT-SNAP", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-MAT-SNAP";
            line.salesOrderLineNo = 1;
            line.productCode = "OTHER-PRODUCT";
            line.orderQty = new BigDecimal("1");
            line.dueDate = planningStart.plusDays(30);
            line.priority = 5;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) != null) {
            WorkOrderEntity.delete("workOrderNo = ?1", WORK_ORDER_NO);
        }
        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, "RES-MAT-SNAP") == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = "RES-MAT-SNAP";
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
