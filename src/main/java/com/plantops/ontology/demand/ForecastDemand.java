package com.plantops.ontology.demand;

import java.time.LocalDate;

public class ForecastDemand {

    private String id;
    private String productCode;
    private double quantity;
    private String forecastPeriod;
    private LocalDate needDate;
    private double confidence;

    public ForecastDemand() {
    }

    public ForecastDemand(
            String id,
            String productCode,
            double quantity,
            String forecastPeriod,
            LocalDate needDate,
            double confidence) {
        this.id = id;
        this.productCode = productCode;
        this.quantity = quantity;
        this.forecastPeriod = forecastPeriod;
        this.needDate = needDate;
        this.confidence = confidence;
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

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getForecastPeriod() {
        return forecastPeriod;
    }

    public void setForecastPeriod(String forecastPeriod) {
        this.forecastPeriod = forecastPeriod;
    }

    public LocalDate getNeedDate() {
        return needDate;
    }

    public void setNeedDate(LocalDate needDate) {
        this.needDate = needDate;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
