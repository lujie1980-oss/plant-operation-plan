package com.plantops.scenario.planning.optimizer;

/**
 * 主计划选优引擎插件（Timefold、OR-Tools 等实现同一契约）。
 */
public interface PlanningOptimizer {

    String engineId();

    OptimizerResult optimize(PlanningProblem problem) throws PlanningOptimizerException;
}
