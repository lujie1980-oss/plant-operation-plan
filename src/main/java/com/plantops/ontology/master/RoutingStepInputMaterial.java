package com.plantops.ontology.master;

/**
 * 工序投入物料（主数据层，对应 BOM 组件挂接首道工序）。
 */
public class RoutingStepInputMaterial {

    private String id;
    private String routingStepId;
    private String componentProductCode;
    private double componentQtyPer;
    private boolean critical;

    public RoutingStepInputMaterial() {
    }

    public RoutingStepInputMaterial(
            String id,
            String routingStepId,
            String componentProductCode,
            double componentQtyPer,
            boolean critical) {
        this.id = id;
        this.routingStepId = routingStepId;
        this.componentProductCode = componentProductCode;
        this.componentQtyPer = componentQtyPer;
        this.critical = critical;
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

    public String getComponentProductCode() {
        return componentProductCode;
    }

    public void setComponentProductCode(String componentProductCode) {
        this.componentProductCode = componentProductCode;
    }

    public double getComponentQtyPer() {
        return componentQtyPer;
    }

    public void setComponentQtyPer(double componentQtyPer) {
        this.componentQtyPer = componentQtyPer;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }
}
