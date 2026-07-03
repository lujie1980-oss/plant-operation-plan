package com.plantops.ontology.master;

import java.math.BigDecimal;

/**
 * 工序可选标准资源（主数据层，对应 {@code ProductResourceEntity} 单行）。
 */
public class RoutingStepOnStandardResource {

    private String id;
    private String routingStepId;
    private String standardResourceId;
    private Integer resourcePriority;
    private int setupTimeMinutes;
    private BigDecimal processTimeSeconds;

    public RoutingStepOnStandardResource() {
    }

    public RoutingStepOnStandardResource(
            String id,
            String routingStepId,
            String standardResourceId,
            Integer resourcePriority,
            int setupTimeMinutes,
            BigDecimal processTimeSeconds) {
        this.id = id;
        this.routingStepId = routingStepId;
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

    public String getRoutingStepId() {
        return routingStepId;
    }

    public void setRoutingStepId(String routingStepId) {
        this.routingStepId = routingStepId;
    }

    public String getStandardResourceId() {
        return standardResourceId;
    }

    public void setStandardResourceId(String standardResourceId) {
        this.standardResourceId = standardResourceId;
    }

    public Integer getResourcePriority() {
        return resourcePriority;
    }

    public void setResourcePriority(Integer resourcePriority) {
        this.resourcePriority = resourcePriority;
    }

    public int getSetupTimeMinutes() {
        return setupTimeMinutes;
    }

    public void setSetupTimeMinutes(int setupTimeMinutes) {
        this.setupTimeMinutes = setupTimeMinutes;
    }

    public BigDecimal getProcessTimeSeconds() {
        return processTimeSeconds;
    }

    public void setProcessTimeSeconds(BigDecimal processTimeSeconds) {
        this.processTimeSeconds = processTimeSeconds;
    }
}
