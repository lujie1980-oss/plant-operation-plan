package com.plantops.scenario.planning.optimizer;

public record PlanningDiagnostic(
        String severity,
        String reasonCode,
        String message,
        String entityId) {
}
