package com.plantops.ontology.master;

public class Product {

    private String id;
    private String productCode;

    public Product() {
    }

    public Product(String id, String productCode) {
        this.id = id;
        this.productCode = productCode;
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
}
