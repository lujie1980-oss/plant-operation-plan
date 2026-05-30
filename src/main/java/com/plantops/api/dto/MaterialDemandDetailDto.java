package com.plantops.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MaterialDemandDetailDto(
        String productCode,
        List<MaterialDemandTreeNodeDto> roots,
        BigDecimal totalQuantity,
        int pathCount) {
}
