package com.plantops.api.dto;

import java.time.LocalDateTime;

public record DispatchResultDto(
        String planVersionId,
        LocalDateTime dispatchedTs,
        String status
) {
}
