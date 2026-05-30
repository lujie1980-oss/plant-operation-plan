package com.plantops.solver.masterplan;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排程反馈对主计划产能的叠加：截止日及之前的槽位固定负荷 + 可重排起点。
 */
public class MasterPlanCapacityOverlay {

    private final LocalDate feedbackCutoff;
    private final Map<String, Integer> fixedMinutesBySlotId;

    public MasterPlanCapacityOverlay(LocalDate feedbackCutoff, Map<String, Integer> fixedMinutesBySlotId) {
        this.feedbackCutoff = feedbackCutoff;
        this.fixedMinutesBySlotId = fixedMinutesBySlotId != null
                ? Map.copyOf(fixedMinutesBySlotId)
                : Map.of();
    }

    public static MasterPlanCapacityOverlay empty() {
        return new MasterPlanCapacityOverlay(null, Map.of());
    }

    public static MasterPlanCapacityOverlay fromFixedLoads(List<SlotFixedLoad> loads, LocalDate cutoff) {
        Map<String, Integer> map = new HashMap<>();
        if (loads != null) {
            for (SlotFixedLoad load : loads) {
                map.merge(load.getSlotId(), load.getFixedMinutes(), Integer::sum);
            }
        }
        return new MasterPlanCapacityOverlay(cutoff, map);
    }

    public LocalDate feedbackCutoff() {
        return feedbackCutoff;
    }

    public int fixedMinutesForSlot(String slotId) {
        return fixedMinutesBySlotId.getOrDefault(slotId, 0);
    }

    public Map<String, Integer> fixedMinutesBySlotId() {
        return Collections.unmodifiableMap(fixedMinutesBySlotId);
    }

    public boolean hasCutoff() {
        return feedbackCutoff != null;
    }

    /** 槽位起始日在 cutoff 之后才可分配新负荷 */
    public boolean isSlotEligibleForReplan(TimeSlot slot) {
        if (!hasCutoff() || slot == null) {
            return true;
        }
        return slot.getDate().isAfter(feedbackCutoff);
    }
}
