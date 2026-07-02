package com.plantops.ontology.period;

/** ENT-PRP · 物理资源期间产能；日历真相源（ADR-17 · §5.8.2）。 */
public class PhysicalResourcePeriod {

    private String id;
    private String physicalResourceId;
    private String standardResourceId;
    private String periodId;
    private double totalCapacity;
    private double calendarDowntime;
    private double schedulerFeedbackMinutes;
    private double reservedCapacity;
    private double availableCapacity;
    private double overloadCapacity;

    public PhysicalResourcePeriod() {}

    public PhysicalResourcePeriod(
            String id, String physicalResourceId, String standardResourceId, String periodId) {
        this.id = id;
        this.physicalResourceId = physicalResourceId;
        this.standardResourceId = standardResourceId;
        this.periodId = periodId;
    }

    public void recalculateCapacityFields() {
        availableCapacity = totalCapacity - calendarDowntime - schedulerFeedbackMinutes;
        overloadCapacity = Math.max(0, reservedCapacity - availableCapacity);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhysicalResourceId() {
        return physicalResourceId;
    }

    public void setPhysicalResourceId(String physicalResourceId) {
        this.physicalResourceId = physicalResourceId;
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

    public double getSchedulerFeedbackMinutes() {
        return schedulerFeedbackMinutes;
    }

    public void setSchedulerFeedbackMinutes(double schedulerFeedbackMinutes) {
        this.schedulerFeedbackMinutes = schedulerFeedbackMinutes;
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

    public double getOverloadCapacity() {
        return overloadCapacity;
    }
}
