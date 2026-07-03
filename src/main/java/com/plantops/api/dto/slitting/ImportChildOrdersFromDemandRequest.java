package com.plantops.api.dto.slitting;

import java.math.BigDecimal;
import java.util.List;

public record ImportChildOrdersFromDemandRequest(
        List<String> salesOrderNos,
        BigDecimal defaultWidthMm,
        BigDecimal defaultLengthMm,
        boolean skipExisting) {
}
