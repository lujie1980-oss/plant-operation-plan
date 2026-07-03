package com.plantops.scenario;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.StandardResourcePeriod;

import java.util.ArrayList;
import java.util.List;

/** leaf ENT-SRP 产能视图与 RULE-MP-07 超载判定（ADR-16 · TODO-23 S3）。 */
public final class SrpLeafCapacitySupport {

    private static final String SHIFT_DAY = "DAY";

    private SrpLeafCapacitySupport() {}

    public static List<StandardResourcePeriod> leafSrps(OntologyGraph graph) {
        if (graph == null) {
            return List.of();
        }
        List<StandardResourcePeriod> leaf = new ArrayList<>();
        for (StandardResourcePeriod srp : graph.srpById().values()) {
            Period period = periodFor(graph, srp.getPeriodId());
            if (period != null && period.isLeaf()) {
                leaf.add(srp);
            }
        }
        return List.copyOf(leaf);
    }

    public static Period periodFor(OntologyGraph graph, String periodId) {
        return StandardResourcePeriodGanttService.periodFor(graph, periodId);
    }

    public static String shiftIdFor(Period period) {
        if (period == null || period.getShiftId() == null || period.getShiftId().isBlank()) {
            return SHIFT_DAY;
        }
        return period.getShiftId();
    }

    public static int utilizationPct(StandardResourcePeriod srp) {
        int available = (int) Math.round(Math.max(0, srp.getAvailableCapacity()));
        int reserved = (int) Math.round(Math.max(0, srp.getReservedCapacity()));
        if (available <= 0) {
            return reserved > 0 ? 100 : 0;
        }
        return (int) (reserved * 100L / available);
    }

    /** RULE-MP-07：reserved 超出 available。 */
    public static boolean overloadedByRule(StandardResourcePeriod srp) {
        srp.recalculateCapacityFields();
        return srp.getOverloadCapacity() > 0;
    }

    /** SCN-03a 展示阈值（KPI-MP-B05 展示侧可与 RULE 分离）。 */
    public static boolean overloadedByThreshold(StandardResourcePeriod srp, int thresholdPct) {
        return utilizationPct(srp) >= thresholdPct;
    }
}
