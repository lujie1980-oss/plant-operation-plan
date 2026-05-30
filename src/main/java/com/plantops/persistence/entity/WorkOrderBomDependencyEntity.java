package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "work_order_bom_dependency")
public class WorkOrderBomDependencyEntity extends WorkspaceScopedEntity {

    public String parentWorkOrderNo;
    public String childWorkOrderNo;

    public static List<WorkOrderBomDependencyEntity> findByParent(String parentWorkOrderNo) {
        return list("workspaceId = ?1 and parentWorkOrderNo = ?2", ws(), parentWorkOrderNo);
    }

    public static List<WorkOrderBomDependencyEntity> findByChild(String childWorkOrderNo) {
        return list("workspaceId = ?1 and childWorkOrderNo = ?2", ws(), childWorkOrderNo);
    }

    public static void deleteForWorkOrders(List<String> workOrderNos) {
        if (workOrderNos == null || workOrderNos.isEmpty()) {
            return;
        }
        delete(
                "workspaceId = ?1 and (parentWorkOrderNo in ?2 or childWorkOrderNo in ?2)",
                ws(),
                workOrderNos);
    }

    public static void deleteAllRegeneratable() {
        delete("workspaceId = ?1", ws());
    }

    public static List<WorkOrderBomDependencyEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
