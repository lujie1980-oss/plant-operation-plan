package com.plantops.ontology.scheduling;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodGranularity;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.scenario.SrpLeafCapacitySupport;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 由 leaf ENT-PER + ENT-SRP 派生求解器 {@link TimeSlot}（ADR-16 · TODO-23 S4/S5）。
 * <p>
 * ENT-SS 已废止：不再装载 {@code OntologyGraph.schedulingSlotsOrdered}；按需 DERIVE。
 */
public final class PeriodTimeSlotDeriver {

    private PeriodTimeSlotDeriver() {}

    public static List<TimeSlot> deriveTimeSlots(OntologyGraph graph, Set<String> resourceIds) {
        if (graph == null) {
            return List.of();
        }
        return deriveTimeSlots(graph.periodsOrdered(), graph.srpById(), resourceIds);
    }

    public static List<TimeSlot> deriveTimeSlots(
            List<Period> periodsOrdered,
            Map<String, StandardResourcePeriod> srpById,
            Set<String> resourceIds) {
        if (periodsOrdered == null || periodsOrdered.isEmpty()) {
            return List.of();
        }
        Set<String> resources = resourceIds == null || resourceIds.isEmpty()
                ? new TreeSet<>(srpById.values().stream()
                        .map(StandardResourcePeriod::getStandardResourceId)
                        .toList())
                : new TreeSet<>(resourceIds);
        if (resources.isEmpty()) {
            return List.of();
        }
        LocalDate planningStart = periodsOrdered.get(0).getStartDate();
        List<Period> leafPeriods = periodsOrdered.stream().filter(Period::isLeaf).toList();
        List<TimeSlot> slots = new ArrayList<>();
        int index = 0;
        for (String resourceId : resources) {
            int weekOrdinal = 0;
            for (Period period : leafPeriods) {
                StandardResourcePeriod srp =
                        srpById.get(OntologyIds.srpId(resourceId, period.getSequenceNr()));
                int capacity = srp != null
                        ? (int) Math.round(Math.max(0, srp.getAvailableCapacity()))
                        : 0;
                if (period.getGranularity() == PeriodGranularity.WEEK) {
                    slots.add(toTimeSlot(
                            resourceId,
                            period,
                            OntologyIds.schedulingSlotWeekId(resourceId, weekOrdinal++),
                            index++,
                            capacity));
                } else {
                    slots.add(toTimeSlot(
                            resourceId,
                            period,
                            slotId(resourceId, period, planningStart),
                            index++,
                            capacity));
                }
            }
        }
        return List.copyOf(slots);
    }

    static String slotId(String resourceId, Period period, LocalDate planningStart) {
        if (period.getGranularity() == PeriodGranularity.SHIFT) {
            return OntologyIds.schedulingSlotShiftId(resourceId, period.getSequenceNr());
        }
        if (period.getGranularity() == PeriodGranularity.DAY
                && period.getStartDate().equals(period.getEndDate())) {
            int dayOffset = (int) ChronoUnit.DAYS.between(planningStart, period.getStartDate());
            return OntologyIds.schedulingSlotDayId(resourceId, dayOffset);
        }
        return OntologyIds.schedulingSlotPeriodId(resourceId, period.getSequenceNr());
    }

    private static TimeSlot toTimeSlot(
            String resourceId,
            Period period,
            String slotId,
            int index,
            int capacityMinutes) {
        TimeslotGranularity granularity = period.getGranularity() == PeriodGranularity.WEEK
                ? TimeslotGranularity.WEEK
                : TimeslotGranularity.DAY;
        String shiftId = SrpLeafCapacitySupport.shiftIdFor(period);
        if (period.getGranularity() == PeriodGranularity.WEEK) {
            shiftId = "WEEK";
        }
        return new TimeSlot(
                slotId,
                index,
                period.getStartDate(),
                period.getEndDate(),
                granularity,
                shiftId,
                resourceId,
                capacityMinutes);
    }
}
