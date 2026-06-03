package com.plantops.scenario;

import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 取消订单行计划：移除 pegging；仅服务本行的工单一并删除。
 */
@ApplicationScoped
public class OrderDemandCancelPlanService {

    public record CancelPlanSummary(int peggingRemoved, int workOrdersDeleted, int workOrdersRetained) {}

    @Transactional
    public CancelPlanSummary cancelForOrderLine(String salesOrderNo, int salesOrderLineNo) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, salesOrderLineNo);
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("销售订单行不存在: " + salesOrderNo + "-" + salesOrderLineNo);
        }

        List<WorkOrderPeggingEntity> orderPegs =
                WorkOrderPeggingEntity.findByOrderLine(salesOrderNo, salesOrderLineNo);
        if (orderPegs.isEmpty()) {
            return new CancelPlanSummary(0, 0, 0);
        }

        Set<String> woNos = new HashSet<>();
        for (WorkOrderPeggingEntity peg : orderPegs) {
            woNos.add(peg.workOrderNo);
        }

        List<WorkOrderEntity> workOrders = woNos.stream()
                .map(WorkOrderEntity::findByNo)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((WorkOrderEntity wo) -> wo.bomLevel).reversed()
                        .thenComparing(wo -> wo.workOrderNo))
                .toList();

        int peggingRemoved = 0;
        int workOrdersDeleted = 0;
        int workOrdersRetained = 0;

        for (WorkOrderEntity wo : workOrders) {
            List<WorkOrderPeggingEntity> allPegs = WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo);
            if (shouldRetainWorkOrder(allPegs, salesOrderNo, salesOrderLineNo, wo)) {
                peggingRemoved += WorkOrderPeggingEntity.deleteForOrderLineAndWorkOrder(
                        salesOrderNo, salesOrderLineNo, wo.workOrderNo);
                workOrdersRetained++;
            } else {
                peggingRemoved += (int) allPegs.stream()
                        .filter(p -> matchesOrderLine(p, salesOrderNo, salesOrderLineNo))
                        .count();
                deleteWorkOrder(wo.workOrderNo);
                workOrdersDeleted++;
            }
        }

        for (String woNo : woNos) {
            if (workOrders.stream().noneMatch(wo -> wo.workOrderNo.equals(woNo))) {
                peggingRemoved += WorkOrderPeggingEntity.deleteForOrderLineAndWorkOrder(
                        salesOrderNo, salesOrderLineNo, woNo);
            }
        }

        order.promiseDate = null;

        return new CancelPlanSummary(peggingRemoved, workOrdersDeleted, workOrdersRetained);
    }

    static boolean shouldRetainWorkOrder(
            List<WorkOrderPeggingEntity> allPegs,
            String salesOrderNo,
            int salesOrderLineNo,
            WorkOrderEntity wo) {
        if (WorkOrderService.DISPATCH_DISPATCHED.equals(wo.dispatchStatus)) {
            return true;
        }
        return allPegs.stream().anyMatch(p -> !matchesOrderLine(p, salesOrderNo, salesOrderLineNo));
    }

    static boolean matchesOrderLine(WorkOrderPeggingEntity peg, String salesOrderNo, int salesOrderLineNo) {
        return peg.salesOrderNo.equals(salesOrderNo) && peg.salesOrderLineNo == salesOrderLineNo;
    }

    private static void deleteWorkOrder(String workOrderNo) {
        WorkOrderBomDependencyEntity.deleteForWorkOrders(List.of(workOrderNo));
        WorkOrderPeggingEntity.deleteForWorkOrder(workOrderNo);
        WorkOrderEntity.delete("workspaceId = ?1 and workOrderNo = ?2", WorkOrderEntity.ws(), workOrderNo);
    }
}
