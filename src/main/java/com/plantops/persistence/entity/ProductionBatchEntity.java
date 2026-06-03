package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_batch", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "batch_no"
}))
public class ProductionBatchEntity extends WorkspaceScopedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String KITTING_UNKNOWN = "UNKNOWN";
    public static final String KITTING_KITTED = "KITTED";
    public static final String KITTING_SHORT = "SHORT";

    public static final String SPLIT_MANUAL = "MANUAL";
    public static final String SPLIT_FIXED = "FIXED";
    public static final String SPLIT_KITTING = "KITTING";
    public static final String SPLIT_AUTO = "AUTO";
    /** 不拆批策略：工单下发时自动创建的整单默认批次。 */
    public static final String SPLIT_WHOLE = "WHOLE";

    public String batchNo;
    public String workOrderNo;
    public int batchSeq;
    public BigDecimal quantity;
    public String kittingStatus = KITTING_UNKNOWN;
    public String splitMethod;
    public String status = STATUS_ACTIVE;
    public Boolean pendingScheduleEligible = Boolean.TRUE;
    public LocalDateTime createdTs = LocalDateTime.now();

    public static ProductionBatchEntity findByBatchNo(String batchNo) {
        return find("workspaceId = ?1 and batchNo = ?2", ws(), batchNo).firstResult();
    }

    public static List<ProductionBatchEntity> listActiveByWorkOrder(String workOrderNo) {
        return list(
                "workspaceId = ?1 and workOrderNo = ?2 and status = ?3 order by batchSeq",
                ws(),
                workOrderNo,
                STATUS_ACTIVE);
    }

    public static List<ProductionBatchEntity> listActiveOrdered() {
        return list(
                "workspaceId = ?1 and status = ?2 order by workOrderNo, batchSeq",
                ws(),
                STATUS_ACTIVE);
    }

    public static BigDecimal sumActiveQuantity(String workOrderNo) {
        return listActiveByWorkOrder(workOrderNo).stream()
                .map(b -> b.quantity != null ? b.quantity : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static int nextBatchSeq(String workOrderNo) {
        Integer max = listActiveByWorkOrder(workOrderNo).stream()
                .mapToInt(b -> b.batchSeq)
                .max()
                .orElse(0);
        return max + 1;
    }

    public static void deleteActiveByWorkOrderNos(List<String> workOrderNos) {
        if (workOrderNos == null || workOrderNos.isEmpty()) {
            return;
        }
        delete("workspaceId = ?1 and workOrderNo in ?2", ws(), workOrderNos);
    }
}
