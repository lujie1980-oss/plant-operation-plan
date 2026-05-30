package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "work_order_pegging")
public class WorkOrderPeggingEntity extends WorkspaceScopedEntity {

    public String workOrderNo;
    public String salesOrderNo;
    public int salesOrderLineNo;
    public String finishedProductCode;
    public BigDecimal peggedQty;
    public LocalDate needDate;

    public static List<WorkOrderPeggingEntity> findByWorkOrder(String workOrderNo) {
        return list(
                "workspaceId = ?1 and workOrderNo = ?2 order by salesOrderNo, salesOrderLineNo",
                ws(),
                workOrderNo);
    }

    public static List<WorkOrderPeggingEntity> findByOrderLine(String salesOrderNo, int salesOrderLineNo) {
        return list(
                "workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3",
                ws(),
                salesOrderNo,
                salesOrderLineNo);
    }

    public static void deleteForWorkOrder(String workOrderNo) {
        delete("workspaceId = ?1 and workOrderNo = ?2", ws(), workOrderNo);
    }

    public static void deleteForWorkOrders(List<String> workOrderNos) {
        if (workOrderNos == null || workOrderNos.isEmpty()) {
            return;
        }
        delete("workspaceId = ?1 and workOrderNo in ?2", ws(), workOrderNos);
    }

    public static void deleteAllRegeneratable() {
        delete("workspaceId = ?1", ws());
    }
}
