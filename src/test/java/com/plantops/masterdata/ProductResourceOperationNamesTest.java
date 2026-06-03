package com.plantops.masterdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductResourceOperationNamesTest {

    @Test
    void normalize_keepsExplicitName() {
        assertEquals("成品", ProductResourceOperationNames.normalize("成品", "总成", 5));
    }

    @Test
    void normalize_infersFromResourceId() {
        assertEquals("半成品", ProductResourceOperationNames.normalize(null, "Coaxial", null));
        assertEquals("半成品", ProductResourceOperationNames.normalize("工序 2", "Coaxial", 2));
    }

    @Test
    void normalize_infersFromSequenceWhenResourceUnknown() {
        assertEquals("气密", ProductResourceOperationNames.normalize("工序 4", "未知设备", 4));
    }

    @Test
    void needsNormalization_detectsPlaceholderAndBlank() {
        assertTrue(ProductResourceOperationNames.needsNormalization(null, "Coaxial"));
        assertTrue(ProductResourceOperationNames.needsNormalization("工序 2", "Coaxial"));
        assertFalse(ProductResourceOperationNames.needsNormalization("半成品", "Coaxial"));
    }
}
