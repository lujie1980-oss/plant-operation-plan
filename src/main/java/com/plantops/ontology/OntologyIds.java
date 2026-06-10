package com.plantops.ontology;

public final class OntologyIds {

    public static final String DEFAULT_FG = "DEFAULT-FG";
    public static final int DEFAULT_PERIOD_COUNT = 28;

    private OntologyIds() {
    }

    public static String pispId(String productCode) {
        return "PISP-" + productCode + "-" + DEFAULT_FG;
    }

    public static String periodId(int sequenceNr) {
        return "P-" + sequenceNr;
    }

    public static String pisppId(String pispId, int sequenceNr) {
        return "PISPP-" + pispId + "-" + periodId(sequenceNr);
    }

    public static String srpId(String resourceId, int sequenceNr) {
        return "SRP-" + resourceId + "-" + periodId(sequenceNr);
    }
}
