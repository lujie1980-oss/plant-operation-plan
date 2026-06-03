package com.plantops.scenario;

import com.plantops.persistence.entity.WorkOrderEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MrpExplosionServiceTest {

    @Test
    void isMrpRegeneratable_excludesDispatched() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.dispatchStatus = WorkOrderService.DISPATCH_DISPATCHED;
        assertTrue(!WorkOrderEntity.isMrpRegeneratable(wo));

        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        assertTrue(WorkOrderEntity.isMrpRegeneratable(wo));
    }
}
