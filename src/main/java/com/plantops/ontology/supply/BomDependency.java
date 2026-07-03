package com.plantops.ontology.supply;

public class BomDependency {

    private String id;
    private String parentSupplyOrderId;
    private String childSupplyOrderId;

    public BomDependency() {
    }

    public BomDependency(String id, String parentSupplyOrderId, String childSupplyOrderId) {
        this.id = id;
        this.parentSupplyOrderId = parentSupplyOrderId;
        this.childSupplyOrderId = childSupplyOrderId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentSupplyOrderId() {
        return parentSupplyOrderId;
    }

    public void setParentSupplyOrderId(String parentSupplyOrderId) {
        this.parentSupplyOrderId = parentSupplyOrderId;
    }

    public String getChildSupplyOrderId() {
        return childSupplyOrderId;
    }

    public void setChildSupplyOrderId(String childSupplyOrderId) {
        this.childSupplyOrderId = childSupplyOrderId;
    }
}
