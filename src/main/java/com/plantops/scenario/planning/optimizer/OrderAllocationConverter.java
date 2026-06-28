package com.plantops.scenario.planning.optimizer;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** {@link OrderAllocation} → DTO / {@link PlanningAssignment}（求解器无关）。 */
public final class OrderAllocationConverter {

    private static final LocalTime SHIFT_START = LocalTime.of(8, 0);
    private static final String SOURCE_EXTERNAL = "EXTERNAL";
    private static final String SOURCE_REPLENISH = "REPLENISH";

    private OrderAllocationConverter() {
    }

    public static List<MasterPlanAllocationDto> toAllocationDtos(List<OrderAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        List<MasterPlanAllocationDto> result = new ArrayList<>(allocations.size());
        for (OrderAllocation allocation : allocations) {
            if (allocation.getTimeSlot() == null) {
                continue;
            }
            result.add(toAllocationDto(allocation));
        }
        return result;
    }

    public static MasterPlanAllocationDto toAllocationDto(OrderAllocation allocation) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(allocation.getWorkOrderNo());
        BigDecimal qty = wo != null ? wo.quantity : BigDecimal.ZERO;
        String parent = wo != null ? wo.parentWorkOrderNo : allocation.getParentWorkOrderNo();
        String source = wo != null && wo.bomLevel == 0 ? SOURCE_EXTERNAL : SOURCE_REPLENISH;
        TimeSlot slot = allocation.getTimeSlot();
        LocalDate slotDate = slot.getDate();
        String shiftId = slot.getShiftId();
        int duration = allocation.getDurationMinutes();
        LocalDateTime startTs = shiftStart(slotDate, shiftId);
        LocalDateTime endTs = slot.isWeekly()
                ? shiftStart(slot.getPeriodEnd(), shiftId).plusHours(8)
                : startTs.plusMinutes(Math.max(1, duration));
        return new MasterPlanAllocationDto(
                allocation.getId(),
                allocation.getSegmentIndex(),
                allocation.getWorkOrderNo(),
                parent,
                source,
                allocation.getProductCode(),
                qty,
                allocation.getSalesOrderNo(),
                allocation.getSalesOrderLineNo(),
                allocation.getResourceId(),
                slot.getIndex(),
                slotDate,
                shiftId,
                startTs,
                endTs,
                duration);
    }

    public static List<PlanningAssignment> toPlanningAssignments(List<OrderAllocation> allocations) {
        return toAllocationDtos(allocations).stream()
                .map(OrderAllocationConverter::toPlanningAssignment)
                .toList();
    }

    public static List<PlanningAssignment> toPlanningAssignmentsFromDtos(List<MasterPlanAllocationDto> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        return allocations.stream().map(OrderAllocationConverter::toPlanningAssignment).toList();
    }

    public static PlanningAssignment toPlanningAssignment(MasterPlanAllocationDto allocation) {
        String operationId = allocation.allocationId();
        int dash = operationId != null ? operationId.lastIndexOf('-') : -1;
        if (dash > 0 && dash < operationId.length() - 1) {
            operationId = operationId.substring(0, dash);
        }
        return new PlanningAssignment(
                allocation.workOrderNo(),
                operationId,
                allocation.segmentIndex(),
                allocation.resourceId(),
                allocation.plannedStartTs(),
                allocation.plannedEndTs(),
                allocation.durationMinutes());
    }

    private static LocalDateTime shiftStart(LocalDate date, String shiftId) {
        if (date == null) {
            return null;
        }
        LocalTime time = SHIFT_START;
        if (shiftId != null && shiftId.contains("PM")) {
            time = LocalTime.of(13, 0);
        }
        return date.atTime(time);
    }
}
