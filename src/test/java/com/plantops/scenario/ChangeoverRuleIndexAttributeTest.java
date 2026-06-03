package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeoverRuleIndexAttributeTest {

    @Test
    void computeMinutes_matchesProductCodeRuleWithoutDatabase() {
        ChangeoverRuleIndex index = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("工序1", "productCode", "P-A", "P-B", 10)));

        assertEquals(10, index.computeMinutes("工序1", null, -1, "P-A", "P-B"));
        assertEquals(0, index.computeMinutes("工序1", null, -1, "P-A", "P-A"));
    }

    @Test
    void computeMinutes_returnsZeroWhenNoRulesForOperation() {
        ChangeoverRuleIndex index = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("工序2", "wireMaterial", "*", "*", 15)));
        assertEquals(0, index.computeMinutes("工序1", null, -1, "P-A", "P-B"));
    }

    @Test
    void computeMinutes_resolvesRoutingNameViaResourceId() {
        ChangeoverRuleIndex index = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("半成品", "productCode", "*", "*", 10)));

        assertEquals(10, index.computeMinutes("工序 2", "Coaxial", 2, "P-A", "P-B"));
        assertEquals(0, index.computeMinutes("工序 2", "Coaxial", 2, "P-A", "P-A"));
    }

    @Test
    void computeMinutes_resolvesRoutingNameViaSequenceFallback() {
        ChangeoverRuleIndex index = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("气密", "productCode", "*", "*", 5)));

        assertEquals(5, index.computeMinutes("工序 4", null, 4, "P-A", "P-B"));
    }

    @Test
    void computeMinutes_usesPreloadedAttributesWithoutDatabase() {
        ChangeoverRuleIndex index = new ChangeoverRuleIndex(
                List.of(new ChangeoverRuleIndex.Rule("半成品", "wireMaterial", "*", "*", 15)),
                ChangeoverProductAttributeIndex.testingExact("P-A", "半成品", "wireMaterial", "W1"),
                Map.of(),
                Map.of());

        assertEquals(15, index.computeMinutes("半成品", null, -1, "P-A", "P-B"));
        assertEquals(0, index.computeMinutes("半成品", null, -1, "P-A", "P-A"));
    }
}
