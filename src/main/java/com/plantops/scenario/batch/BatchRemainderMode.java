package com.plantops.scenario.batch;

public enum BatchRemainderMode {
    FLOOR,
    CEIL,
    SEPARATE_TAIL,
    MERGE_TAIL;

    public static BatchRemainderMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SEPARATE_TAIL;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SEPARATE_TAIL;
        }
    }
}
