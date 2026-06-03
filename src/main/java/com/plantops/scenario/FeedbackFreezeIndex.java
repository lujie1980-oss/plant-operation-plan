package com.plantops.scenario;

import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.persistence.entity.ScheduleFeedbackScope;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** 排程反馈冻结工序 → 锚点分钟快照。 */
public final class FeedbackFreezeIndex {

    private static final FeedbackFreezeIndex EMPTY = new FeedbackFreezeIndex(null, Map.of());

    private final LocalDate cutoff;
    private final Map<String, Integer> frozenStartMinuteByOperationId;

    public FeedbackFreezeIndex(LocalDate cutoff, Map<String, Integer> frozenStartMinuteByOperationId) {
        this.cutoff = cutoff;
        this.frozenStartMinuteByOperationId =
                frozenStartMinuteByOperationId != null ? frozenStartMinuteByOperationId : Map.of();
    }

    public static FeedbackFreezeIndex empty() {
        return EMPTY;
    }

    public static FeedbackFreezeIndex fromWorkspace(LocalDate sessionAnchor, LocalDate cutoff) {
        if (cutoff == null) {
            return empty();
        }
        LocalDate anchor = sessionAnchor != null ? sessionAnchor : LocalDate.now();
        Map<String, Integer> map = new HashMap<>();
        for (ScheduleFeedbackEntity fb : ScheduleFeedbackEntity.listFrozenUpTo(cutoff)) {
            if (fb.operationId == null || fb.plannedStart == null) {
                continue;
            }
            LocalDate fbAnchor = fb.planningAnchorDate != null ? fb.planningAnchorDate : anchor;
            int minute = (int) Duration.between(fbAnchor.atStartOfDay(), fb.plannedStart).toMinutes();
            if (fbAnchor.equals(anchor)) {
                map.put(fb.operationId, minute);
            } else {
                int dayShift = (int) java.time.temporal.ChronoUnit.DAYS.between(fbAnchor, anchor)
                        * com.plantops.solver.detailschedule.ScheduleTimingUtil.MINUTES_PER_DAY;
                map.put(fb.operationId, minute + dayShift);
            }
        }
        return new FeedbackFreezeIndex(cutoff, Map.copyOf(map));
    }

    public LocalDate cutoff() {
        return cutoff;
    }

    public Integer frozenStartMinute(String operationId) {
        return operationId != null ? frozenStartMinuteByOperationId.get(operationId) : null;
    }

    public boolean isFrozen(String operationId) {
        return frozenStartMinute(operationId) != null;
    }
}
