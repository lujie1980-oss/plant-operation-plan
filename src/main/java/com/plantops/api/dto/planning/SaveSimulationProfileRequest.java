package com.plantops.api.dto.planning;

public record SaveSimulationProfileRequest(
        String profileId,
        String name,
        String layer,
        String masterPlanVersionId,
        String configJson,
        Boolean active) {
}
