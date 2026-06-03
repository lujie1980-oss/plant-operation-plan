package com.plantops.api.dto.execution;

public record CreateScheduleSessionRequest(
        String masterPlanVersionId,
        Boolean seedInitialQueues,
        Boolean solve,
        String simulationProfileId) {

    public boolean resolveSeedInitialQueues() {
        return Boolean.TRUE.equals(seedInitialQueues);
    }

    public boolean resolveSolve() {
        return Boolean.TRUE.equals(solve);
    }
}
