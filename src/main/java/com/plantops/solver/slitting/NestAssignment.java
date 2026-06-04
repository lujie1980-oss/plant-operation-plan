package com.plantops.solver.slitting;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class NestAssignment {

    @PlanningId
    private String assignmentId;
    private RollNode placedNode;

    @PlanningVariable(valueRangeProviderRefs = "containerRange")
    private RollNode parentNode;

    @PlanningVariable(valueRangeProviderRefs = "positionRange")
    private Integer positionX;

    @PlanningVariable(valueRangeProviderRefs = "positionRange")
    private Integer positionY;

    @PlanningVariable(valueRangeProviderRefs = "rotatedRange")
    private Boolean rotated;

    private int sequence;

    public NestAssignment() {
    }

    public NestAssignment(String assignmentId, RollNode placedNode) {
        this.assignmentId = assignmentId;
        this.placedNode = placedNode;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public RollNode getPlacedNode() {
        return placedNode;
    }

    public void setPlacedNode(RollNode placedNode) {
        this.placedNode = placedNode;
    }

    public RollNode getParentNode() {
        return parentNode;
    }

    public void setParentNode(RollNode parentNode) {
        this.parentNode = parentNode;
    }

    public Integer getPositionX() {
        return positionX;
    }

    public void setPositionX(Integer positionX) {
        this.positionX = positionX;
    }

    public Integer getPositionY() {
        return positionY;
    }

    public void setPositionY(Integer positionY) {
        this.positionY = positionY;
    }

    public Boolean getRotated() {
        return rotated;
    }

    public void setRotated(Boolean rotated) {
        this.rotated = rotated;
    }

    public boolean isRotated() {
        return Boolean.TRUE.equals(rotated);
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }
}
