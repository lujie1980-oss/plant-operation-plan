package com.plantops.api.dto;

import java.time.LocalDate;

public record MaterialPeriodHeaderDto(
        String periodId,
        int sequenceNr,
        LocalDate startDate,
        LocalDate endDate,
        String label) {
}
