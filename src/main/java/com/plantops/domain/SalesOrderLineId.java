package com.plantops.domain;

public record SalesOrderLineId(String salesOrderNo, int lineNo) {

    public String key() {
        return salesOrderNo + ":" + lineNo;
    }

    public static SalesOrderLineId parse(String key) {
        int idx = key.lastIndexOf(':');
        return new SalesOrderLineId(key.substring(0, idx), Integer.parseInt(key.substring(idx + 1)));
    }
}
