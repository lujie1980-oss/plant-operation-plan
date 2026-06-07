package com.plantops.ontology.supply;

import java.time.LocalDate;

public class SupplyOrder {

    private String id;
    private String productCode;
    private String pispId;
    private double quantity;
    private LocalDate needDate;
    private SupplyOrderStatus status;
    private SupplyOrderType type;

    public SupplyOrder() {
    }

    public SupplyOrder(
            String id,
            String productCode,
            String pispId,
            double quantity,
            LocalDate needDate,
            SupplyOrderStatus status,
            SupplyOrderType type) {
        this.id = id;
        this.productCode = productCode;
        this.pispId = pispId;
        this.quantity = quantity;
        this.needDate = needDate;
        this.status = status;
        this.type = type;
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

    public LocalDate getNeedDate() {
        return needDate;
    }

    public void setNeedDate(LocalDate needDate) {
        this.needDate = needDate;
    }

    public SupplyOrderStatus getStatus() {
        return status;
    }

    public void setStatus(SupplyOrderStatus status) {
        this.status = status;
    }

    public SupplyOrderType getType() {
        return type;
    }

    public void setType(SupplyOrderType type) {
        this.type = type;
    }
}
