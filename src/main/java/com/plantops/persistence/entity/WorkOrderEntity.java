package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;



@Entity

@Table(name = "work_order", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "work_order_no"

}))

public class WorkOrderEntity extends WorkspaceScopedEntity {



    public String workOrderNo;

    public String salesOrderNo;

    public int salesOrderLineNo;

    public String productCode;

    public BigDecimal quantity;

    public String resourceId;

    public int sequenceNo;

    public String parentWorkOrderNo;

    public String dispatchStatus;

    public LocalDateTime dispatchedTs;

    /** MRP 最晚完工日 */
    public LocalDate needDate;

    public int bomLevel;

    /** MRP / MANUAL */
    public String sourceType;

    public static final String SOURCE_MRP = "MRP";
    public static final String SOURCE_MANUAL = "MANUAL";



    public static List<WorkOrderEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static List<WorkOrderEntity> listAllOrdered() {

        return list("workspaceId = ?1 order by sequenceNo", ws());

    }



    public static WorkOrderEntity findByNo(String workOrderNo) {

        return find("workspaceId = ?1 and workOrderNo = ?2", ws(), workOrderNo).firstResult();

    }



    public static List<WorkOrderEntity> findChildren(String parentWorkOrderNo) {

        return list("workspaceId = ?1 and parentWorkOrderNo = ?2", ws(), parentWorkOrderNo);

    }



    public static WorkOrderEntity findRootForOrderLine(String salesOrderNo, int lineNo, String productCode) {

        return find(

                "workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3 and productCode = ?4 and parentWorkOrderNo is null",

                ws(), salesOrderNo, lineNo, productCode)

                .firstResult();

    }



    public static List<WorkOrderEntity> findForOrderLine(String salesOrderNo, int lineNo) {

        return list("workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3 order by sequenceNo",

                ws(), salesOrderNo, lineNo);

    }



    public static void deleteForOrderLine(String salesOrderNo, int lineNo) {

        delete("workspaceId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3", ws(), salesOrderNo, lineNo);

    }



    public static int nextSequenceNo() {

        WorkOrderEntity last = find("workspaceId = ?1 order by sequenceNo desc", ws()).firstResult();

        return last != null ? last.sequenceNo + 1 : 1;

    }

    public static List<WorkOrderEntity> listMrpRegeneratable() {
        return list(
                "workspaceId = ?1 and (sourceType is null or sourceType = ?2) and (dispatchStatus is null or dispatchStatus <> ?3)",
                ws(),
                SOURCE_MRP,
                "DISPATCHED");
    }

    public static void deleteMrpRegeneratable() {
        List<WorkOrderEntity> rows = listMrpRegeneratable();
        if (rows.isEmpty()) {
            return;
        }
        List<String> nos = rows.stream().map(wo -> wo.workOrderNo).toList();
        WorkOrderBomDependencyEntity.deleteForWorkOrders(nos);
        WorkOrderPeggingEntity.deleteForWorkOrders(nos);
        delete("workspaceId = ?1 and workOrderNo in ?2", ws(), nos);
    }

    public static List<WorkOrderEntity> findByPeggingOrderLine(String salesOrderNo, int lineNo, String productCode) {
        return getEntityManager()
                .createQuery(
                        """
                        select wo from WorkOrderEntity wo
                        where wo.workspaceId = :ws
                          and wo.productCode = :product
                          and wo.workOrderNo in (
                            select p.workOrderNo from WorkOrderPeggingEntity p
                            where p.workspaceId = :ws
                              and p.salesOrderNo = :so
                              and p.salesOrderLineNo = :line
                          )
                        order by wo.bomLevel, wo.sequenceNo
                        """,
                        WorkOrderEntity.class)
                .setParameter("ws", ws())
                .setParameter("product", productCode)
                .setParameter("so", salesOrderNo)
                .setParameter("line", lineNo)
                .getResultList();
    }

    public static WorkOrderEntity findChildByDependency(String parentWorkOrderNo, String productCode) {
        List<WorkOrderBomDependencyEntity> deps = WorkOrderBomDependencyEntity.findByParent(parentWorkOrderNo);
        for (WorkOrderBomDependencyEntity dep : deps) {
            WorkOrderEntity child = findByNo(dep.childWorkOrderNo);
            if (child != null && productCode.equals(child.productCode)) {
                return child;
            }
        }
        return null;
    }

}


