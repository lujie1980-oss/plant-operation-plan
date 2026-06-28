package com.plantops.ontology.supply;

public class OperationOutputMaterial {

    private String id;
    private String operationId;
    private String supplyId;
    private double outputQty;

    public OperationOutputMaterial() {
    }

    public OperationOutputMaterial(String id, String operationId, String supplyId, double outputQty) {
        this.id = id;
        this.operationId = operationId;
        this.supplyId = supplyId;
        this.outputQty = outputQty;
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

    public String getSupplyId() {
        return supplyId;
    }

    public void setSupplyId(String supplyId) {
        this.supplyId = supplyId;
    }

    public double getOutputQty() {
        return outputQty;
    }

    public void setOutputQty(double outputQty) {
        this.outputQty = outputQty;
    }
}
