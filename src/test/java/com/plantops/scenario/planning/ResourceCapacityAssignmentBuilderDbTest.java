package com.plantops.scenario.planning;

import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCapacityAssignmentBuilderDbTest {

    @Test
    void expandsMultipleResourcesPerOperation() throws Exception {
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480),
                new TimeSlot("RES-B-D0", 1, start, "DAY", "RES-B", 480));

        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = "WO-DB-SPLIT";
        wo.productCode = "FG-TEST";
        wo.quantity = BigDecimal.TEN;
        wo.bomLevel = 1;

        WorkOrderScheduleContext scheduleCtx = newScheduleContext(
                LocalDate.of(2026, 6, 30), 5, "SO-1", 1, false, true);

        List<ProductRoutingSteps.Operation> operations = List.of(
                new ProductRoutingSteps.Operation(
                        1,
                        "OP1",
                        List.of(
                                new ProductRoutingSteps.ResourceOption("RES-A", 1, BigDecimal.valueOf(60), 0),
                                new ProductRoutingSteps.ResourceOption("RES-B", 2, BigDecimal.valueOf(90), 0))));

        ResourceCapacityAssignmentBuilder.BuildResult result = ResourceCapacityAssignmentBuilder.buildForWorkOrder(
                wo,
                scheduleCtx,
                operations,
                slots,
                true,
                false,
                5,
                WorkOrderTimingBoundsContext.empty(),
                MasterPlanCapacityOverlay.empty());

        assertEquals(2, result.assignments().size());
        assertTrue(result.assignments().stream().anyMatch(a -> "RES-A".equals(a.getResourceId())));
        assertTrue(result.assignments().stream().anyMatch(a -> "RES-B".equals(a.getResourceId())));
        assertEquals(
                ResourceCapacityAssignment.operationKey("WO-DB-SPLIT", 1),
                result.assignments().get(0).getOperationKey());
        assertTrue(result.operationPrecedenceFacts().isEmpty());
    }

    private static WorkOrderScheduleContext newScheduleContext(
            LocalDate dueDate,
            int priority,
            String salesOrderNo,
            int salesOrderLineNo,
            boolean anyOrderLocked,
            boolean schedulable) throws Exception {
        Constructor<WorkOrderScheduleContext> ctor = WorkOrderScheduleContext.class.getDeclaredConstructor(
                LocalDate.class, int.class, String.class, int.class, boolean.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(dueDate, priority, salesOrderNo, salesOrderLineNo, anyOrderLocked, schedulable);
    }
}
