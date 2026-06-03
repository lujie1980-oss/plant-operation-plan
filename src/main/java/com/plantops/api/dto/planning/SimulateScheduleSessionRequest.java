package com.plantops.api.dto.planning;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

public record SimulateScheduleSessionRequest(
        List<SessionStepPatchDto> stepPatches,
        List<String> affectedOperationIds,
        Boolean fullReschedule,
        String simulationProfileId,
        Map<String, Map<String, Object>> ruleOverrides,
        String feedbackCutoff) {

    public boolean resolveFullReschedule() {
        return Boolean.TRUE.equals(fullReschedule);
    }

    public LocalDate resolveFeedbackCutoff() {
        if (feedbackCutoff == null || feedbackCutoff.isBlank()) {
            return null;
        }
        return LocalDate.parse(feedbackCutoff);
    }
}
