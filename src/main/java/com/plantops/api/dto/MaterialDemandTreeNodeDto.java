package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MaterialDemandTreeNodeDto(
        String nodeId,
        String nodeType,
        String label,
        String productCode,
        LocalDate needDate,
        BigDecimal quantity,
        List<MaterialDemandTreeNodeDto> children) {
}
