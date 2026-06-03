package com.plantops.masterdata;

public enum MasterFieldDataType {
    STRING,
    NUMBER,
    INTEGER,
    DATE,
    BOOL,
    ENUM;

    public String code() {
        return name();
    }
}
