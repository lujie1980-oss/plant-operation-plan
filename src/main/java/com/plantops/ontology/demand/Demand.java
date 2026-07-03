package com.plantops.ontology.demand;

import java.time.LocalDate;

public class Demand {

    private String id;
    private String productCode;
    private String pispId;
    private double quantity;
    private LocalDate needDate;
    private int priority;
    private DemandSourceType sourceType;
    private String sourceId;

    public Demand() {
    }

    public Demand(
            String id,
            String productCode,
            String pispId,
            double quantity,
            LocalDate needDate,
            int priority,
            DemandSourceType sourceType,
            String sourceId) {
        this.id = id;
        this.productCode = productCode;
        this.pispId = pispId;
        this.quantity = quantity;
        this.needDate = needDate;
        this.priority = priority;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public DemandSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DemandSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}
