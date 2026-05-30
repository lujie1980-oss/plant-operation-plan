package com.plantops.api.dto;

import java.util.List;

public record MasterPlanObjectivesUpdateRequest(
        List<MasterPlanObjectiveUpdateDto> objectives) {
}
