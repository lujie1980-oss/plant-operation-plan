package com.plantops.scenario.planning.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleProblemFacts;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** 一次 simulate / 赋时共享上下文（Phase 2 扩展 Profile 参数）。 */
public record SimulationRuleContext(
        DetailSchedule schedule,
        DetailScheduleProblemFacts facts,
        Set<String> enabledRuleTypes,
        Map<String, JsonNode> ruleParams,
        SimulationMode mode,
        Set<String> seedOperationIds,
        LocalDate planningAnchorDate,
        SimulationProfileSettings profileSettings) {

    public SimulationRuleContext {
        if (profileSettings == null) {
            profileSettings = SimulationProfileSettings.defaults(null);
        }
    }
}
