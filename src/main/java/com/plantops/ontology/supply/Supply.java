package com.plantops.ontology.supply;

public class Supply {

    private String id;
    private String productCode;
    private String pispId;
    private double quantity;
    private String supplyOrderId;

    public Supply() {
    }

    public Supply(String id, String productCode, String pispId, double quantity, String supplyOrderId) {
        this.id = id;
        this.productCode = productCode;
        this.pispId = pispId;
        this.quantity = quantity;
        this.supplyOrderId = supplyOrderId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getPispId() {
        return pispId;
    }

    public void setPispId(String pispId) {
        this.pispId = pispId;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getSupplyOrderId() {
        return supplyOrderId;
    }

    public void setSupplyOrderId(String supplyOrderId) {
        this.supplyOrderId = supplyOrderId;
    }
}
