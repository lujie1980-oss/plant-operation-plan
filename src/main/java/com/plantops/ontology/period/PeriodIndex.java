package com.plantops.ontology.period;

import java.time.LocalDate;
import java.util.List;

public final class PeriodIndex {

    private final List<Period> periodsOrdered;

    private PeriodIndex(List<Period> periodsOrdered) {
        this.periodsOrdered = periodsOrdered;
    }

    public static PeriodIndex of(List<Period> periodsOrdered) {
        return new PeriodIndex(List.copyOf(periodsOrdered));
    }

    public int sequenceFor(LocalDate date) {
        if (date == null || periodsOrdered.isEmpty()) {
            return 0;
        }
        if (date.isBefore(periodsOrdered.get(0).getStartDate())) {
            return 0;
        }
        for (Period period : periodsOrdered) {
            if (!date.isBefore(period.getStartDate()) && !date.isAfter(period.getEndDate())) {
                return period.getSequenceNr();
            }
        }
        return periodsOrdered.get(periodsOrdered.size() - 1).getSequenceNr();
    }
}
