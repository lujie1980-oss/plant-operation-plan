package com.plantops.ontology.supply;

public class OperationInputMaterial {

    private String id;
    private String operationId;
    private String demandId;
    private double componentQty;

    public OperationInputMaterial() {
    }

    public OperationInputMaterial(String id, String operationId, String demandId, double componentQty) {
        this.id = id;
        this.operationId = operationId;
        this.demandId = demandId;
        this.componentQty = componentQty;
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

    public String getDemandId() {
        return demandId;
    }

    public void setDemandId(String demandId) {
        this.demandId = demandId;
    }

    public double getComponentQty() {
        return componentQty;
    }

    public void setComponentQty(double componentQty) {
        this.componentQty = componentQty;
    }
}
