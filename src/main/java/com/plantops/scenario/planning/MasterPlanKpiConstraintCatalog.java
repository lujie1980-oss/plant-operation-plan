package com.plantops.scenario.planning;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Timefold master-plan constraint names to §15 KPI IDs and TOT aggregation domains.
 */
public final class MasterPlanKpiConstraintCatalog {

    public enum Layer {
        SCORING,
        CONSTRAINT
    }

    public record Entry(String kpiId, String displayName, String domain, Layer layer) {}

    private static final Map<String, Entry> BY_CONSTRAINT = Map.ofEntries(
            Map.entry("Material feasible on slot date",
                    new Entry("KPI-MP-C01", "缺失上游供应", "material", Layer.CONSTRAINT)),
            Map.entry("Resource must match slot",
                    new Entry("KPI-MP-C09", "缺失产能消耗", "capacity", Layer.CONSTRAINT)),
            Map.entry("Prefer higher priority resource",
                    new Entry("KPI-MP-S07", "供应偏好得分", "preference", Layer.SCORING)),
            Map.entry("Not before earliest feasible start",
                    new Entry("KPI-MP-C03", "上游供应延迟", "supply", Layer.CONSTRAINT)),
            Map.entry("Upstream before parent work order",
                    new Entry("KPI-MP-C03", "上游供应延迟", "supply", Layer.CONSTRAINT)),
            Map.entry("Operation serial precedence",
                    new Entry("KPI-MP-C10", "产能消耗分散", "capacity", Layer.CONSTRAINT)),
            Map.entry("Parallel operations same start slot",
                    new Entry("KPI-MP-C10", "产能消耗分散", "capacity", Layer.CONSTRAINT)),
            Map.entry("Slot capacity",
                    new Entry("KPI-MP-C07", "资源产能过载", "capacity", Layer.CONSTRAINT)),
            Map.entry("Segment order across days",
                    new Entry("KPI-MP-S05", "在制品持有得分", "capacity", Layer.SCORING)),
            Map.entry("Locked orders prefer earlier",
                    new Entry("KPI-MP-S07", "供应偏好得分", "preference", Layer.SCORING)),
            Map.entry("Minimize lateness",
                    new Entry("KPI-MP-S01", "交付性能得分", "delivery", Layer.SCORING)),
            Map.entry("Earlier slot for high priority",
                    new Entry("KPI-MP-S02", "交付履约得分", "delivery", Layer.SCORING)),
            Map.entry("Balance adjacent slot loading",
                    new Entry("KPI-MP-S06", "资源利用率得分", "capacity", Layer.SCORING)),
            Map.entry("Balance adjacent slot loading empty later",
                    new Entry("KPI-MP-S06", "资源利用率得分", "capacity", Layer.SCORING)),
            Map.entry("Balance adjacent slot loading empty earlier",
                    new Entry("KPI-MP-S06", "资源利用率得分", "capacity", Layer.SCORING)),
            Map.entry("Minimize active slot count",
                    new Entry("KPI-MP-S06", "资源利用率得分", "capacity", Layer.SCORING)),
            Map.entry("Minimize unused capacity in active slots",
                    new Entry("KPI-MP-S06", "资源利用率得分", "capacity", Layer.SCORING)),
            Map.entry("Minimize slot product changeover",
                    new Entry("KPI-MP-S05", "在制品持有得分", "capacity", Layer.SCORING)));

    private MasterPlanKpiConstraintCatalog() {}

    public static Optional<Entry> resolve(String constraintId) {
        if (constraintId == null || constraintId.isBlank()) {
            return Optional.empty();
        }
        Entry direct = BY_CONSTRAINT.get(constraintId.trim());
        if (direct != null) {
            return Optional.of(direct);
        }
        String normalized = constraintId.trim().toLowerCase(Locale.ROOT);
        return BY_CONSTRAINT.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(normalized))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
