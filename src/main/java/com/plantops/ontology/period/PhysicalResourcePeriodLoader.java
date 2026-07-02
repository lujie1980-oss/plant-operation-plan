package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.PhysicalResource;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.WorkOrderEntity;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 日历 → ENT-PRP → rollup ENT-SRP（ADR-17 · TODO-24 P2~P3）。 */
public final class PhysicalResourcePeriodLoader {

    private PhysicalResourcePeriodLoader() {}

    public static void load(OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
        Set<String> standardResourceIds = collectStandardResourceIds();
        PhysicalResourceRegistry registry = PhysicalResourceRegistry.forWorkspace(standardResourceIds);
        Map<String, PhysicalResourcePeriod> prpByKey = new LinkedHashMap<>();

        for (PhysicalResource pr : registry.all()) {
            for (Period period : periods) {
                PhysicalResourcePeriod prp = new PhysicalResourcePeriod(
                        OntologyIds.prpId(pr.getId(), period.getId()),
                        pr.getId(),
                        pr.getStandardResourceId(),
                        period.getId());
                prpByKey.put(prp.getId(), prp);
                builder.physicalResourcePeriod(prp);
            }
        }

        applyCalendar(prpByKey, registry, periods, periodIndex);
        PhysicalResourcePeriodRollup.rollupParentCapacities(prpByKey, periods, periodIndex);
        prpByKey.values().forEach(PhysicalResourcePeriod::recalculateCapacityFields);

        StandardResourcePeriodAggregator.aggregate(builder, prpByKey, standardResourceIds, periods);
    }

    static Set<String> collectStandardResourceIds() {
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
            Map<String, PhysicalResourcePeriod> prpByKey,
            PhysicalResourceRegistry registry,
            List<Period> periods,
            PeriodIndex periodIndex) {
        if (periods.isEmpty()) {
            return;
        }
        LocalDateRange horizon = LocalDateRange.of(periods);
        Set<String> srDateShiftCovered = new HashSet<>();

        for (ResourceCalendarEntity cal : ResourceCalendarEntity.listInWorkspace()) {
            if (cal.resourceId == null || cal.calendarDate == null) {
                continue;
            }
            if (cal.calendarDate.isBefore(horizon.start()) || cal.calendarDate.isAfter(horizon.end())) {
                continue;
            }
            if (!registry.isPhysicalResource(cal.resourceId)) {
                continue;
            }
            String srId = registry.standardResourceForPhysical(cal.resourceId);
            if (srId != null) {
                srDateShiftCovered.add(coverageKey(srId, cal));
            }
            applyCalendarRow(prpByKey, registry, periodIndex, cal, List.of(cal.resourceId));
        }

        for (ResourceCalendarEntity cal : ResourceCalendarEntity.listInWorkspace()) {
            if (cal.resourceId == null || cal.calendarDate == null) {
                continue;
            }
            if (cal.calendarDate.isBefore(horizon.start()) || cal.calendarDate.isAfter(horizon.end())) {
                continue;
            }
            if (registry.isPhysicalResource(cal.resourceId)) {
                continue;
            }
            if (!registry.isStandardResource(cal.resourceId)) {
                continue;
            }
            if (srDateShiftCovered.contains(coverageKey(cal.resourceId, cal))) {
                continue;
            }
            List<PhysicalResource> targets = registry.physicalResourcesForStandard(cal.resourceId);
            if (targets.isEmpty()) {
                continue;
            }
            List<String> physicalIds =
                    targets.stream().map(PhysicalResource::getId).toList();
            applyCalendarRow(prpByKey, registry, periodIndex, cal, physicalIds);
        }
    }

    private static String coverageKey(String standardResourceId, ResourceCalendarEntity cal) {
        return standardResourceId + "|" + cal.calendarDate + "|" + normalizeShiftId(cal.shiftId);
    }

    private static void applyCalendarRow(
            Map<String, PhysicalResourcePeriod> prpByKey,
            PhysicalResourceRegistry registry,
            PeriodIndex periodIndex,
            ResourceCalendarEntity cal,
            List<String> physicalResourceIds) {
        if (physicalResourceIds.isEmpty()) {
            return;
        }
        int seq;
        Period period;
        if (periodIndex.hasShiftPeriods()) {
            String effectiveShiftId = normalizeShiftId(cal.shiftId);
            seq = periodIndex.sequenceFor(cal.calendarDate, effectiveShiftId);
            period = periodIndex.periodAt(seq);
            if (period == null || !period.isLeaf() || period.getGranularity() != PeriodGranularity.SHIFT) {
                return;
            }
        } else {
            seq = periodIndex.sequenceFor(cal.calendarDate);
            period = periodIndex.periodAt(seq);
            if (period != null && !period.isLeaf()) {
                return;
            }
        }
        if (period == null) {
            return;
        }

        int parts = physicalResourceIds.size();
        for (int i = 0; i < parts; i++) {
            String physicalResourceId = physicalResourceIds.get(i);
            PhysicalResourcePeriod prp = prpByKey.get(OntologyIds.prpId(physicalResourceId, period.getId()));
            if (prp == null) {
                continue;
            }
            addCalendarMinutes(
                    prp,
                    splitMinutes(cal.availableCapacityMinutes, parts, i),
                    splitMinutes(cal.unavailableCapacityMinutes, parts, i));
        }
    }

    private static void addCalendarMinutes(
            PhysicalResourcePeriod prp, double availableMinutes, double unavailableMinutes) {
        prp.setTotalCapacity(prp.getTotalCapacity() + availableMinutes + unavailableMinutes);
        prp.setCalendarDowntime(prp.getCalendarDowntime() + unavailableMinutes);
    }

    private static double splitMinutes(int total, int parts, int index) {
        if (parts <= 1) {
            return total;
        }
        int base = total / parts;
        int remainder = total % parts;
        return base + (index < remainder ? 1 : 0);
    }

    static String normalizeShiftId(String shiftId) {
        return StandardResourcePeriodLoader.normalizeShiftId(shiftId);
    }

    private record LocalDateRange(java.time.LocalDate start, java.time.LocalDate end) {
        static LocalDateRange of(List<Period> periods) {
            java.time.LocalDate start = periods.get(0).getStartDate();
            java.time.LocalDate end = periods.get(periods.size() - 1).getEndDate();
            return new LocalDateRange(start, end);
        }
    }
}
