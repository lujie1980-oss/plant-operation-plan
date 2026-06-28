package com.plantops.scenario;

import com.plantops.api.dto.CapacityBucketWorkOrderDto;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CapacityBucketWorkOrderResolver {

    public List<CapacityBucketWorkOrderDto> resolve(
            String resourceId,
            LocalDate date,
            String shiftId,
            String masterPlanVersionId) {
        Map<String, CapacityBucketWorkOrderDto> byWo = new LinkedHashMap<>();
        if (masterPlanVersionId != null) {
            List<MasterPlanAllocationEntity> allocRows = MasterPlanAllocationEntity
                    .find("planVersionId = ?1 and resourceId = ?2 and slotDate = ?3 and shiftId = ?4",
                            masterPlanVersionId, resourceId, date, shiftId)
                    .list();
            for (MasterPlanAllocationEntity alloc : allocRows) {
                WorkOrderEntity wo = alloc.workOrderNo != null
                        ? WorkOrderEntity.findByNo(alloc.workOrderNo)
                        : WorkOrderEntity.findRootForOrderLine(
                                alloc.salesOrderNo, alloc.salesOrderLineNo, findProductForLine(alloc));
                if (wo == null) {
                    continue;
                }

                int minutes = alloc.durationMinutes != null && alloc.durationMinutes > 0
                        ? alloc.durationMinutes
                        : workOrderMinutes(wo);

                String rowKey = alloc.allocationId != null ? alloc.allocationId : wo.workOrderNo;

                boolean feedbackLocked = alloc.allocationId != null && alloc.allocationId.startsWith("FB-");
                SalesOrderRef orderRef = resolveSalesOrderRef(wo, alloc);
                byWo.putIfAbsent(rowKey, new CapacityBucketWorkOrderDto(
                        wo.workOrderNo,
                        orderRef.salesOrderNo(),
                        orderRef.salesOrderLineNo(),
                        wo.productCode,
                        wo.quantity,
                        minutes,
                        "主计划",
                        feedbackLocked));
            }
        }

        if (byWo.isEmpty() && masterPlanVersionId == null) {
            for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
                if (!order.dueDate.equals(date)) {
                    continue;
                }
                boolean canProduce = ProductResourceEntity.listInWorkspace().stream()
                        .anyMatch(pr -> pr.productCode.equals(order.productCode) && pr.resourceId.equals(resourceId));
                if (!canProduce) {
                    continue;
                }
                WorkOrderEntity wo = WorkOrderEntity.findRootForOrderLine(
                        order.salesOrderNo, order.salesOrderLineNo, order.productCode);
                if (wo == null || !resourceId.equals(wo.resourceId)) {
                    continue;
                }
                int minutes = workOrderMinutes(wo);
                SalesOrderRef orderRef = resolveSalesOrderRef(wo, null);
                byWo.putIfAbsent(
                        wo.workOrderNo,
                        new CapacityBucketWorkOrderDto(
                                wo.workOrderNo,
                                orderRef.salesOrderNo(),
                                orderRef.salesOrderLineNo(),
                                wo.productCode,
                                wo.quantity,
                                minutes,
                                "需求测算"));
            }
        }

        return byWo.values().stream()
                .sorted(Comparator.comparing(CapacityBucketWorkOrderDto::workOrderNo))
                .toList();
    }

    private String findProductForLine(MasterPlanAllocationEntity alloc) {
        SalesOrderLineEntity line = SalesOrderLineEntity.findByKey(alloc.salesOrderNo, alloc.salesOrderLineNo);
        return line != null ? line.productCode : "";
    }

    private int workOrderMinutes(WorkOrderEntity wo) {
        return ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
    }

    private static SalesOrderRef resolveSalesOrderRef(WorkOrderEntity wo, MasterPlanAllocationEntity alloc) {
        if (wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()) {
            return new SalesOrderRef(wo.salesOrderNo, wo.salesOrderLineNo);
        }
        if (alloc != null && alloc.salesOrderNo != null && !alloc.salesOrderNo.isBlank()) {
            return new SalesOrderRef(alloc.salesOrderNo, alloc.salesOrderLineNo);
        }
        WorkOrderScheduleContext ctx = WorkOrderScheduleContext.resolve(wo);
        if (ctx.salesOrderNo != null && !ctx.salesOrderNo.isBlank()) {
            return new SalesOrderRef(ctx.salesOrderNo, ctx.salesOrderLineNo);
        }
        return new SalesOrderRef(null, 0);
    }

    private record SalesOrderRef(String salesOrderNo, int salesOrderLineNo) {
    }
}
