package com.plantops.api.dto.planning;

public record MasterPlanSessionConfirmResultDto(String sessionId, String planVersionId, int allocationCount) {
}
