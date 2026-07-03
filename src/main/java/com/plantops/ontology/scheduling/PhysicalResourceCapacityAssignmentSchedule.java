package com.plantops.ontology.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ENT-RCA-SCH · minute occupancy on ENT-PR (physical resource), aligned with S05 feedback
 * (ADR-17 · TODO-20 SCH-P0). Distinct from day-level ENT-RCA on ENT-SRP.
 */
public class PhysicalResourceCapacityAssignmentSchedule {

    private String id;
    private String operationScheduleId;
    private String operationId;
    private String physicalResourceId;
    private String standardResourceId;
    private int assignedMinutes;
    private int operationTotalMinutes;
    private boolean locked;
    private LocalDate slotDate;
    private LocalDateTime plannedStartTs;
    private LocalDateTime plannedEndTs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOperationScheduleId() {
        return operationScheduleId;
    }

    public void setOperationScheduleId(String operationScheduleId) {
        this.operationScheduleId = operationScheduleId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
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

    public int getAssignedMinutes() {
        return assignedMinutes;
    }

    public void setAssignedMinutes(int assignedMinutes) {
        this.assignedMinutes = assignedMinutes;
    }

    public int getOperationTotalMinutes() {
        return operationTotalMinutes;
    }

    public void setOperationTotalMinutes(int operationTotalMinutes) {
        this.operationTotalMinutes = operationTotalMinutes;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalDateTime getPlannedStartTs() {
        return plannedStartTs;
    }

    public void setPlannedStartTs(LocalDateTime plannedStartTs) {
        this.plannedStartTs = plannedStartTs;
    }

    public LocalDateTime getPlannedEndTs() {
        return plannedEndTs;
    }

    public void setPlannedEndTs(LocalDateTime plannedEndTs) {
        this.plannedEndTs = plannedEndTs;
    }
}
