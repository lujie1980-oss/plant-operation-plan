package com.plantops.solver.slitting;

import java.util.ArrayList;
import java.util.List;

public class RollNode {

    private String nodeId;
    private RollType type;
    private Dimensions dimensions;
    private CuttingMethod cuttingMethod;
    private double kerfMm;
    private String sourceSpecCode;
    private Long sourceChildOrderId;
    private Long sourceMasterRollId;
    private RollNode parent;
    private final List<RollNode> children = new ArrayList<>();

    public RollNode() {
    }

    public RollNode(String nodeId, RollType type, Dimensions dimensions) {
        this.nodeId = nodeId;
        this.type = type;
        this.dimensions = dimensions;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public RollType getType() {
        return type;
    }

    public void setType(RollType type) {
        this.type = type;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimensions dimensions) {
        this.dimensions = dimensions;
    }

    public CuttingMethod getCuttingMethod() {
        return cuttingMethod;
    }

    public void setCuttingMethod(CuttingMethod cuttingMethod) {
        this.cuttingMethod = cuttingMethod;
    }

    public double getKerfMm() {
        return kerfMm;
    }

    public void setKerfMm(double kerfMm) {
        this.kerfMm = kerfMm;
    }

    public String getSourceSpecCode() {
        return sourceSpecCode;
    }

    public void setSourceSpecCode(String sourceSpecCode) {
        this.sourceSpecCode = sourceSpecCode;
    }

    public Long getSourceChildOrderId() {
        return sourceChildOrderId;
    }

    public void setSourceChildOrderId(Long sourceChildOrderId) {
        this.sourceChildOrderId = sourceChildOrderId;
    }

    public Long getSourceMasterRollId() {
        return sourceMasterRollId;
    }

    public void setSourceMasterRollId(Long sourceMasterRollId) {
        this.sourceMasterRollId = sourceMasterRollId;
    }

    public RollNode getParent() {
        return parent;
    }

    public void setParent(RollNode parent) {
        this.parent = parent;
    }

    public List<RollNode> getChildren() {
        return children;
    }

    public double calculateWasteArea(java.util.Collection<NestAssignment> assignments) {
        if (dimensions == null) {
            return 0;
        }
        double used = assignments.stream()
                .filter(a -> a.getParentNode() != null && nodeId.equals(a.getParentNode().getNodeId()))
                .mapToDouble(a -> SlittingGeometryUtil.effectiveWidth(a.getPlacedNode(), a.isRotated())
                        * SlittingGeometryUtil.effectiveLength(a.getPlacedNode(), a.isRotated()))
                .sum();
        return Math.max(0, dimensions.area() - used);
    }
}
