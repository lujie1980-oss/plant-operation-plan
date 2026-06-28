package com.plantops.ontology.demand;

import java.time.LocalDate;

public class CustomerOrderLineDelivery {

    private String id;
    private String customerOrderLineId;
    private double deliveryQty;
    private LocalDate requestedDate;
    private LocalDate latestDesiredDate;
    private String status;

    public CustomerOrderLineDelivery() {
    }

    public CustomerOrderLineDelivery(
            String id,
            String customerOrderLineId,
            double deliveryQty,
            LocalDate requestedDate,
            LocalDate latestDesiredDate,
            String status) {
        this.id = id;
        this.customerOrderLineId = customerOrderLineId;
        this.deliveryQty = deliveryQty;
        this.requestedDate = requestedDate;
        this.latestDesiredDate = latestDesiredDate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerOrderLineId() {
        return customerOrderLineId;
    }

    public void setCustomerOrderLineId(String customerOrderLineId) {
        this.customerOrderLineId = customerOrderLineId;
    }

    public double getDeliveryQty() {
        return deliveryQty;
    }

    public void setDeliveryQty(double deliveryQty) {
        this.deliveryQty = deliveryQty;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public LocalDate getLatestDesiredDate() {
        return latestDesiredDate;
    }

    public void setLatestDesiredDate(LocalDate latestDesiredDate) {
        this.latestDesiredDate = latestDesiredDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
