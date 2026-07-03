package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 将 {@link ResourceCapacityAssignment} 求解结果投影为 {@link MasterPlanAllocationDto}。 */
public final class ResourceCapacityResultProjector {

    private static final LocalTime SHIFT_START = LocalTime.of(8, 0);
    private static final String SOURCE_EXTERNAL = "EXTERNAL";
    private static final String SOURCE_REPLENISH = "REPLENISH";

    private ResourceCapacityResultProjector() {
    }

    public static List<MasterPlanAllocationDto> toAllocationDtos(List<ResourceCapacityAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<MasterPlanAllocationDto> result = new ArrayList<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getTimeSlot() == null || assignment.getAssignedMinutes() <= 0) {
                continue;
            }
            result.add(toAllocationDto(assignment));
        }
        return result;
    }

    public static MasterPlanAllocationDto toAllocationDto(ResourceCapacityAssignment assignment) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(assignment.getWorkOrderNo());
        BigDecimal qty = wo != null ? wo.quantity : assignment.getWorkOrderQuantity();
        String parent = wo != null ? wo.parentWorkOrderNo : assignment.getParentWorkOrderNo();
        String source = wo != null && wo.bomLevel == 0 ? SOURCE_EXTERNAL : SOURCE_REPLENISH;
        TimeSlot slot = assignment.getTimeSlot();
        LocalDate slotDate = slot.getDate();
        String shiftId = slot.getShiftId();
        int duration = assignment.getAssignedMinutes();
        LocalDateTime startTs = shiftStart(slotDate, shiftId);
        LocalDateTime endTs = slot.isWeekly()
                ? shiftStart(slot.getPeriodEnd(), shiftId).plusHours(8)
                : startTs.plusMinutes(Math.max(1, duration));
        return new MasterPlanAllocationDto(
                assignment.getId(),
                assignment.getDaySegmentIndex(),
                assignment.getWorkOrderNo(),
                parent,
                source,
                assignment.getProductCode(),
                qty,
                assignment.getSalesOrderNo(),
                assignment.getSalesOrderLineNo(),
                assignment.getResourceId(),
                slot.getIndex(),
                slotDate,
                shiftId,
                startTs,
                endTs,
                duration);
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
