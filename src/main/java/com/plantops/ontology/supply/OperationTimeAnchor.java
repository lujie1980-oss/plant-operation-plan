package com.plantops.ontology.supply;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 与 {@link com.plantops.scenario.WorkOrderTimingService} 一致的日界锚点。 */
public final class OperationTimeAnchor {

    public static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    public static final LocalTime WORKDAY_END = LocalTime.of(17, 0);

    private OperationTimeAnchor() {
    }

    public static LocalDateTime horizonStart(LocalDate planningStart) {
        LocalDate date = planningStart != null ? planningStart : LocalDate.now();
        return date.atTime(WORKDAY_START);
    }

    public static LocalDateTime needDateEnd(LocalDate needDate, LocalDate fallback) {
        LocalDate date = needDate != null ? needDate : fallback;
        return date.atTime(WORKDAY_END);
    }

    public static LocalDateTime plusElapsed(LocalDateTime base, long elapsedSeconds) {
        if (base == null) {
            return null;
        }
        if (elapsedSeconds <= 0) {
            return base;
        }
        return base.plusSeconds(elapsedSeconds);
    }

    public static LocalDateTime minusElapsed(LocalDateTime base, long elapsedSeconds) {
        if (base == null) {
            return null;
        }
        if (elapsedSeconds <= 0) {
            return base;
        }
        return base.minusSeconds(elapsedSeconds);
    }
}
