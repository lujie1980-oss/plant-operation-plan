package com.plantops.ontology.supply;

/**
 * ENT-RCA：工序经 OOSR 在 SRP 上的产能占用分钟（ADR-15 · §5.5.1）。
 */
public class ResourceCapacityAssignment {

    private String id;
    private String operationId;
    private String operationOnStandardResourceId;
    private String standardResourcePeriodId;
    private int assignedMinutes;
    private int operationTotalMinutes;
    private boolean locked;
    private String parallelGroupId;

    public ResourceCapacityAssignment() {
    }

    public ResourceCapacityAssignment(
            String id,
            String operationId,
            String operationOnStandardResourceId,
            String standardResourcePeriodId,
            int assignedMinutes,
            int operationTotalMinutes,
            boolean locked,
            String parallelGroupId) {
        this.id = id;
        this.operationId = operationId;
        this.operationOnStandardResourceId = operationOnStandardResourceId;
        this.standardResourcePeriodId = standardResourcePeriodId;
        this.assignedMinutes = assignedMinutes;
        this.operationTotalMinutes = operationTotalMinutes;
        this.locked = locked;
        this.parallelGroupId = parallelGroupId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationOnStandardResourceId() {
        return operationOnStandardResourceId;
    }

    public void setOperationOnStandardResourceId(String operationOnStandardResourceId) {
        this.operationOnStandardResourceId = operationOnStandardResourceId;
    }

    public String getStandardResourcePeriodId() {
        return standardResourcePeriodId;
    }

    public void setStandardResourcePeriodId(String standardResourcePeriodId) {
        this.standardResourcePeriodId = standardResourcePeriodId;
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

    public String getParallelGroupId() {
        return parallelGroupId;
    }

    public void setParallelGroupId(String parallelGroupId) {
        this.parallelGroupId = parallelGroupId;
    }
}
