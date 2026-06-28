package com.plantops.ontology.master;

/**
 * 工序产出物料（主数据层，末道工序产出成品）。
 */
public class RoutingStepOutputMaterial {

    private String id;
    private String routingStepId;
    private String outputProductCode;
    private double outputQtyPer;

    public RoutingStepOutputMaterial() {
    }

    public RoutingStepOutputMaterial(
            String id,
            String routingStepId,
            String outputProductCode,
            double outputQtyPer) {
        this.id = id;
        this.routingStepId = routingStepId;
        this.outputProductCode = outputProductCode;
        this.outputQtyPer = outputQtyPer;
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

    public String getOutputProductCode() {
        return outputProductCode;
    }

    public void setOutputProductCode(String outputProductCode) {
        this.outputProductCode = outputProductCode;
    }

    public double getOutputQtyPer() {
        return outputQtyPer;
    }

    public void setOutputQtyPer(double outputQtyPer) {
        this.outputQtyPer = outputQtyPer;
    }
}
