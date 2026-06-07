package com.plantops.api.dto.planning;

public record SimulateMasterPlanSessionRequest(
        String pispPeriodId,
        String property,
        Double value) {
}
