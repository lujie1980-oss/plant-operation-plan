package com.plantops.ontology.supply;

public class OperationOnStandardResource {

    private String id;
    private String operationId;
    private String standardResourceId;
    private int resourcePriority;
    private int setupTimeMinutes;
    private double processTimeSeconds;

    public OperationOnStandardResource() {
    }

    public OperationOnStandardResource(
            String id,
            String operationId,
            String standardResourceId,
            int resourcePriority,
            int setupTimeMinutes,
            double processTimeSeconds) {
        this.id = id;
        this.operationId = operationId;
        this.standardResourceId = standardResourceId;
        this.resourcePriority = resourcePriority;
        this.setupTimeMinutes = setupTimeMinutes;
        this.processTimeSeconds = processTimeSeconds;
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

    public String getStandardResourceId() {
        return standardResourceId;
    }

    public void setStandardResourceId(String standardResourceId) {
        this.standardResourceId = standardResourceId;
    }

    public int getResourcePriority() {
        return resourcePriority;
    }

    public void setResourcePriority(int resourcePriority) {
        this.resourcePriority = resourcePriority;
    }

    public int getSetupTimeMinutes() {
        return setupTimeMinutes;
    }

    public void setSetupTimeMinutes(int setupTimeMinutes) {
        this.setupTimeMinutes = setupTimeMinutes;
    }

    public double getProcessTimeSeconds() {
        return processTimeSeconds;
    }

    public void setProcessTimeSeconds(double processTimeSeconds) {
        this.processTimeSeconds = processTimeSeconds;
    }
}
