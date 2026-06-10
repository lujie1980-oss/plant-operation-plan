package com.plantops.ontology;

import com.plantops.ontology.master.Product;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.rol.OperationTimeWindowDerivations;
import com.plantops.rol.PispRolling;
import com.plantops.scenario.WorkOrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

        loadOperations(builder, supplyOrders);

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

        loadStandardResourcePeriods(builder, periods, periodIndex);

        OntologyGraph graph = builder.build();
        for (SupplyOrder supplyOrder : supplyOrders) {
            OperationTimeWindowDerivations.recalculate(graph, supplyOrder.getId(), planningStart);
        }
        return graph;
    }

    private static void loadOperations(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        Map<String, List<ProductResourceEntity>> routingByProduct = new LinkedHashMap<>();
        for (ProductResourceEntity pr : ProductResourceEntity.listInWorkspace()) {
            if (pr.productCode == null || pr.operationName == null || pr.operationName.isBlank()) {
                continue;
            }
            routingByProduct.computeIfAbsent(pr.productCode, k -> new ArrayList<>()).add(pr);
        }
        for (SupplyOrder supplyOrder : supplyOrders) {
            List<ProductResourceEntity> steps = distinctOrderedSteps(routingByProduct.get(supplyOrder.getProductCode()));
            for (int i = 0; i < steps.size(); i++) {
                ProductResourceEntity step = steps.get(i);
                double processSeconds = step.processTimeSeconds != null ? step.processTimeSeconds.doubleValue() : 0.0;
                double prodMinutes = step.setupTimeMinutes + processSeconds * supplyOrder.getQuantity() / 60.0;
                builder.operation(new Operation(
                        OntologyIds.operationId(supplyOrder.getId(), i),
                        supplyOrder.getId(), i, step.operationName, prodMinutes));
            }
        }
    }

    /** 同名工序去重（取 sequenceNo 最小行），再按 sequenceNo 升序；缺 sequenceNo 排末位。 */
    private static List<ProductResourceEntity> distinctOrderedSteps(List<ProductResourceEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, ProductResourceEntity> byName = new LinkedHashMap<>();
        for (ProductResourceEntity row : rows) {
            ProductResourceEntity existing = byName.get(row.operationName);
            if (existing == null || sequenceOf(row) < sequenceOf(existing)) {
                byName.put(row.operationName, row);
            }
        }
        return byName.values().stream()
                .sorted(Comparator.comparingInt(OntologyLoader::sequenceOf))
                .toList();
    }

    private static int sequenceOf(ProductResourceEntity row) {
        return row.sequenceNo != null ? row.sequenceNo : Integer.MAX_VALUE;
    }

    private static void loadStandardResourcePeriods(
            OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
        Set<String> resourceIds = new LinkedHashSet<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.resourceId != null && !line.resourceId.isBlank()) {
                resourceIds.add(line.resourceId);
            }
        }
        Map<String, StandardResourcePeriod> srpByKey = new LinkedHashMap<>();
        for (String resourceId : resourceIds) {
            for (Period period : periods) {
                StandardResourcePeriod srp = new StandardResourcePeriod(
                        OntologyIds.srpId(resourceId, period.getSequenceNr()), resourceId, period.getId());
                srpByKey.put(srp.getId(), srp);
                builder.standardResourcePeriod(srp);
            }
        }
        for (ResourceCalendarEntity cal : ResourceCalendarEntity.listInWorkspace()) {
            if (cal.resourceId == null || !resourceIds.contains(cal.resourceId) || cal.calendarDate == null) {
                continue;
            }
            if (cal.calendarDate.isBefore(periods.get(0).getStartDate())
                    || cal.calendarDate.isAfter(periods.get(periods.size() - 1).getEndDate())) {
                continue;
            }
            int seq = periodIndex.sequenceFor(cal.calendarDate);
            StandardResourcePeriod srp = srpByKey.get(OntologyIds.srpId(cal.resourceId, seq));
            srp.setTotalCapacity(srp.getTotalCapacity() + cal.availableCapacityMinutes + cal.unavailableCapacityMinutes);
            srp.setCalendarDowntime(srp.getCalendarDowntime() + cal.unavailableCapacityMinutes);
        }
        srpByKey.values().forEach(StandardResourcePeriod::recalculateCapacityFields);
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
