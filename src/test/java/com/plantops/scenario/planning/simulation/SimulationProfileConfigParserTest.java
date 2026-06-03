package com.plantops.scenario.planning.simulation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SimulationProfileConfigParserTest {

    @Inject
    SimulationProfileConfigParser parser;

    @Test
    void parsesTimingAndIncrementalRuleFlags() {
        String json = """
                {
                  "timing": {
                    "maxRoutingIterations": 8,
                    "rules": { "changeover": { "enabled": false } }
                  },
                  "incremental": {
                    "rules": { "routing-successor": { "enabled": false } }
                  },
                  "validation": { "blockConfirmOnHard": true }
                }
                """;
        SimulationProfileSettings settings = parser.parse("SP-TEST", json);
        assertEquals(8, settings.maxRoutingIterations());
        assertTrue(settings.blockConfirmOnHard());
        assertFalse(settings.isRuleEnabled("changeover", true));
        assertFalse(settings.isRuleEnabled("routing-successor", true));
        assertTrue(settings.isRuleEnabled("parallel-mate", true));
    }

    @Test
    void mergeOverridesAppliesRequestOnlyForSimulate() {
        SimulationProfileSettings base = parser.parse("SP-DEFAULT", SimulationProfileConfigParser.DEFAULT_CONFIG_JSON);
        SimulationProfileSettings merged = parser.mergeOverrides(
                base,
                Map.of("changeover", Map.of("enabled", false)));
        assertFalse(merged.isRuleEnabled("changeover", true));
    }
}
