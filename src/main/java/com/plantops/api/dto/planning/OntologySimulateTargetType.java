package com.plantops.api.dto.planning;

public enum OntologySimulateTargetType {
    PISPP,
    SRP,
    SUPPLY_ORDER;

    public static OntologySimulateTargetType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PISPP;
        }
        return OntologySimulateTargetType.valueOf(raw.trim().toUpperCase());
    }
}
