package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.HardSoftScore;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MasterPlanService;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class OntologyDirectSolveParityTest {

    private static final String SALES_ORDER_NO = "SO-OTD-PARITY";
    private static final String WORK_ORDER_NO = "WO-OTD-PARITY";
    private static final String PRODUCT_CODE = "FG-OTD-PARITY-100";

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyToMasterPlanScheduleMapper scheduleMapper;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Test
    @TestTransaction
    void entityAndDirectPathsProduceEqualHardScore() throws ExecutionException, InterruptedException {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(null);
        MasterPlanCapacityOverlay overlay = MasterPlanCapacityOverlay.empty();
        MasterPlanPlanningContext entityContext = masterPlanService.buildPlanningContext(resolved, overlay);
        MasterPlanService.InMemorySolveResult entitySolve = masterPlanService.solveInMemory(entityContext);

        OntologyGraph graph = ontologyLoader.loadForWorkspace(planningStart);
        MasterPlanSolveProfile profile = new MasterPlanSolveProfile(
                planningStart,
                resolved.capacityStrategy(),
                resolved.objectiveSettings(),
                overlay,
                resolved.id());
        MasterPlanSchedule directProblem = scheduleMapper.toSchedule(graph, profile);
        MasterPlanService.InMemorySolveResult directSolve = masterPlanService.solveInMemory(directProblem);

        long entityHard = hardScore(entitySolve.solution());
        long directHard = hardScore(directSolve.solution());
        assertEquals(entityHard, directHard, "hard score should match between entity and direct paths");

        double jaccard = jaccard(
                allocationKeys(entitySolve.solution().getOrderAllocations()),
                allocationKeys(directSolve.solution().getOrderAllocations()));
        assertFalse(jaccard < 0.95, "allocation key Jaccard should be >= 0.95, was " + jaccard);
    }

    private static long hardScore(MasterPlanSchedule schedule) {
        HardSoftScore score = schedule.getScore();
        return score != null ? score.hardScore() : 0L;
    }

    private static Set<String> allocationKeys(List<OrderAllocation> allocations) {
        return allocations.stream()
                .map(OrderAllocation::getId)
                .collect(Collectors.toCollection(HashSet::new));
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
            workOrder.resourceId = "RES-OTD-PARITY";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting(PRODUCT_CODE, "RES-OTD-PARITY", "RUN", 1);
    }

    private static void ensureRouting(
            String productCode,
            String resourceId,
            String operationName,
            int sequenceNo) {
        if (ProductResourceEntity.findByProductAndResource(productCode, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = productCode;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.setupTimeMinutes = 0;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
