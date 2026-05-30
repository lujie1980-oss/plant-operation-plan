package com.plantops.api.dto;

import java.time.LocalDate;

public record LineOpeningDecisionDto(
        String areaId,
        String lineId,
        String shiftId,
        LocalDate date,
        boolean opened,
        int suggestedHeadcount
) {
}
