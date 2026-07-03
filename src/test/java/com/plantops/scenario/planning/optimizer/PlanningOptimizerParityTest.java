package com.plantops.scenario.planning.optimizer;

import com.plantops.testsupport.SpecRef;
import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.ontology.OntologyIds;
import com.plantops.workspace.WorkspaceResolver;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MasterPlanService;
import com.plantops.scenario.planning.MasterPlanPlanningContext;
import com.plantops.scenario.planning.optimizer.ortools.OrtoolsPlanningOptimizer;
import com.plantops.scenario.planning.optimizer.timefold.TimefoldPlanningOptimizer;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timefold vs OR-Tools 对等性（D40）：hard 可行 + assignment 键 Jaccard ≥ 0.95。
 */
@QuarkusTest
@SpecRef("AC-05")
class PlanningOptimizerParityTest {

    private static final String SALES_ORDER_NO = "SO-OPT-PARITY";
    private static final String WORK_ORDER_NO = "WO-OPT-PARITY";
    private static final String PRODUCT_CODE = "FG-OPT-PARITY-100";
    private static final String RESOURCE_ID = "RES-OPT-PARITY";

    @Inject
    PlanningOptimizerRegistry optimizerRegistry;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Test
    @TestTransaction
    void timefoldAndOrtoolsProduceHardFeasibleAssignmentsWithHighKeyOverlap() throws Exception {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        authoritativeOntologyGraph.invalidateWorkspace(WorkspaceResolver.currentWorkspaceId());

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(null);
        MasterPlanPlanningContext context = masterPlanService.buildPlanningContext(
                resolved, MasterPlanCapacityOverlay.empty());
        assertFalse(
                context.orderAllocations().isEmpty(),
                "PATH-ONT should produce order allocation candidates");
        PlanningProblem problem = PlanningProblem.forContext(
                context,
                "optimizer-parity",
                Set.of(WORK_ORDER_NO));

        OptimizerResult timefold = optimizerRegistry.require(TimefoldPlanningOptimizer.ENGINE_ID).optimize(problem);
        OptimizerResult ortools = optimizerRegistry.require(OrtoolsPlanningOptimizer.ENGINE_ID).optimize(problem);

        assertHardFeasible(timefold, "timefold");
        assertHardFeasible(ortools, "ortools");

        double jaccard = jaccard(
                assignmentKeys(timefold.persistAllocations()),
                assignmentKeys(ortools.persistAllocations()));
        assertFalse(jaccard < 0.95, "assignment key Jaccard should be >= 0.95, was " + jaccard);
    }

    private static void assertHardFeasible(OptimizerResult result, String engine) {
        assertTrue(result != null && !result.persistAllocations().isEmpty(), engine + " should assign allocations");
        assertTrue(
                result.scoreSummary() != null && result.scoreSummary().startsWith("0hard"),
                engine + " hard score should be feasible, was " + result.scoreSummary());
    }

    static Set<String> assignmentKeys(List<MasterPlanAllocationDto> allocations) {
        return allocations.stream().map(PlanningOptimizerParityTest::assignmentKey).collect(Collectors.toCollection(HashSet::new));
    }

    static String assignmentKey(MasterPlanAllocationDto dto) {
        return dto.workOrderNo()
                + "|"
                + dto.allocationId()
                + "|"
                + dto.segmentIndex()
                + "|"
                + dto.resourceId()
                + "|"
                + dto.plannedStartTs();
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private void ensureFixture(LocalDate planningStart) {
        if (SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = SALES_ORDER_NO;
            line.salesOrderLineNo = 1;
            line.productCode = PRODUCT_CODE;
            line.orderQty = new BigDecimal("40");
            line.dueDate = planningStart.plusDays(10);
            line.priority = 3;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("40");
            workOrder.needDate = planningStart.plusDays(10);
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, RESOURCE_ID) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = RESOURCE_ID;
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.setupTimeMinutes = 0;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }
        if (InventoryEntity.find(
                        "workspaceId = ?1 and productCode = ?2",
                        WorkspaceResolver.currentWorkspaceId(),
                        PRODUCT_CODE)
                .firstResult() == null) {
            InventoryEntity inventory = new InventoryEntity();
            inventory.stockingPointCode = OntologyIds.DEFAULT_FG;
            inventory.productCode = PRODUCT_CODE;
            inventory.onhandQty = new BigDecimal("10");
            inventory.reservedQty = BigDecimal.ZERO;
            inventory.qualityHoldQty = BigDecimal.ZERO;
            inventory.stampWorkspace();
            inventory.persist();
        }
    }
}
