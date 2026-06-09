package com.plantops.rol;

public record ChangeOperation(
        String targetType,
        String targetId,
        String property,
        Object value) {

    public static final String TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD = "ProductInStockingPointPeriod";
}
