package com.plantops.ontology.period;

import com.plantops.ontology.OntologyIds;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link PeriodSequenceSpec} 展开为有序 ENT-PER 列表（ADR-16 · TODO-23 S1）。
 */
public final class PeriodExpander {

    private record ShiftTemplate(String shiftId, LocalTime start, LocalTime end, boolean endNextDay) {}

    private static final List<ShiftTemplate> DEFAULT_SHIFT_TEMPLATES = List.of(
            new ShiftTemplate("S1", LocalTime.of(8, 0), LocalTime.of(20, 0), false),
            new ShiftTemplate("S2", LocalTime.of(20, 0), LocalTime.of(8, 0), true),
            new ShiftTemplate("S3", LocalTime.of(0, 0), LocalTime.of(8, 0), false));

    private PeriodExpander() {}

    public static List<Period> expand(PeriodSequenceSpec spec, LocalDate planningStart) {
        if (spec == null) {
            return List.of();
        }
        return expandSegments(spec.segments(), planningStart);
    }

    static List<Period> expandSegments(List<PeriodSequenceSpec.Segment> segments, LocalDate planningStart) {
        List<Period> periods = new ArrayList<>();
        LocalDate cursor = planningStart;
        int seq = 0;
        for (PeriodSequenceSpec.Segment segment : segments) {
            if (segment instanceof PeriodSequenceSpec.ShiftSegment shiftSegment) {
                seq = appendShiftDays(periods, cursor, seq, shiftSegment);
                cursor = cursor.plusDays(shiftSegment.dayCount());
            } else if (segment instanceof PeriodSequenceSpec.DaySegment daySegment) {
                seq = appendDayBuckets(periods, cursor, seq, daySegment);
                cursor = cursor.plusDays((long) daySegment.count() * daySegment.lengthDays());
            } else {
                throw new IllegalStateException("Unknown segment: " + segment);
            }
        }
        return List.copyOf(periods);
    }

    private static int appendShiftDays(
            List<Period> periods,
            LocalDate startDate,
            int seq,
            PeriodSequenceSpec.ShiftSegment segment) {
        List<ShiftTemplate> shifts = shiftTemplates(segment.shiftsPerDay());
        for (int day = 0; day < segment.dayCount(); day++) {
            LocalDate calendarDate = startDate.plusDays(day);
            Period parent = new Period(
                    OntologyIds.periodId(seq),
                    seq,
                    calendarDate,
                    calendarDate);
            parent.setGranularity(PeriodGranularity.DAY);
            parent.setLeaf(false);
            parent.setStartDateTime(calendarDate.atStartOfDay());
            parent.setEndDateTime(calendarDate.atTime(23, 59, 59));
            periods.add(parent);
            String parentId = parent.getId();
            seq++;

            for (ShiftTemplate shift : shifts) {
                Period period = new Period(
                        OntologyIds.periodId(seq),
                        seq,
                        calendarDate,
                        calendarDate);
                period.setGranularity(PeriodGranularity.SHIFT);
                period.setShiftId(shift.shiftId());
                period.setParentPeriodId(parentId);
                period.setLeaf(true);
                applyShiftDateTimes(period, calendarDate, shift);
                periods.add(period);
                seq++;
            }
        }
        return seq;
    }

    private static int appendDayBuckets(
            List<Period> periods,
            LocalDate cursor,
            int seq,
            PeriodSequenceSpec.DaySegment segment) {
        LocalDate bucketStart = cursor;
        for (int i = 0; i < segment.count(); i++) {
            LocalDate end = bucketStart.plusDays(segment.lengthDays() - 1L);
            Period period = new Period(OntologyIds.periodId(seq), seq, bucketStart, end);
            period.setGranularity(granularityForLengthDays(segment.lengthDays()));
            period.setLeaf(true);
            period.setStartDateTime(bucketStart.atStartOfDay());
            period.setEndDateTime(end.atTime(23, 59, 59));
            periods.add(period);
            bucketStart = end.plusDays(1);
            seq++;
        }
        return seq;
    }

    private static PeriodGranularity granularityForLengthDays(int lengthDays) {
        if (lengthDays >= 30) {
            return PeriodGranularity.MONTH;
        }
        if (lengthDays >= 7) {
            return PeriodGranularity.WEEK;
        }
        return PeriodGranularity.DAY;
    }

    private static List<ShiftTemplate> shiftTemplates(int shiftsPerDay) {
        if (shiftsPerDay < 1 || shiftsPerDay > DEFAULT_SHIFT_TEMPLATES.size()) {
            throw new IllegalArgumentException("shiftsPerDay must be 1.." + DEFAULT_SHIFT_TEMPLATES.size());
        }
        return DEFAULT_SHIFT_TEMPLATES.subList(0, shiftsPerDay);
    }

    private static void applyShiftDateTimes(Period period, LocalDate calendarDate, ShiftTemplate shift) {
        LocalDateTime start = LocalDateTime.of(calendarDate, shift.start());
        LocalDate endDate = shift.endNextDay() ? calendarDate.plusDays(1) : calendarDate;
        LocalDateTime end = LocalDateTime.of(endDate, shift.end());
        period.setStartDateTime(start);
        period.setEndDateTime(end);
    }
}
