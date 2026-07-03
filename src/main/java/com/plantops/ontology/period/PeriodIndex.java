package com.plantops.ontology.period;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** shift / 日 Period 索引（ADR-16 · TODO-23 S2）。 */
public final class PeriodIndex {

    private final List<Period> periodsOrdered;
    private final Map<Integer, Period> bySequenceNr;
    private final Map<String, List<Period>> childrenByParentId;
    private final boolean hasShiftPeriods;

    private PeriodIndex(List<Period> periodsOrdered) {
        this.periodsOrdered = List.copyOf(periodsOrdered);
        Map<Integer, Period> bySeq = new LinkedHashMap<>();
        Map<String, List<Period>> children = new HashMap<>();
        boolean shift = false;
        for (Period period : periodsOrdered) {
            bySeq.put(period.getSequenceNr(), period);
            if (period.getGranularity() == PeriodGranularity.SHIFT) {
                shift = true;
            }
            if (period.getParentPeriodId() != null && !period.getParentPeriodId().isBlank()) {
                children.computeIfAbsent(period.getParentPeriodId(), ignored -> new ArrayList<>()).add(period);
            }
        }
        this.bySequenceNr = Map.copyOf(bySeq);
        this.childrenByParentId = children;
        this.hasShiftPeriods = shift;
    }

    public static PeriodIndex of(List<Period> periodsOrdered) {
        return new PeriodIndex(periodsOrdered);
    }

    public List<Period> periodsOrdered() {
        return periodsOrdered;
    }

    public boolean hasShiftPeriods() {
        return hasShiftPeriods;
    }

    public Period periodAt(int sequenceNr) {
        return bySequenceNr.get(sequenceNr);
    }

    public List<Period> childrenOf(String parentPeriodId) {
        return childrenByParentId.getOrDefault(parentPeriodId, List.of());
    }

    public List<Period> leafPeriods() {
        return periodsOrdered.stream().filter(Period::isLeaf).toList();
    }

    public int sequenceFor(LocalDate date) {
        if (date == null || periodsOrdered.isEmpty()) {
            return 0;
        }
        if (date.isBefore(periodsOrdered.get(0).getStartDate())) {
            return 0;
        }
        for (Period period : periodsOrdered) {
            if (!period.isLeaf()) {
                continue;
            }
            if (!date.isBefore(period.getStartDate()) && !date.isAfter(period.getEndDate())) {
                return period.getSequenceNr();
            }
        }
        return periodsOrdered.get(periodsOrdered.size() - 1).getSequenceNr();
    }

    public int sequenceFor(LocalDate date, String shiftId) {
        if (date == null || periodsOrdered.isEmpty()) {
            return 0;
        }
        String effectiveShiftId = StandardResourcePeriodLoader.normalizeShiftId(shiftId);
        if (effectiveShiftId != null && !effectiveShiftId.isBlank() && hasShiftPeriods) {
            for (Period period : periodsOrdered) {
                if (!period.isLeaf() || period.getGranularity() != PeriodGranularity.SHIFT) {
                    continue;
                }
                if (date.equals(period.getStartDate()) && effectiveShiftId.equals(period.getShiftId())) {
                    return period.getSequenceNr();
                }
            }
        }
        return sequenceFor(date);
    }
}
