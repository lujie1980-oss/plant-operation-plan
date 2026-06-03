package com.plantops.api.dto.planning;

import java.util.List;

public record SimulateScheduleSessionRequest(
        List<SessionStepPatchDto> stepPatches,
        List<String> affectedOperationIds,
        Boolean fullReschedule) {

    public boolean resolveFullReschedule() {
        return Boolean.TRUE.equals(fullReschedule);
    }
}
