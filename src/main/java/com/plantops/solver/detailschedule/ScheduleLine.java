package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * 产线（机器）规划实体：{@link PlanningListVariable} 维护同线工序顺序，
 * 为 Timefold 标准「Chained Through Time / list variable」模型。
 */
@PlanningEntity
public class ScheduleLine {

    @PlanningId
    private String lineId;
    private String resourceId;
    private String areaId;
    private boolean opened;
    private int capacityMinutes;

    @PlanningListVariable(valueRangeProviderRefs = "operationRange")
    private List<OperationAssignment> assignedOperations = new ArrayList<>();

    public ScheduleLine() {
    }

    public ScheduleLine(String lineId, String resourceId, String areaId, boolean opened, int capacityMinutes) {
        this.lineId = lineId;
        this.resourceId = resourceId;
        this.areaId = areaId;
        this.opened = opened;
        this.capacityMinutes = capacityMinutes;
    }

    public String getLineId() {
        return lineId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAreaId() {
        return areaId;
    }

    public boolean isOpened() {
        return opened;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }

    public List<OperationAssignment> getAssignedOperations() {
        return assignedOperations;
    }

    public void setAssignedOperations(List<OperationAssignment> assignedOperations) {
        this.assignedOperations = assignedOperations != null ? assignedOperations : new ArrayList<>();
    }
}
