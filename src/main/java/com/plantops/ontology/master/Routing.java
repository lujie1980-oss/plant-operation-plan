package com.plantops.ontology.master;

/**
 * 主计划主数据：产品在某库存点上的工艺路线头。
 */
public class Routing {

    private String id;
    private String pispId;
    private String productCode;
    private String routingName;

    public Routing() {
    }

    public Routing(String id, String pispId, String productCode, String routingName) {
        this.id = id;
        this.pispId = pispId;
        this.productCode = productCode;
        this.routingName = routingName;
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

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getRoutingName() {
        return routingName;
    }

    public void setRoutingName(String routingName) {
        this.routingName = routingName;
    }
}
