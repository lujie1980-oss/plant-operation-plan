package com.plantops.ontology.demand;

public class CustomerOrderLine {

    private String id;
    private String salesOrderNo;
    private int salesOrderLineNo;
    private String customerCode;
    private String productCode;
    private double orderQty;

    public CustomerOrderLine() {
    }

    public CustomerOrderLine(
            String id,
            String salesOrderNo,
            int salesOrderLineNo,
            String customerCode,
            String productCode,
            double orderQty) {
        this.id = id;
        this.salesOrderNo = salesOrderNo;
        this.salesOrderLineNo = salesOrderLineNo;
        this.customerCode = customerCode;
        this.productCode = productCode;
        this.orderQty = orderQty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSalesOrderNo() {
        return salesOrderNo;
    }

    public void setSalesOrderNo(String salesOrderNo) {
        this.salesOrderNo = salesOrderNo;
    }

    public int getSalesOrderLineNo() {
        return salesOrderLineNo;
    }

    public void setSalesOrderLineNo(int salesOrderLineNo) {
        this.salesOrderLineNo = salesOrderLineNo;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public double getOrderQty() {
        return orderQty;
    }

    public void setOrderQty(double orderQty) {
        this.orderQty = orderQty;
    }
}
