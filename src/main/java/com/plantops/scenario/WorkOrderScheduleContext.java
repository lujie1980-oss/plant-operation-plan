package com.plantops.scenario;

import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;

import java.time.LocalDate;
import java.util.List;

/** 合并 MRP 工单的排程交期/优先级解析（来自 pegging 或 legacy 订单行字段）。 */
public final class WorkOrderScheduleContext {

    public final LocalDate dueDate;
    public final int priority;
    public final String salesOrderNo;
    public final int salesOrderLineNo;
    public final boolean anyOrderLocked;
    public final boolean schedulable;

    private WorkOrderScheduleContext(
            LocalDate dueDate,
            int priority,
            String salesOrderNo,
            int salesOrderLineNo,
            boolean anyOrderLocked,
            boolean schedulable) {
        this.dueDate = dueDate;
        this.priority = priority;
        this.salesOrderNo = salesOrderNo;
        this.salesOrderLineNo = salesOrderLineNo;
        this.anyOrderLocked = anyOrderLocked;
        this.schedulable = schedulable;
    }

    public static WorkOrderScheduleContext resolve(WorkOrderEntity wo) {
        if (wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()) {
            SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
            if (order == null || "CANCELLED".equals(order.status)) {
                return notSchedulable();
            }
            return new WorkOrderScheduleContext(
                    order.dueDate,
                    order.priority,
                    order.salesOrderNo,
                    order.salesOrderLineNo,
                    order.scheduleLockFlag,
                    true);
        }

        List<WorkOrderPeggingEntity> pegs = WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo);
        if (pegs.isEmpty()) {
            LocalDate due = wo.needDate != null ? wo.needDate : LocalDate.now().plusDays(14);
            return new WorkOrderScheduleContext(due, 5, "", 0, false, true);
        }

        LocalDate due = wo.needDate;
        int priority = Integer.MAX_VALUE;
        String primarySo = pegs.get(0).salesOrderNo;
        int primaryLine = pegs.get(0).salesOrderLineNo;
        boolean locked = false;
        boolean anyActive = false;

        for (WorkOrderPeggingEntity peg : pegs) {
            SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(peg.salesOrderNo, peg.salesOrderLineNo);
            if (order == null || "CANCELLED".equals(order.status)) {
                continue;
            }
            anyActive = true;
            if (due == null || order.dueDate.isBefore(due)) {
                due = order.dueDate;
                primarySo = order.salesOrderNo;
                primaryLine = order.salesOrderLineNo;
            }
            if (order.priority < priority) {
                priority = order.priority;
            }
            if (order.scheduleLockFlag) {
                locked = true;
            }
        }

        if (!anyActive) {
            return notSchedulable();
        }
        if (due == null) {
            due = LocalDate.now().plusDays(14);
        }
        if (priority == Integer.MAX_VALUE) {
            priority = 5;
        }
        return new WorkOrderScheduleContext(due, priority, primarySo, primaryLine, locked, true);
    }

    private static WorkOrderScheduleContext notSchedulable() {
        return new WorkOrderScheduleContext(LocalDate.now(), 5, "", 0, false, false);
    }
}
