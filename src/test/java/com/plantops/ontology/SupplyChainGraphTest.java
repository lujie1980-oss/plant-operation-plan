package com.plantops.ontology;

import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.demand.ForecastDemand;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOutputMaterial;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ForecastDemandEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SupplyChainGraphTest {

    private static final String WORK_ORDER_NO = "WO-SC-CHAIN-TEST";
    private static final String SALES_ORDER_NO = "SO-SC-CHAIN-TEST";
    private static final String FG_CODE = "FG-SC-CHAIN-100";
    private static final String COMP_CODE = "RM-SC-CHAIN-200";
    private static final String FORECAST_ID = "FC-SC-CHAIN-001";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loadsCustomerOrderLineDeliveryAndForecastDemandChains() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        String colId = OntologyIds.customerOrderLineId(SALES_ORDER_NO, 1);
        CustomerOrderLine col = graph.customerOrderLine(colId);
        assertNotNull(col);
        assertEquals(FG_CODE, col.getProductCode());

        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        CustomerOrderLineDelivery cold = graph.customerOrderLineDelivery(coldId);
        assertNotNull(cold);
        assertEquals(colId, cold.getCustomerOrderLineId());
        assertEquals(40.0, cold.getDeliveryQty(), 1e-6);

        Demand customerDemand = graph.demand(OntologyIds.demandFromCustomerDeliveryId(coldId));
        assertNotNull(customerDemand);
        assertEquals(DemandSourceType.CUSTOMER_DELIVERY, customerDemand.getSourceType());
        assertEquals(coldId, customerDemand.getSourceId());

        String fcId = OntologyIds.forecastDemandId(FORECAST_ID);
        ForecastDemand forecast = graph.forecastDemand(fcId);
        assertNotNull(forecast);
        assertEquals(FG_CODE, forecast.getProductCode());

        Demand forecastDemand = graph.demand(OntologyIds.demandFromForecastId(fcId));
        assertNotNull(forecastDemand);
        assertEquals(DemandSourceType.FORECAST, forecastDemand.getSourceType());
        assertEquals(fcId, forecastDemand.getSourceId());
    }

    @Test
    @TestTransaction
    void expandsPlanUnitOperationsSupplyAndBomDemands() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        SupplyOrder so = graph.supplyOrder(WORK_ORDER_NO);
        assertNotNull(so);

        List<PlanUnit> planUnits = graph.planUnitsForSupplyOrder(WORK_ORDER_NO);
        assertEquals(1, planUnits.size());
        String planUnitId = OntologyIds.planUnitId(WORK_ORDER_NO, 0);
        assertEquals(planUnitId, planUnits.get(0).getId());

        List<Operation> ops = graph.operationsForSupplyOrder(WORK_ORDER_NO);
        assertEquals(2, ops.size());
        ops.forEach(op -> assertEquals(planUnitId, op.getPlanUnitId()));

        Operation lastOp = ops.get(1);
        List<OperationOutputMaterial> ooms = graph.operationOutputMaterialsForOperation(lastOp.getId());
        assertEquals(1, ooms.size());
        Supply supply = graph.supply(ooms.get(0).getSupplyId());
        assertNotNull(supply);
        assertEquals(FG_CODE, supply.getProductCode());
        assertEquals(60.0, supply.getQuantity(), 1e-6);

        Operation firstOp = ops.get(0);
        List<OperationInputMaterial> oims = graph.operationInputMaterialsForOperation(firstOp.getId());
        assertFalse(oims.isEmpty());
        Demand bomDemand = graph.demand(oims.get(0).getDemandId());
        assertNotNull(bomDemand);
        assertEquals(DemandSourceType.BOM_COMPONENT, bomDemand.getSourceType());
        assertEquals(COMP_CODE, bomDemand.getProductCode());
        assertEquals(60.0, bomDemand.getQuantity(), 1e-6);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (MaterialEntity.findByCode(FG_CODE) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = FG_CODE;
            fg.materialName = FG_CODE;
            fg.stampWorkspace();
            fg.persist();
        }
        if (MaterialEntity.findByCode(COMP_CODE) == null) {
            MaterialEntity comp = new MaterialEntity();
            comp.materialCode = COMP_CODE;
            comp.materialName = COMP_CODE;
            comp.stampWorkspace();
            comp.persist();
        }

        if (SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1) == null) {
            SalesOrderLineEntity salesLine = new SalesOrderLineEntity();
            salesLine.salesOrderNo = SALES_ORDER_NO;
            salesLine.salesOrderLineNo = 1;
            salesLine.productCode = FG_CODE;
            salesLine.orderQty = new BigDecimal("40");
            salesLine.dueDate = planningStart.plusDays(7);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }

        if (ForecastDemandEntity.findByForecastId(FORECAST_ID) == null) {
            ForecastDemandEntity fc = new ForecastDemandEntity();
            fc.forecastId = FORECAST_ID;
            fc.productCode = FG_CODE;
            fc.quantity = new BigDecimal("25");
            fc.forecastPeriod = "2026-W24";
            fc.needDate = planningStart.plusDays(14);
            fc.confidence = new BigDecimal("0.85");
            fc.stampWorkspace();
            fc.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = FG_CODE;
            workOrder.quantity = new BigDecimal("60");
            workOrder.needDate = planningStart.plusDays(5);
            workOrder.resourceId = "RES-SC-CHAIN-A";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        ensureRouting("RES-SC-CHAIN-A", "SC-OP-A", 1, 5, "60");
        ensureRouting("RES-SC-CHAIN-B", "SC-OP-B", 2, 0, "30");

        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-SC-CHAIN";
            bom.bomVersion = "1";
            bom.finishedProductCode = FG_CODE;
            bom.parentProductCode = FG_CODE;
            bom.componentProductCode = COMP_CODE;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }

    private static void ensureRouting(
            String resourceId, String operationName, int sequenceNo, int setupTimeMinutes, String processTimeSeconds) {
        if (ProductResourceEntity.findByProductAndResource(FG_CODE, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = FG_CODE;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.setupTimeMinutes = setupTimeMinutes;
            routing.processTimeSeconds = new BigDecimal(processTimeSeconds);
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
