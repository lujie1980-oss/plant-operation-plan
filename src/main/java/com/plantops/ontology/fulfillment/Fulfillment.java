package com.plantops.ontology.fulfillment;

public class Fulfillment {

    private String id;
    private String demandId;
    private String supplyId;
    private double quantity;
    private FulfillmentType type;

    public Fulfillment() {
    }

    public Fulfillment(String id, String demandId, String supplyId, double quantity, FulfillmentType type) {
        this.id = id;
        this.demandId = demandId;
        this.supplyId = supplyId;
        this.quantity = quantity;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDemandId() {
        return demandId;
    }

    public void setDemandId(String demandId) {
        this.demandId = demandId;
    }

    public String getSupplyId() {
        return supplyId;
    }

    public void setSupplyId(String supplyId) {
        this.supplyId = supplyId;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public FulfillmentType getType() {
        return type;
    }

    public void setType(FulfillmentType type) {
        this.type = type;
    }
}
