package com.plantops.api.dto;

import java.time.LocalDate;

public record LineOpeningSuggestionDto(
        String areaId,
        String lineId,
        String shiftId,
        LocalDate date,
        boolean open,
        int suggestedHeadcount,
        String reason
) {
}
