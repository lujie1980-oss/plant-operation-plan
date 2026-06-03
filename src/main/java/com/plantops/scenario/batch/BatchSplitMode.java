package com.plantops.scenario.batch;

public enum BatchSplitMode {
    NONE,
    FIXED_QTY,
    KITTING,
    AUTO;

    public static BatchSplitMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
