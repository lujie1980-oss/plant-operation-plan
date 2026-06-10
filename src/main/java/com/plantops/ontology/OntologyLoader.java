package com.plantops.ontology;

import com.plantops.ontology.master.Product;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.rol.PispRolling;
import com.plantops.scenario.WorkOrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OntologyLoader {

    public OntologyGraph loadForWorkspace(LocalDate planningStart) {
        LocalDate effectiveStart = planningStart != null ? planningStart : LocalDate.now();
        return buildGraph(effectiveStart);
    }

    public OntologyGraph loadForPlanVersion(String planVersionId) {
        PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(planVersionId);
        if (planVersion == null) {
            throw new NotFoundException("Plan version not found: " + planVersionId);
        }
        LocalDate planningStart = resolvePlanningStart(planVersion);
        return buildGraph(planningStart);
    }

    private static LocalDate resolvePlanningStart(PlanVersionEntity planVersion) {
        // PlanVersionEntity has no planningStart field in M1; default to today.
        return LocalDate.now();
    }

    private OntologyGraph buildGraph(LocalDate planningStart) {
        Set<String> productCodes = collectProductCodes();
        List<Period> periods = buildPeriods(planningStart);
        PeriodIndex periodIndex = PeriodIndex.of(periods);
        OntologyGraph.Builder builder = OntologyGraph.builder()
                .defaultStockingPoint(StockingPoint.defaultFg())
                .periodsOrdered(periods);

        for (String productCode : productCodes) {
            builder.product(new Product(productCode, productCode));
            builder.pisp(new ProductInStockingPoint(
                    OntologyIds.pispId(productCode),
                    productCode,
                    StockingPoint.DEFAULT_FG,
                    productCode));
        }

        List<SupplyOrder> supplyOrders = new ArrayList<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (!isOpenWorkOrder(wo)) {
                continue;
            }
            SupplyOrder supplyOrder = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);
            if (supplyOrder != null) {
                supplyOrders.add(supplyOrder);
                builder.supplyOrder(supplyOrder);
            }
        }

        Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId = new LinkedHashMap<>();
        for (String productCode : productCodes) {
            String pispId = OntologyIds.pispId(productCode);
            double openingOnHand = sumInventoryOnHand(productCode);
            List<ProductInStockingPointPeriod> chain = new ArrayList<>(periods.size());
            for (int i = 0; i < periods.size(); i++) {
                Period period = periods.get(i);
                ProductInStockingPointPeriod pispp = new ProductInStockingPointPeriod(
                        OntologyIds.pisppId(pispId, period.getSequenceNr()),
                        pispId,
                        period.getId());
                if (i == 0) {
                    pispp.setOnHand(openingOnHand);
                    pispp.recalculatePlanningFields();
                }
                chain.add(pispp);
                builder.pispPeriod(pispp);
            }
            pisppChainByPispId.put(pispId, chain);
        }

        aggregateSupplyIntoPispp(supplyOrders, periodIndex, pisppChainByPispId);
        aggregateSalesDemandIntoPispp(periodIndex, pisppChainByPispId);
        for (List<ProductInStockingPointPeriod> chain : pisppChainByPispId.values()) {
            PispRolling.rollChain(chain);
        }

        return builder.build();
    }

    private static Set<String> collectProductCodes() {
        Set<String> productCodes = new LinkedHashSet<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (wo.productCode != null && !wo.productCode.isBlank()) {
                productCodes.add(wo.productCode);
            }
        }
        for (MaterialEntity material : MaterialEntity.listInWorkspace()) {
            if (material.materialCode != null && !material.materialCode.isBlank()) {
                productCodes.add(material.materialCode);
            }
        }
        for (InventoryEntity inventory : InventoryEntity.listInWorkspace()) {
            if (inventory.productCode != null && !inventory.productCode.isBlank()) {
                productCodes.add(inventory.productCode);
            }
        }
        return productCodes;
    }

    private static List<Period> buildPeriods(LocalDate planningStart) {
        SystemParameterEntity specRow = SystemParameterEntity.findByParamId("ontology_period_sequence");
        String specText = specRow != null ? specRow.paramValue : null;
        return PeriodSequenceSpec.parseOrDefault(specText).expand(planningStart);
    }

    private static double sumInventoryOnHand(String productCode) {
        double total = 0.0;
        for (InventoryEntity row : InventoryEntity.findByProduct(productCode)) {
            if (row.onhandQty != null) {
                total += row.onhandQty.doubleValue();
            }
        }
        return total;
    }

    private static boolean isOpenWorkOrder(WorkOrderEntity wo) {
        return wo.dispatchStatus == null
                || !WorkOrderService.DISPATCH_DISPATCHED.equals(wo.dispatchStatus);
    }

    private static void aggregateSupplyIntoPispp(
            List<SupplyOrder> supplyOrders,
            PeriodIndex periodIndex,
            Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId) {
        for (SupplyOrder supplyOrder : supplyOrders) {
            List<ProductInStockingPointPeriod> chain = pisppChainByPispId.get(supplyOrder.getPispId());
            if (chain == null) {
                continue;
            }
            ProductInStockingPointPeriod pispp = chain.get(periodIndex.sequenceFor(supplyOrder.getNeedDate()));
            pispp.setPlannedSupplyTotal(pispp.getPlannedSupplyTotal() + supplyOrder.getQuantity());
        }
    }

    private static void aggregateSalesDemandIntoPispp(
            PeriodIndex periodIndex,
            Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId) {
        for (SalesOrderLineEntity line : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(line.status)) {
                continue;
            }
            if (line.productCode == null || line.productCode.isBlank()) {
                continue;
            }
            List<ProductInStockingPointPeriod> chain = pisppChainByPispId.get(OntologyIds.pispId(line.productCode));
            if (chain == null) {
                continue;
            }
            ProductInStockingPointPeriod pispp = chain.get(periodIndex.sequenceFor(line.dueDate));
            double orderQty = line.orderQty != null ? line.orderQty.doubleValue() : 0.0;
            pispp.setPlannedDemandQuantityTotal(pispp.getPlannedDemandQuantityTotal() + orderQty);
        }
    }
}
