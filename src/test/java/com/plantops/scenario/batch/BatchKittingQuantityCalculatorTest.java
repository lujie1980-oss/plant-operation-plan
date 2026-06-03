package com.plantops.scenario.batch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchKittingQuantityCalculatorTest {

    @Test
    void canKitQuantity_blankInputsArePermissive() {
        assertTrue(BatchKittingQuantityCalculator.canKitQuantity(null, BigDecimal.ONE, Map.of(), null));
        assertTrue(
                BatchKittingQuantityCalculator.canKitQuantity(
                        null, null, Map.of("A", BigDecimal.ONE), null));
    }
}
