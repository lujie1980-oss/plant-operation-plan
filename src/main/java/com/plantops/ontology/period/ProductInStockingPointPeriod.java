package com.plantops.ontology.period;

public class ProductInStockingPointPeriod {

    private String id;
    private String pispId;
    private String periodId;
    private double onHand;
    private double plannedSupplyTotal;
    private double plannedDemandQuantityTotal;
    private double inventoryTargetQuantity;
    private double plannedInventoryLevel;
    private double replenishedInventoryLevel;
    private double stockShortageQuantity;

    public ProductInStockingPointPeriod() {
    }

    public ProductInStockingPointPeriod(String id, String pispId, String periodId) {
        this.id = id;
        this.pispId = pispId;
        this.periodId = periodId;
    }

    public void recalculatePlanningFields() {
        plannedInventoryLevel = onHand + plannedSupplyTotal - plannedDemandQuantityTotal;
        replenishedInventoryLevel = onHand + plannedSupplyTotal;
        stockShortageQuantity = Math.max(
                0,
                plannedDemandQuantityTotal + inventoryTargetQuantity - replenishedInventoryLevel);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPispId() {
        return pispId;
    }

    public void setPispId(String pispId) {
        this.pispId = pispId;
    }

    public String getPeriodId() {
        return periodId;
    }

    public void setPeriodId(String periodId) {
        this.periodId = periodId;
    }

    public double getOnHand() {
        return onHand;
    }

    public void setOnHand(double onHand) {
        this.onHand = onHand;
    }

    public double getPlannedSupplyTotal() {
        return plannedSupplyTotal;
    }

    public void setPlannedSupplyTotal(double plannedSupplyTotal) {
        this.plannedSupplyTotal = plannedSupplyTotal;
    }

    public double getPlannedDemandQuantityTotal() {
        return plannedDemandQuantityTotal;
    }

    public void setPlannedDemandQuantityTotal(double plannedDemandQuantityTotal) {
        this.plannedDemandQuantityTotal = plannedDemandQuantityTotal;
    }

    public double getInventoryTargetQuantity() {
        return inventoryTargetQuantity;
    }

    public void setInventoryTargetQuantity(double inventoryTargetQuantity) {
        this.inventoryTargetQuantity = inventoryTargetQuantity;
    }

    public double getPlannedInventoryLevel() {
        return plannedInventoryLevel;
    }

    public double getReplenishedInventoryLevel() {
        return replenishedInventoryLevel;
    }

    public double getStockShortageQuantity() {
        return stockShortageQuantity;
    }
}
