package com.plantops.scenario.planning.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class SimulationProfileConfigParser {

    public static final String DEFAULT_CONFIG_JSON = """
            {
              "timing": {
                "maxRoutingIterations": 16,
                "rules": {
                  "factory-calendar": { "enabled": false },
                  "feedback-freeze": { "enabled": false }
                }
              },
              "incremental": {
                "rules": {
                  "batch-continuous": { "enabled": false }
                }
              },
              "validation": { "blockConfirmOnHard": false }
            }
            """;

    @Inject
    ObjectMapper objectMapper;

    public SimulationProfileSettings parse(String profileId, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return SimulationProfileSettings.defaults(profileId);
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            int maxIter = root.path("timing").path("maxRoutingIterations").asInt(
                    SimulationProfileSettings.DEFAULT_MAX_ROUTING_ITERATIONS);
            boolean blockConfirm = root.path("validation").path("blockConfirmOnHard").asBoolean(false);
            Map<String, Boolean> rules = new LinkedHashMap<>();
            mergeRuleFlags(rules, root.path("timing").path("rules"));
            mergeRuleFlags(rules, root.path("incremental").path("rules"));
            return new SimulationProfileSettings(profileId, maxIter, Map.copyOf(rules), blockConfirm);
        } catch (Exception e) {
            return SimulationProfileSettings.defaults(profileId);
        }
    }

    public SimulationProfileSettings mergeOverrides(
            SimulationProfileSettings base,
            Map<String, Map<String, Object>> requestOverrides) {
        if (requestOverrides == null || requestOverrides.isEmpty()) {
            return base;
        }
        Map<String, Boolean> merged = new LinkedHashMap<>(base.ruleEnabledByKey());
        for (var entry : requestOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Object enabled = entry.getValue().get("enabled");
            if (enabled instanceof Boolean bool) {
                merged.put(entry.getKey(), bool);
            }
        }
        return new SimulationProfileSettings(
                base.profileId(),
                base.maxRoutingIterations(),
                Map.copyOf(merged),
                base.blockConfirmOnHard());
    }

    private static void mergeRuleFlags(Map<String, Boolean> target, JsonNode rulesNode) {
        if (rulesNode == null || !rulesNode.isObject()) {
            return;
        }
        rulesNode.fields().forEachRemaining(entry -> {
            JsonNode enabled = entry.getValue().path("enabled");
            if (enabled.isBoolean()) {
                target.put(entry.getKey(), enabled.booleanValue());
            }
        });
    }
}
