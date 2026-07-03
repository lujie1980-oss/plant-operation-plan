package com.plantops.scenario;

import com.plantops.api.dto.SalesOrderDemandRescaleResultDto;
import com.plantops.api.dto.WorkOrderGenerationBatchResultDto;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量缩放销售订单行数量，并重建 MRP 工单（本体 {@code CustomerOrderLineDelivery} 随订单数量重算）。
 */
@ApplicationScoped
public class SalesOrderDemandRescaleService {

  private static final BigDecimal MIN_ORDER_QTY = new BigDecimal("0.0001");

  @Inject
  Instance<SalesOrderDemandRescaleService> self;

  @Inject
  WorkOrderGenerationService workOrderGenerationService;

  public SalesOrderDemandRescaleResultDto rescaleAndRegenerate(BigDecimal divisor, boolean replaceWorkOrders) {
    if (divisor == null || divisor.compareTo(BigDecimal.ONE) <= 0) {
      throw new BadRequestException("divisor 必须大于 1");
    }
    List<SalesOrderDemandRescaleResultDto.LineChange> changes = self.get().scaleOrderQuantities(divisor);
    WorkOrderGenerationBatchResultDto workOrders = replaceWorkOrders
        ? workOrderGenerationService.generateForAllOpenOrders(true)
        : null;
    return new SalesOrderDemandRescaleResultDto(divisor, changes.size(), changes, workOrders);
  }

  @Transactional(TxType.REQUIRES_NEW)
  List<SalesOrderDemandRescaleResultDto.LineChange> scaleOrderQuantities(BigDecimal divisor) {
    List<SalesOrderDemandRescaleResultDto.LineChange> changes = new ArrayList<>();
    for (SalesOrderLineEntity line : SalesOrderLineEntity.listInWorkspace()) {
      if ("CANCELLED".equals(line.status) || line.orderQty == null) {
        continue;
      }
      BigDecimal before = line.orderQty;
      BigDecimal after = before.divide(divisor, 4, RoundingMode.HALF_UP);
      if (after.compareTo(MIN_ORDER_QTY) < 0) {
        after = MIN_ORDER_QTY;
      }
      if (after.compareTo(before) == 0) {
        continue;
      }
      line.orderQty = after;
      line.promiseDate = null;
      changes.add(new SalesOrderDemandRescaleResultDto.LineChange(
          line.salesOrderNo,
          line.salesOrderLineNo,
          before,
          after));
    }
    return changes;
  }
}
