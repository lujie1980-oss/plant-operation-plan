package com.plantops.api.dto.planning;

import java.time.LocalDate;

public record CreateMasterPlanSessionRequest(
        String planVersionId,
        LocalDate planningStart) {
}
