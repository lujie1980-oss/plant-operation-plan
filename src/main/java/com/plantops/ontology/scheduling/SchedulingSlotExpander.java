package com.plantops.ontology.scheduling;

import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 委托 {@link TimeslotHorizonService} 生成槽位，再投影为本体 {@link SchedulingSlot}（禁止第三套时栅算法）。
 */
@ApplicationScoped
public class SchedulingSlotExpander {

    private final TimeslotHorizonService timeslotHorizonService;

    @Inject
    public SchedulingSlotExpander(TimeslotHorizonService timeslotHorizonService) {
        this.timeslotHorizonService = timeslotHorizonService;
    }

    public List<SchedulingSlot> expand(LocalDate planningStart, Set<String> resourceIds) {
        if (planningStart == null || resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        List<TimeSlot> horizonSlots = timeslotHorizonService.buildSlots(planningStart, resourceIds);
        return horizonSlots.stream().map(SchedulingSlot::fromTimeSlot).toList();
    }
}
