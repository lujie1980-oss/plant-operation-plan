package com.plantops.ontology;

import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyLoaderPlanVersionHydrationTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-HYDRATE";
    private static final String PRODUCT_CODE = "FG-OTD-HYDRATE";
    private static final String WORK_ORDER_NO = "WO-OTD-HYDRATE";
    private static final String RESOURCE_ID = "RES-OTD-HYDRATE";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loadForPlanVersionHydratesOperationTimesAndSrpReserved() {
        LocalDate slotDate = LocalDate.now();
        ensureFixture(slotDate);

        OntologyGraph graph = loader.loadForPlanVersion(PLAN_VERSION_ID);
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());

        Operation operation = graph.operationsForSupplyOrder(WORK_ORDER_NO).get(0);
        assertNotNull(operation.getPlannedStartTotal(), "published allocation should project planned start");
        assertTrue(operation.isLocked(), "allocated operation should be locked");

        String srpId = OntologyIds.srpId(RESOURCE_ID, periodIndex.sequenceFor(slotDate));
        StandardResourcePeriod srp = graph.srp(srpId);
        assertNotNull(srp);
        assertEquals(360.0, srp.getReservedCapacity(), 1e-6);
    }

    private void ensureFixture(LocalDate slotDate) {
        if (PlanVersionEntity.findByVersionId(PLAN_VERSION_ID) == null) {
            PlanVersionEntity planVersion = new PlanVersionEntity();
            planVersion.planVersionId = PLAN_VERSION_ID;
            planVersion.planType = "MASTER_PLAN";
            planVersion.planGeneratedTs = LocalDateTime.now();
            planVersion.stampWorkspace();
            planVersion.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-HYDRATE";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("50");
            workOrder.needDate = slotDate.plusDays(3);
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        if (ProductionLineEntity.findByLineId("LINE-OTD-HYDRATE") == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = "LINE-OTD-HYDRATE";
            line.areaId = "AREA-OTD-HYDRATE";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 100;
            line.stampWorkspace();
            line.persist();
        }

        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, RESOURCE_ID) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = RESOURCE_ID;
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }

        String allocationId = WORK_ORDER_NO + "@OP1_0#0";
        if (MasterPlanAllocationEntity.find(
                        "workspaceId = ?1 and planVersionId = ?2 and allocationId = ?3",
                        WorkspaceResolver.currentWorkspaceId(),
                        PLAN_VERSION_ID,
                        allocationId)
                .firstResult() == null) {
            MasterPlanAllocationEntity allocation = new MasterPlanAllocationEntity();
            allocation.planVersionId = PLAN_VERSION_ID;
            allocation.allocationId = allocationId;
            allocation.workOrderNo = WORK_ORDER_NO;
            allocation.productCode = PRODUCT_CODE;
            allocation.salesOrderNo = "SO-OTD-HYDRATE";
            allocation.salesOrderLineNo = 1;
            allocation.resourceId = RESOURCE_ID;
            allocation.slotIndex = 0;
            allocation.slotDate = slotDate;
            allocation.shiftId = "DAY";
            allocation.durationMinutes = 360;
            allocation.stampWorkspace();
            allocation.persist();
        }
    }
}
