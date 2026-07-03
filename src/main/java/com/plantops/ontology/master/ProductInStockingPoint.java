package com.plantops.ontology.master;

public class ProductInStockingPoint {

    private String id;
    private String productId;
    private String stockingPointId;
    private String productCode;

    public ProductInStockingPoint() {
    }

    public ProductInStockingPoint(String id, String productId, String stockingPointId, String productCode) {
        this.id = id;
        this.productId = productId;
        this.stockingPointId = stockingPointId;
        this.productCode = productCode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getStockingPointId() {
        return stockingPointId;
    }

    public void setStockingPointId(String stockingPointId) {
        this.stockingPointId = stockingPointId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }
}
