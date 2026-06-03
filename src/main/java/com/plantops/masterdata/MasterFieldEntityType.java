package com.plantops.masterdata;

public enum MasterFieldEntityType {
    PRODUCT_RESOURCE,
    MATERIAL,
    SALES_ORDER;

    public String code() {
        return name();
    }

    public static MasterFieldEntityType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
