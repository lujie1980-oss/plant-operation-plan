package com.plantops.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesOrderDemandRescaleResultDto(
    BigDecimal divisor,
    int linesUpdated,
    List<LineChange> changes,
    WorkOrderGenerationBatchResultDto workOrders) {

  public record LineChange(
      String salesOrderNo,
      int salesOrderLineNo,
      BigDecimal orderQtyBefore,
      BigDecimal orderQtyAfter) {}
}
