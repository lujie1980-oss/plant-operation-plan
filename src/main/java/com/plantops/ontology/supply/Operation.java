package com.plantops.ontology.supply;

import java.time.LocalDate;

public class Operation {

    private String id;
    private String supplyOrderId;
    private int sequenceNr;
    private String operationName;
    private double productionTimeMinutes;
    private LocalDate earliestPossibleStart;
    private LocalDate latestPossibleEnd;
    private boolean infeasible;

    public Operation() {
    }

    public Operation(String id, String supplyOrderId, int sequenceNr,
            String operationName, double productionTimeMinutes) {
        this.id = id;
        this.supplyOrderId = supplyOrderId;
        this.sequenceNr = sequenceNr;
        this.operationName = operationName;
        this.productionTimeMinutes = productionTimeMinutes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSupplyOrderId() {
        return supplyOrderId;
    }

    public void setSupplyOrderId(String supplyOrderId) {
        this.supplyOrderId = supplyOrderId;
    }

    public int getSequenceNr() {
        return sequenceNr;
    }

    public void setSequenceNr(int sequenceNr) {
        this.sequenceNr = sequenceNr;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public double getProductionTimeMinutes() {
        return productionTimeMinutes;
    }

    public void setProductionTimeMinutes(double productionTimeMinutes) {
        this.productionTimeMinutes = productionTimeMinutes;
    }

    public LocalDate getEarliestPossibleStart() {
        return earliestPossibleStart;
    }

    public void setEarliestPossibleStart(LocalDate earliestPossibleStart) {
        this.earliestPossibleStart = earliestPossibleStart;
    }

    public LocalDate getLatestPossibleEnd() {
        return latestPossibleEnd;
    }

    public void setLatestPossibleEnd(LocalDate latestPossibleEnd) {
        this.latestPossibleEnd = latestPossibleEnd;
    }

    public boolean isInfeasible() {
        return infeasible;
    }

    public void setInfeasible(boolean infeasible) {
        this.infeasible = infeasible;
    }
}
