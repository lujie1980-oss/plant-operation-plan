package com.plantops.scenario;

import com.plantops.api.dto.WorkOrderGenerationBatchResultDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;
import com.plantops.domain.SalesOrderLineId;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.util.Set;

/**
 * 工单生成入口：委托场景级 {@link MrpExplosionService} 按 BOM 层级汇总后生成合并工单。
 */
@ApplicationScoped
public class WorkOrderGenerationService {

    @Inject
    MrpExplosionService mrpExplosionService;

    @Transactional(TxType.NOT_SUPPORTED)
    public WorkOrderGenerationBatchResultDto generateForAllOpenOrders(boolean replaceExisting) {
        return mrpExplosionService.regenerateMergedWorkOrders(replaceExisting);
    }

    @Transactional(TxType.NOT_SUPPORTED)
    public int regenerateForAllOpenOrders() {
        return mrpExplosionService.regenerateMergedWorkOrdersSkipping(Set.of()).workOrdersCreated();
    }

    @Transactional(TxType.NOT_SUPPORTED)
    public int regenerateForAllOpenOrdersSkipping(Set<SalesOrderLineId> blocked) {
        return mrpExplosionService.regenerateMergedWorkOrdersSkipping(blocked).workOrdersCreated();
    }

    /**
     * 单行变更亦重跑全场景 MRP（合并工单无法仅重算一行）。
     */
    @Transactional(TxType.NOT_SUPPORTED)
    public WorkOrderGenerationResultDto generateForOrderLine(
            String salesOrderNo,
            int salesOrderLineNo,
            boolean replaceExisting) {
        WorkOrderGenerationBatchResultDto batch =
                mrpExplosionService.regenerateMergedWorkOrdersSkipping(Set.of(), replaceExisting);
        return batch.details().stream()
                .filter(d -> d.salesOrderNo().equals(salesOrderNo) && d.salesOrderLineNo() == salesOrderLineNo)
                .findFirst()
                .orElse(new WorkOrderGenerationResultDto(salesOrderNo, salesOrderLineNo, 0, java.util.List.of()));
    }

    @Transactional(TxType.NOT_SUPPORTED)
    public int ensureWorkOrdersForActiveOrders() {
        long open = com.plantops.persistence.entity.SalesOrderLineEntity.listInWorkspace().stream()
                .filter(o -> !"CANCELLED".equals(o.status))
                .count();
        long woCount = com.plantops.persistence.entity.WorkOrderEntity.listInWorkspace().stream()
                .filter(w -> WorkOrderEntity.SOURCE_MRP.equals(w.sourceType)
                        || w.sourceType == null)
                .count();
        if (open > 0 && woCount == 0) {
            return regenerateForAllOpenOrders();
        }
        return 0;
    }
}
