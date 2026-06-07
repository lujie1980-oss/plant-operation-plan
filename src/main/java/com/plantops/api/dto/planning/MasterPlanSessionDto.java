package com.plantops.api.dto.planning;

import java.time.LocalDateTime;

public record MasterPlanSessionDto(
        String sessionId,
        String planVersionId,
        int pispCount,
        int periodCount,
        LocalDateTime expiresAt) {
}
