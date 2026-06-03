package com.plantops.scenario.planning.simulation;

import java.util.Map;

/** 从 config_json + 请求 override 解析出的运行时推演开关。 */
public record SimulationProfileSettings(
        String profileId,
        int maxRoutingIterations,
        Map<String, Boolean> ruleEnabledByKey,
        boolean blockConfirmOnHard) {

    public static final int DEFAULT_MAX_ROUTING_ITERATIONS = 16;

    public static SimulationProfileSettings defaults(String profileId) {
        return new SimulationProfileSettings(
                profileId,
                DEFAULT_MAX_ROUTING_ITERATIONS,
                Map.of(),
                false);
    }

    public boolean isRuleEnabled(String key, boolean whenAbsent) {
        if (key == null || key.isBlank()) {
            return whenAbsent;
        }
        Boolean explicit = ruleEnabledByKey.get(key);
        return explicit != null ? explicit : whenAbsent;
    }
}
