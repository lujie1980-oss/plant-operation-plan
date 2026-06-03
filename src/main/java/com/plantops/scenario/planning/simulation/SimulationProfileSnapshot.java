package com.plantops.scenario.planning.simulation;

/** Session 创建时冻结的推演配置快照（避免推演中途主数据变更）。 */
public record SimulationProfileSnapshot(String profileId, String configJson) {
}
