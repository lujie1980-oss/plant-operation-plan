package com.plantops.ontology.supply;

public class PlanUnit {

    private String id;
    private String supplyOrderId;
    private double quantity;
    private int sequenceNr;

    public PlanUnit() {
    }

    public PlanUnit(String id, String supplyOrderId, double quantity, int sequenceNr) {
        this.id = id;
        this.supplyOrderId = supplyOrderId;
        this.quantity = quantity;
        this.sequenceNr = sequenceNr;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSupplyOrderId() {
        return supplyOrderId;
    }

    public void setSupplyOrderId(String supplyOrderId) {
        this.supplyOrderId = supplyOrderId;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public int getSequenceNr() {
        return sequenceNr;
    }

    public void setSequenceNr(int sequenceNr) {
        this.sequenceNr = sequenceNr;
    }
}
