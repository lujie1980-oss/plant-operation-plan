package com.plantops.ontology.period;

public class StandardResourcePeriod {

    private String id;
    private String standardResourceId;
    private String periodId;
    private double totalCapacity;
    private double calendarDowntime;
    private double technicalDowntime;
    private double reservedCapacity;
    private double availableCapacity;
    private double freeCapacity;
    private double overloadCapacity;

    public StandardResourcePeriod() {
    }

    public StandardResourcePeriod(String id, String standardResourceId, String periodId) {
        this.id = id;
        this.standardResourceId = standardResourceId;
        this.periodId = periodId;
    }

    public void recalculateCapacityFields() {
        availableCapacity = totalCapacity - calendarDowntime - technicalDowntime;
        freeCapacity = availableCapacity - reservedCapacity;
        overloadCapacity = Math.max(0, reservedCapacity - availableCapacity);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStandardResourceId() {
        return standardResourceId;
    }

    public void setStandardResourceId(String standardResourceId) {
        this.standardResourceId = standardResourceId;
    }

    public String getPeriodId() {
        return periodId;
    }

    public void setPeriodId(String periodId) {
        this.periodId = periodId;
    }

    public double getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(double totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public double getCalendarDowntime() {
        return calendarDowntime;
    }

    public void setCalendarDowntime(double calendarDowntime) {
        this.calendarDowntime = calendarDowntime;
    }

    public double getTechnicalDowntime() {
        return technicalDowntime;
    }

    public void setTechnicalDowntime(double technicalDowntime) {
        this.technicalDowntime = technicalDowntime;
    }

    public double getReservedCapacity() {
        return reservedCapacity;
    }

    public void setReservedCapacity(double reservedCapacity) {
        this.reservedCapacity = reservedCapacity;
    }

    public double getAvailableCapacity() {
        return availableCapacity;
    }

    public double getFreeCapacity() {
        return freeCapacity;
    }

    public double getOverloadCapacity() {
        return overloadCapacity;
    }
}
