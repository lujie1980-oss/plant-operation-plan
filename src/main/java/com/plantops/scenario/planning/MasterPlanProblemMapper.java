package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.AdjacentSlotPairFactory;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 将 {@link MasterPlanPlanningContext} 投影为 Timefold {@link MasterPlanSchedule}（只读视图 + 待优化变量）。
 */
@ApplicationScoped
public class MasterPlanProblemMapper {

    public MasterPlanSchedule toSchedule(MasterPlanPlanningContext context) {
        if (context == null) {
            return MasterPlanSchedule.empty();
        }
        return new MasterPlanSchedule(
                context.timeSlots(),
                context.orderAllocations(),
                context.planningStart(),
                new MasterPlanSettings(context.capacityStrategy()),
                context.materialFeasibility(),
                context.objectiveSettings(),
                AdjacentSlotPairFactory.fromSlots(context.timeSlots()),
                context.capacityOverlay(),
                context.bomDependencyEdges(),
                context.operationPrecedenceEdges(),
                context.workOrderTimingBounds());
    }
}
