package com.plantops.ontology.period;

import java.time.LocalDate;

public class Period {

    private String id;
    private int sequenceNr;
    private LocalDate startDate;
    private LocalDate endDate;

    public Period() {
    }

    public Period(String id, int sequenceNr, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.sequenceNr = sequenceNr;
        this.startDate = startDate;
        this.endDate = endDate;
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
}
