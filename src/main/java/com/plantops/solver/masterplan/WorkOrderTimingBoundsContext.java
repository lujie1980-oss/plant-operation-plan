package com.plantops.solver.masterplan;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 主计划求解前预计算的工单「最早可行开始」下界（与 {@link com.plantops.scenario.WorkOrderTimingService} 同源）。
 */
public record WorkOrderTimingBoundsContext(Map<String, LocalDateTime> earliestStartByWorkOrder) {

    public static WorkOrderTimingBoundsContext empty() {
        return new WorkOrderTimingBoundsContext(Map.of());
    }

    public LocalDateTime earliestStart(String workOrderNo) {
        if (workOrderNo == null || earliestStartByWorkOrder == null) {
            return null;
        }
        return earliestStartByWorkOrder.get(workOrderNo);
    }

    public boolean slotAllowed(String workOrderNo, TimeSlot slot) {
        LocalDateTime earliest = earliestStart(workOrderNo);
        if (earliest == null || slot == null) {
            return true;
        }
        return !MasterPlanSlotTimes.startsBefore(slot, earliest);
    }

    public boolean violatesEarliestStart(String workOrderNo, TimeSlot slot) {
        LocalDateTime earliest = earliestStart(workOrderNo);
        if (earliest == null || slot == null) {
            return false;
        }
        return MasterPlanSlotTimes.startsBefore(slot, earliest);
    }

    public int violationWeight(String workOrderNo, TimeSlot slot) {
        return MasterPlanSlotTimes.violationMinutes(slot, earliestStart(workOrderNo));
    }

    /** 早于最早可行开始的天数（向上取整），用于软约束按天计成本。 */
    public int violationDays(String workOrderNo, TimeSlot slot) {
        int minutes = violationWeight(workOrderNo, slot);
        if (minutes <= 0) {
            return 0;
        }
        return (int) Math.ceil(minutes / 1440.0);
    }
}
