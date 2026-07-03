package com.plantops.ontology.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** ENT-OP-SCH · minute-level operation schedule (MOD-SCH / PROC-S05 · TODO-20 SCH-P0). */
public class OperationSchedule {

    private String id;
    private String detailScheduleVersionId;
    private String operationId;
    private String workOrderNo;
    private String batchNo;
    private String physicalResourceId;
    private String standardResourceId;
    private int sequenceIndex;
    private int operationSeq;
    private String operationName;
    private String productCode;
    private int startMinute;
    private int endMinute;
    private int durationMinutes;
    private LocalDate planningAnchorDate;
    private LocalDateTime plannedStartTs;
    private LocalDateTime plannedEndTs;
    private boolean pinned;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDetailScheduleVersionId() {
        return detailScheduleVersionId;
    }

    public void setDetailScheduleVersionId(String detailScheduleVersionId) {
        this.detailScheduleVersionId = detailScheduleVersionId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(int sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public int getOperationSeq() {
        return operationSeq;
    }

    public void setOperationSeq(int operationSeq) {
        this.operationSeq = operationSeq;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public LocalDate getPlanningAnchorDate() {
        return planningAnchorDate;
    }

    public void setPlanningAnchorDate(LocalDate planningAnchorDate) {
        this.planningAnchorDate = planningAnchorDate;
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

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
