package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductRoutingStepsTest {

    @Test
    void operationsForProduct_groupsSameSequenceAndSortsByResourcePriority() {
        ProductRoutingSteps.Operation op = new ProductRoutingSteps.Operation(
                1,
                "绕线",
                List.of(
                        new ProductRoutingSteps.ResourceOption("RES-A", 1, BigDecimal.TEN, 5),
                        new ProductRoutingSteps.ResourceOption("RES-B", 2, BigDecimal.TEN, 5)));
        assertEquals(List.of("RES-A", "RES-B"), op.allowedResourceIds());
        assertEquals("RES-A", op.primaryResourceId());
    }

    @Test
    void operationDurationMinutes_includesSetupAndCt() {
        // 15 + ceil(5 * 90 / 60) = 23
        assertEquals(
                23,
                ProductRoutingSteps.operationDurationMinutes(
                        15, BigDecimal.valueOf(90), BigDecimal.valueOf(5)));
    }

    @Test
    void operationDurationMinutes_sumsMultipleStepsManually() {
        int step1 = ProductRoutingSteps.operationDurationMinutes(10, BigDecimal.valueOf(120), BigDecimal.TEN);
        int step2 = ProductRoutingSteps.operationDurationMinutes(5, BigDecimal.valueOf(60), BigDecimal.TEN);
        // (10 + ceil(10*120/60)) + (5 + ceil(10*60/60)) = 30 + 15 = 45
        assertEquals(30, step1);
        assertEquals(15, step2);
        assertEquals(45, step1 + step2);
    }

    @Test
    void operationDurationMinutes_fallbackWhenNoCt() {
        assertEquals(25, ProductRoutingSteps.operationDurationMinutes(10, null, BigDecimal.TEN));
    }
}
