package com.plantops.ontology.period;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Period {

    private String id;
    private int sequenceNr;
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodGranularity granularity = PeriodGranularity.DAY;
    private String shiftId;
    private String parentPeriodId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    /** leaf Period 可挂 ENT-RCA；汇总日桶为 false（TODO-23 S2）。 */
    private boolean leaf = true;

    public Period() {
    }

    public Period(String id, int sequenceNr, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.sequenceNr = sequenceNr;
        this.startDate = startDate;
        this.endDate = endDate;
        this.granularity = PeriodGranularity.DAY;
        this.startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        this.endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSequenceNr() {
        return sequenceNr;
    }

    public void setSequenceNr(int sequenceNr) {
        this.sequenceNr = sequenceNr;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public PeriodGranularity getGranularity() {
        return granularity != null ? granularity : PeriodGranularity.DAY;
    }

    public void setGranularity(PeriodGranularity granularity) {
        this.granularity = granularity;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getParentPeriodId() {
        return parentPeriodId;
    }

    public void setParentPeriodId(String parentPeriodId) {
        this.parentPeriodId = parentPeriodId;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(boolean leaf) {
        this.leaf = leaf;
    }

    /** @deprecated 使用显式 {@link #isLeaf()} 字段。 */
    @Deprecated
    public boolean isLeafGranularity() {
        PeriodGranularity g = getGranularity();
        return g == PeriodGranularity.SHIFT || g == PeriodGranularity.DAY;
    }
}
