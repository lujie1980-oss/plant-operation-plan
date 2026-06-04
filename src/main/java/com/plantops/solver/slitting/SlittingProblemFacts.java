package com.plantops.solver.slitting;

import java.util.List;

public class SlittingProblemFacts {

    private int maxPositionMm = 10000;
    private double masterWasteWeight = 10.0;
    private double intermediateWasteWeight = 1.0;
    private List<Dimensions> standardIntermediateSizes = List.of();

    public int getMaxPositionMm() {
        return maxPositionMm;
    }

    public void setMaxPositionMm(int maxPositionMm) {
        this.maxPositionMm = maxPositionMm;
    }

    public double getMasterWasteWeight() {
        return masterWasteWeight;
    }

    public void setMasterWasteWeight(double masterWasteWeight) {
        this.masterWasteWeight = masterWasteWeight;
    }

    public double getIntermediateWasteWeight() {
        return intermediateWasteWeight;
    }

    public void setIntermediateWasteWeight(double intermediateWasteWeight) {
        this.intermediateWasteWeight = intermediateWasteWeight;
    }

    public List<Dimensions> getStandardIntermediateSizes() {
        return standardIntermediateSizes;
    }

    public void setStandardIntermediateSizes(List<Dimensions> standardIntermediateSizes) {
        this.standardIntermediateSizes = standardIntermediateSizes;
    }
}
