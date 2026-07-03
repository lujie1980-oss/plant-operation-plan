package com.plantops.solver.slitting;

public enum CuttingMethod {
    LONGITUDINAL,
    TRANSVERSE,
    LASER;

    public static CuttingMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return LONGITUDINAL;
        }
        return CuttingMethod.valueOf(value.trim().toUpperCase());
    }
}
