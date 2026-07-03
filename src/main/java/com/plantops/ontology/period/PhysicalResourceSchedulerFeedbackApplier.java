package com.plantops.ontology.period;

import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.PhysicalResource;
import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.persistence.entity.ScheduleFeedbackScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** S05 冻结反馈 → ENT-PRP.schedulerFeedbackMinutes（ADR-17 P5 · RULE-SUP-05）。 */
public final class PhysicalResourceSchedulerFeedbackApplier {

    private PhysicalResourceSchedulerFeedbackApplier() {}

    public static void apply(
            Map<String, PhysicalResourcePeriod> prpByKey,
            PhysicalResourceRegistry registry,
            List<Period> periods,
            PeriodIndex periodIndex) {
        if (periods.isEmpty() || prpByKey.isEmpty()) {
            return;
        }
        LocalDateRange horizon = LocalDateRange.of(periods);
        apply(prpByKey, registry, periods, periodIndex, ScheduleFeedbackEntity.listFrozenUpTo(horizon.end()));
    }

    public static void apply(
            Map<String, PhysicalResourcePeriod> prpByKey,
            PhysicalResourceRegistry registry,
            List<Period> periods,
            PeriodIndex periodIndex,
            List<ScheduleFeedbackEntity> frozenFeedback) {
        if (periods.isEmpty() || prpByKey.isEmpty() || frozenFeedback == null || frozenFeedback.isEmpty()) {
            return;
        }
        LocalDateRange horizon = LocalDateRange.of(periods);
        for (ScheduleFeedbackEntity fb : frozenFeedback) {
            if (fb.slotDate == null || fb.durationMinutes <= 0) {
                continue;
            }
            if (fb.slotDate.isBefore(horizon.start()) || fb.slotDate.isAfter(horizon.end())) {
                continue;
            }
            if (!ScheduleFeedbackScope.FROZEN.name().equals(fb.scope)) {
                continue;
            }
            List<String> physicalIds = resolvePhysicalResourceIds(fb, registry);
            if (physicalIds.isEmpty()) {
                continue;
            }
            List<Period> leafPeriods = leafPeriodsForDate(periodIndex, fb.slotDate);
            if (leafPeriods.isEmpty()) {
                continue;
            }
            int parts = physicalIds.size() * leafPeriods.size();
            int partIndex = 0;
            for (Period period : leafPeriods) {
                for (String physicalId : physicalIds) {
                    PhysicalResourcePeriod prp =
                            prpByKey.get(OntologyIds.prpId(physicalId, period.getId()));
                    if (prp == null) {
                        partIndex++;
                        continue;
                    }
                    double share = splitMinutes(fb.durationMinutes, parts, partIndex++);
                    prp.setSchedulerFeedbackMinutes(prp.getSchedulerFeedbackMinutes() + share);
                }
            }
        }
    }

    static List<String> resolvePhysicalResourceIds(
            ScheduleFeedbackEntity fb, PhysicalResourceRegistry registry) {
        List<String> ids = new ArrayList<>();
        if (fb.physicalResourceId != null && !fb.physicalResourceId.isBlank()) {
            if (registry.isPhysicalResource(fb.physicalResourceId)) {
                ids.add(fb.physicalResourceId);
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        String fromOp = lookupLineIdFromOperation(fb);
        if (fromOp != null && registry.isPhysicalResource(fromOp)) {
            return List.of(fromOp);
        }
        if (fb.resourceId != null && registry.isPhysicalResource(fb.resourceId)) {
            return List.of(fb.resourceId);
        }
        if (fb.resourceId != null && registry.isStandardResource(fb.resourceId)) {
            return registry.physicalResourcesForStandard(fb.resourceId).stream()
                    .map(PhysicalResource::getId)
                    .toList();
        }
        return List.of();
    }

    private static String lookupLineIdFromOperation(ScheduleFeedbackEntity fb) {
        if (fb.detailScheduleVersionId == null || fb.operationId == null) {
            return null;
        }
        DetailScheduleOperationEntity op = DetailScheduleOperationEntity.find(
                        "workspaceId = ?1 and planVersionId = ?2 and operationId = ?3",
                        ScheduleFeedbackEntity.ws(),
                        fb.detailScheduleVersionId,
                        fb.operationId)
                .firstResult();
        return op != null ? op.lineId : null;
    }

    private static List<Period> leafPeriodsForDate(PeriodIndex periodIndex, java.time.LocalDate date) {
        if (!periodIndex.hasShiftPeriods()) {
            int seq = periodIndex.sequenceFor(date);
            Period period = periodIndex.periodAt(seq);
            if (period != null && period.isLeaf()) {
                return List.of(period);
            }
            return List.of();
        }
        return periodIndex.leafPeriods().stream()
                .filter(p -> !date.isBefore(p.getStartDate()) && !date.isAfter(p.getEndDate()))
                .toList();
    }

    private static double splitMinutes(int total, int parts, int index) {
        if (parts <= 1) {
            return total;
        }
        int base = total / parts;
        int remainder = total % parts;
        return base + (index < remainder ? 1 : 0);
    }

    private record LocalDateRange(java.time.LocalDate start, java.time.LocalDate end) {
        static LocalDateRange of(List<Period> periods) {
            return new LocalDateRange(
                    periods.get(0).getStartDate(), periods.get(periods.size() - 1).getEndDate());
        }
    }
}
