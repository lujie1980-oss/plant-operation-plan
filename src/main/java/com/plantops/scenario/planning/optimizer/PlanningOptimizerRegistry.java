package com.plantops.scenario.planning.optimizer;

import com.plantops.config.ParameterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class PlanningOptimizerRegistry {

    public static final String PARAM_ENGINE = "planning_optimizer_engine";

    private final Map<String, PlanningOptimizer> optimizersById = new LinkedHashMap<>();

    @Inject
    ParameterRegistry parameters;

    @Inject
    public PlanningOptimizerRegistry(Instance<PlanningOptimizer> optimizers) {
        for (PlanningOptimizer optimizer : optimizers) {
            optimizersById.put(optimizer.engineId(), optimizer);
        }
    }

    public PlanningOptimizer requireDefault() {
        String engineId = parameters.get(PARAM_ENGINE);
        if (engineId == null || engineId.isBlank()) {
            throw new BadRequestException("System parameter planning_optimizer_engine is not configured");
        }
        PlanningOptimizer optimizer = optimizersById.get(engineId.trim());
        if (optimizer == null) {
            throw new BadRequestException("Unknown planning optimizer engine: " + engineId);
        }
        return optimizer;
    }

    public PlanningOptimizer require(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            return requireDefault();
        }
        PlanningOptimizer optimizer = optimizersById.get(engineId);
        if (optimizer == null) {
            throw new BadRequestException("Unknown planning optimizer engine: " + engineId);
        }
        return optimizer;
    }
}
