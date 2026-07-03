package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;

import java.util.List;
import java.util.Map;

/** 从 PRP 聚合装载 ENT-SRP（ADR-17 · TODO-24 P3）。 */
public final class StandardResourcePeriodLoader {

    private StandardResourcePeriodLoader() {}

    public static void load(OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
        PhysicalResourcePeriodLoader.load(builder, periods, periodIndex);
        Map<String, StandardResourcePeriod> srpByKey = builder.srpByIdSnapshot();
        StandardResourcePeriodRollup.rollupParentCapacities(srpByKey, periods, periodIndex);
        srpByKey.values().forEach(StandardResourcePeriod::recalculateCapacityFields);
    }

    public static String normalizeShiftId(String shiftId) {
        if (shiftId == null || shiftId.isBlank() || "DAY".equalsIgnoreCase(shiftId)) {
            return "S1";
        }
        return shiftId;
    }
}
