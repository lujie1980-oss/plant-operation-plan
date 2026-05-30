package com.plantops.scenario.planning;

import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.MasterPlanService;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已持久化主计划分配恢复 {@link MasterPlanSchedule}，供 {@code SolutionManager.explain()} 使用（不重跑求解）。
 */
@ApplicationScoped
public class MasterPlanSolutionRestorer {

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MasterPlanProblemMapper problemMapper;

    @Inject
    SampleDataLoader sampleDataLoader;

    public MasterPlanSchedule restore(String planVersionId) {
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
        if (version == null || !"MASTER_PLAN".equals(version.planType)) {
            throw new NotFoundException("Master plan version not found: " + planVersionId);
        }
        sampleDataLoader.extendCalendarsToHorizon();
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                version.strategyId != null && !version.strategyId.isBlank() ? version.strategyId : null);
        MasterPlanCapacityOverlay overlay = overlayForVersion(version);
        MasterPlanPlanningContext context = masterPlanService.buildPlanningContext(resolved, overlay);
        MasterPlanSchedule schedule = problemMapper.toSchedule(context);
        applyPersistedAssignments(schedule, planVersionId);
        return schedule;
    }

    static void applyPersistedAssignments(MasterPlanSchedule schedule, String planVersionId) {
        List<MasterPlanAllocationEntity> rows = MasterPlanAllocationEntity
                .find("planVersionId = ?1", planVersionId)
                .list();
        Map<String, MasterPlanAllocationEntity> byAllocationId = new HashMap<>();
        for (MasterPlanAllocationEntity row : rows) {
            if (row.allocationId == null || row.allocationId.startsWith("FB-")) {
                continue;
            }
            byAllocationId.put(row.allocationId, row);
        }
        List<TimeSlot> slots = schedule.getTimeSlotRange();
        for (OrderAllocation allocation : schedule.getOrderAllocations()) {
            MasterPlanAllocationEntity row = byAllocationId.get(allocation.getId());
            if (row == null) {
                continue;
            }
            TimeSlot slot = findMatchingSlot(slots, row);
            if (slot != null) {
                allocation.setTimeSlot(slot);
            }
        }
    }

    static TimeSlot findMatchingSlot(List<TimeSlot> slots, MasterPlanAllocationEntity row) {
        if (row.resourceId != null && row.slotDate != null) {
            for (TimeSlot slot : slots) {
                if (row.resourceId.equals(slot.getResourceId())
                        && row.slotDate.equals(slot.getDate())
                        && (row.shiftId == null || row.shiftId.isBlank()
                                || row.shiftId.equals(slot.getShiftId()))) {
                    return slot;
                }
            }
        }
        for (TimeSlot slot : slots) {
            if (slot.getIndex() == row.slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private MasterPlanCapacityOverlay overlayForVersion(PlanVersionEntity version) {
        if (version.sourceDetailScheduleVersionId == null
                || version.sourceDetailScheduleVersionId.isBlank()) {
            return MasterPlanCapacityOverlay.empty();
        }
        List<ScheduleFeedbackEntity> feedback = ScheduleFeedbackEntity.listForDetailSchedule(
                version.sourceDetailScheduleVersionId);
        if (feedback.isEmpty()) {
            return MasterPlanCapacityOverlay.empty();
        }
        LocalDate cutoff = feedback.stream()
                .map(f -> f.slotDate)
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        return masterPlanService.buildFeedbackOverlay(cutoff);
    }
}
