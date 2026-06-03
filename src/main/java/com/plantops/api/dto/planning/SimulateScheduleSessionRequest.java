package com.plantops.api.dto.planning;

import java.util.List;
import java.util.Map;

public record SimulateScheduleSessionRequest(
        List<SessionStepPatchDto> stepPatches,
        List<String> affectedOperationIds,
        Boolean fullReschedule,
        String simulationProfileId,
        Map<String, Map<String, Object>> ruleOverrides) {

    public boolean resolveFullReschedule() {
        return Boolean.TRUE.equals(fullReschedule);
    }
}
