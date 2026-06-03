package com.plantops.masterdata;

public enum MasterFieldCategory {
    GENERAL,
    CUSTOM;

    public String code() {
        return name();
    }
}
