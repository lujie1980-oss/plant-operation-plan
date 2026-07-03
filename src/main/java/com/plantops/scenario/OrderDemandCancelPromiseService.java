package com.plantops.scenario;

import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * 取消订单承诺（SCN-01f · RULE-FF-03）：仅清空承诺交期，不改动 pegging / 工单。
 */
@ApplicationScoped
public class OrderDemandCancelPromiseService {

    public record CancelPromiseResult(boolean cleared, String message) {}

    @Transactional
    public CancelPromiseResult cancelForOrderLine(String salesOrderNo, int salesOrderLineNo) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, salesOrderLineNo);
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("销售订单行不存在: " + salesOrderNo + "-" + salesOrderLineNo);
        }
        if (order.promiseDate == null) {
            return new CancelPromiseResult(false, "当前无承诺交期");
        }
        order.promiseDate = null;
        return new CancelPromiseResult(true, "已取消承诺交期");
    }
}
