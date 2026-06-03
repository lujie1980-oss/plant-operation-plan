package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeoverProductAttributeIndexTest {

    @Test
    void resolve_productCodeWithoutDatabase() {
        ChangeoverProductAttributeIndex index = ChangeoverProductAttributeIndex.empty();
        assertEquals("P-A", index.resolve("P-A", "半成品", "productCode"));
    }

    @Test
    void resolve_usesPreloadedAttributes() {
        ChangeoverProductAttributeIndex index = ChangeoverProductAttributeIndex.testingExact(
                "P-A", "半成品", "wireMaterial", "W1");

        assertEquals("W1", index.resolve("P-A", "半成品", "wireMaterial"));
        assertEquals("*", index.resolve("P-A", "半成品", "keyMaterial"));
    }
}
