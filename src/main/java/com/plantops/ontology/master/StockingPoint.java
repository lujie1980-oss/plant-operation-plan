package com.plantops.ontology.master;

import com.plantops.ontology.OntologyIds;

public class StockingPoint {

    public static final String DEFAULT_FG = OntologyIds.DEFAULT_FG;

    private String id;
    private String stockingPointCode;

    public StockingPoint() {
    }

    public StockingPoint(String id, String stockingPointCode) {
        this.id = id;
        this.stockingPointCode = stockingPointCode;
    }

    public static StockingPoint defaultFg() {
        return new StockingPoint(DEFAULT_FG, DEFAULT_FG);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStockingPointCode() {
        return stockingPointCode;
    }

    public void setStockingPointCode(String stockingPointCode) {
        this.stockingPointCode = stockingPointCode;
    }
}
