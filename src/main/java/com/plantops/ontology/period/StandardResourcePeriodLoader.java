package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.WorkOrderEntity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从资源日历装载 ENT-SRP（ADR-16 · TODO-23 S2）。 */
public final class StandardResourcePeriodLoader {

    private StandardResourcePeriodLoader() {}

    public static void load(OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
        Set<String> resourceIds = collectResourceIds();
        Map<String, StandardResourcePeriod> srpByKey = new LinkedHashMap<>();
        for (String resourceId : resourceIds) {
            for (Period period : periods) {
                StandardResourcePeriod srp = new StandardResourcePeriod(
                        OntologyIds.srpId(resourceId, period.getSequenceNr()), resourceId, period.getId());
                srpByKey.put(srp.getId(), srp);
                builder.standardResourcePeriod(srp);
            }
        }
        applyCalendar(srpByKey, periods, periodIndex);
        StandardResourcePeriodRollup.rollupParentCapacities(srpByKey, periods, periodIndex);
        srpByKey.values().forEach(StandardResourcePeriod::recalculateCapacityFields);
    }

    private static Set<String> collectResourceIds() {
        Set<String> resourceIds = new LinkedHashSet<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.resourceId != null && !line.resourceId.isBlank()) {
                resourceIds.add(line.resourceId);
            }
        }
        for (ProductResourceEntity pr : ProductResourceEntity.listInWorkspace()) {
            if (pr.resourceId != null && !pr.resourceId.isBlank()) {
                resourceIds.add(pr.resourceId);
            }
        }
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (wo.resourceId != null && !wo.resourceId.isBlank()) {
                resourceIds.add(wo.resourceId);
            }
        }
        return resourceIds;
    }

    private static void applyCalendar(
            Map<String, StandardResourcePeriod> srpByKey,
            List<Period> periods,
            PeriodIndex periodIndex) {
        if (periods.isEmpty()) {
            return;
        }
        LocalDateRange horizon = LocalDateRange.of(periods);
        for (ResourceCalendarEntity cal : ResourceCalendarEntity.listInWorkspace()) {
            if (cal.resourceId == null || cal.calendarDate == null) {
                continue;
            }
            if (cal.calendarDate.isBefore(horizon.start()) || cal.calendarDate.isAfter(horizon.end())) {
                continue;
            }
            if (periodIndex.hasShiftPeriods()) {
                applyShiftCalendarRow(srpByKey, periodIndex, cal);
            } else {
                applyDayCalendarRow(srpByKey, periodIndex, cal);
            }
        }
    }

    private static void applyShiftCalendarRow(
            Map<String, StandardResourcePeriod> srpByKey,
            PeriodIndex periodIndex,
            ResourceCalendarEntity cal) {
        String effectiveShiftId = normalizeShiftId(cal.shiftId);
        int seq = periodIndex.sequenceFor(cal.calendarDate, effectiveShiftId);
        StandardResourcePeriod srp = srpByKey.get(OntologyIds.srpId(cal.resourceId, seq));
        if (srp == null) {
            return;
        }
        Period period = periodIndex.periodAt(seq);
        if (period == null || !period.isLeaf() || period.getGranularity() != PeriodGranularity.SHIFT) {
            return;
        }
        addCalendarMinutes(srp, cal);
    }

    private static void applyDayCalendarRow(
            Map<String, StandardResourcePeriod> srpByKey,
            PeriodIndex periodIndex,
            ResourceCalendarEntity cal) {
        int seq = periodIndex.sequenceFor(cal.calendarDate);
        StandardResourcePeriod srp = srpByKey.get(OntologyIds.srpId(cal.resourceId, seq));
        if (srp == null) {
            return;
        }
        Period period = periodIndex.periodAt(seq);
        if (period != null && !period.isLeaf()) {
            return;
        }
        addCalendarMinutes(srp, cal);
    }

    private static void addCalendarMinutes(StandardResourcePeriod srp, ResourceCalendarEntity cal) {
        srp.setTotalCapacity(srp.getTotalCapacity() + cal.availableCapacityMinutes + cal.unavailableCapacityMinutes);
        srp.setCalendarDowntime(srp.getCalendarDowntime() + cal.unavailableCapacityMinutes);
    }

    static String normalizeShiftId(String shiftId) {
        if (shiftId == null || shiftId.isBlank() || "DAY".equalsIgnoreCase(shiftId)) {
            return "S1";
        }
        return shiftId;
    }

    private record LocalDateRange(java.time.LocalDate start, java.time.LocalDate end) {
        static LocalDateRange of(List<Period> periods) {
            java.time.LocalDate start = periods.get(0).getStartDate();
            java.time.LocalDate end = periods.get(periods.size() - 1).getEndDate();
            return new LocalDateRange(start, end);
        }
    }
}
