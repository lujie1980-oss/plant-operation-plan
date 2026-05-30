package com.plantops.scenario;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.scenario.planning.InventorySnapshot;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.MaterialFeasibilityEvaluator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * ??????????MRP ?? + ?? BOM?????? {@link KittingService} ?????? */
@ApplicationScoped
public class MaterialFeasibilityService {

    @Inject
    RuleScopeHelper ruleScopeHelper;

    private static final int DEMAND_OFFSET_DAYS_PER_LEVEL = 3;

    public MaterialFeasibilityContext prepareContext() {
        return prepareContext(InventorySnapshot.loadFromWorkspace());
    }

    /** 基于统一库存快照构建 MRP 按日闭合上下文（S04）。 */
    public MaterialFeasibilityContext prepareContext(InventorySnapshot inventorySnapshot) {
        Map<String, BigDecimal> opening = inventorySnapshot != null
                ? inventorySnapshot.availableByProduct()
                : Map.of();
        Map<String, Map<LocalDate, BigDecimal>> demand = new HashMap<>();
        Map<String, Map<LocalDate, BigDecimal>> supply = new HashMap<>();
        accumulateOrderDemand(demand);
        accumulateWorkOrderSupply(supply);

        LocalDate horizonStart = LocalDate.now();
        LocalDate horizonEnd = horizonStart;
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if (!"CANCELLED".equals(order.status)) {
                horizonEnd = maxDate(horizonEnd, order.dueDate);
            }
        }
        horizonEnd = horizonEnd.plusDays(3);
        if (horizonEnd.isBefore(horizonStart)) {
            horizonEnd = horizonStart.plusDays(14);
        }
        horizonStart = minDate(horizonStart, horizonEnd.minusDays(21));

        List<LocalDate> dates = dateRange(horizonStart, horizonEnd);
        Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial = new HashMap<>();

        for (String productCode : collectAllMaterials(opening, demand, supply)) {
            Map<LocalDate, BigDecimal> demandByDay = demand.getOrDefault(productCode, Map.of());
            Map<LocalDate, BigDecimal> supplyByDay = supply.getOrDefault(productCode, Map.of());
            BigDecimal carry = opening.getOrDefault(productCode, BigDecimal.ZERO);
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            for (LocalDate d : dates) {
                BigDecimal closing = carry
                        .add(supplyByDay.getOrDefault(d, BigDecimal.ZERO))
                        .subtract(demandByDay.getOrDefault(d, BigDecimal.ZERO));
                if (closing.compareTo(BigDecimal.ZERO) < 0) {
                    closing = BigDecimal.ZERO;
                }
                series.put(d, closing);
                carry = closing;
            }
            closingByMaterial.put(productCode, series);
        }

        BomSnapshot bomSnapshot = loadBomSnapshot();
        return new MaterialFeasibilityContext(
                closingByMaterial,
                bomSnapshot.byParent(),
                bomSnapshot.byFinishedAndParent(),
                loadManufacturedProducts());
    }

    private record BomSnapshot(
            Map<String, List<MaterialFeasibilityContext.ComponentNeed>> byParent,
            Map<String, List<MaterialFeasibilityContext.ComponentNeed>> byFinishedAndParent) {
    }

    private BomSnapshot loadBomSnapshot() {
        Set<String> manufactured = loadManufacturedProducts();
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bom = new HashMap<>();
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bomByFinished = new HashMap<>();
        for (BomComponentEntity row : BomComponentEntity.listInWorkspace()) {
            boolean critical = ruleScopeHelper.criticalForMasterPlan(row);
            MaterialFeasibilityContext.ComponentNeed need = new MaterialFeasibilityContext.ComponentNeed(
                    row.componentProductCode,
                    row.componentQty != null ? row.componentQty : BigDecimal.ZERO,
                    critical,
                    manufactured.contains(row.componentProductCode));
            bom.computeIfAbsent(row.parentProductCode, k -> new ArrayList<>()).add(need);
            if (row.finishedProductCode != null && !row.finishedProductCode.isBlank()) {
                String key = MaterialFeasibilityContext.finishedAndParentKey(
                        row.finishedProductCode, row.parentProductCode);
                bomByFinished.computeIfAbsent(key, k -> new ArrayList<>()).add(need);
            }
        }
        return new BomSnapshot(bom, bomByFinished);
    }

    private Set<String> loadManufacturedProducts() {
        return ProductResourceEntity.listInWorkspace().stream()
                .map(pr -> pr.productCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    public boolean isWorkOrderFeasibleOnDate(
            WorkOrderEntity wo,
            LocalDate productionDate,
            MaterialFeasibilityContext context) {
        if (wo == null || productionDate == null || context == null) {
            return true;
        }
        BigDecimal qty = wo.quantity != null ? wo.quantity : BigDecimal.ZERO;
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        return MaterialFeasibilityEvaluator.isFeasible(finished, wo.productCode, qty, productionDate, context);
    }

    private void accumulateOrderDemand(Map<String, Map<LocalDate, BigDecimal>> demand) {
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            explodeDemand(demand, order.productCode, order.productCode, order.orderQty, order.dueDate, 0);
        }
    }

    private void explodeDemand(
            Map<String, Map<LocalDate, BigDecimal>> demand,
            String finishedProductCode,
            String productCode,
            BigDecimal qty,
            LocalDate orderDueDate,
            int level) {
        LocalDate needDate = orderDueDate.minusDays((long) level * DEMAND_OFFSET_DAYS_PER_LEVEL);
        addQty(demand, productCode, needDate, qty);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finishedProductCode, productCode)) {
            if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                continue;
            }
            BigDecimal componentQty = bom.componentQty != null ? bom.componentQty : BigDecimal.ONE;
            if (bom.scrapRate != null && bom.scrapRate.compareTo(BigDecimal.ZERO) > 0) {
                componentQty = componentQty.multiply(BigDecimal.ONE.add(bom.scrapRate));
            }
            explodeDemand(
                    demand,
                    finishedProductCode,
                    bom.componentProductCode,
                    componentQty.multiply(qty),
                    orderDueDate,
                    level + 1);
        }
    }

    private void accumulateWorkOrderSupply(Map<String, Map<LocalDate, BigDecimal>> supply) {
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (wo.quantity == null || wo.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate finishDate = wo.needDate;
            if (finishDate == null && wo.salesOrderNo != null) {
                SalesOrderLineEntity order = SalesOrderLineEntity.find(
                        "salesOrderNo = ?1 and salesOrderLineNo = ?2",
                        wo.salesOrderNo,
                        wo.salesOrderLineNo)
                        .firstResult();
                if (order != null && !"CANCELLED".equals(order.status)) {
                    finishDate = order.dueDate.minusDays((long) workOrderDepth(wo) * 4);
                }
            }
            if (finishDate == null) {
                finishDate = LocalDate.now();
            }
            addQty(supply, wo.productCode, finishDate, wo.quantity);
        }
    }

    private int workOrderDepth(WorkOrderEntity wo) {
        int depth = 0;
        String parent = wo.parentWorkOrderNo;
        while (parent != null) {
            depth++;
            WorkOrderEntity p = WorkOrderEntity.findByNo(parent);
            if (p == null) {
                break;
            }
            parent = p.parentWorkOrderNo;
        }
        return depth;
    }

    private java.util.Set<String> collectAllMaterials(
            Map<String, BigDecimal> opening,
            Map<String, Map<LocalDate, BigDecimal>> demand,
            Map<String, Map<LocalDate, BigDecimal>> supply) {
        java.util.Set<String> materials = new java.util.HashSet<>(opening.keySet());
        materials.addAll(demand.keySet());
        materials.addAll(supply.keySet());
        return materials;
    }

    private void addQty(
            Map<String, Map<LocalDate, BigDecimal>> map,
            String product,
            LocalDate date,
            BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        map.computeIfAbsent(product, k -> new TreeMap<>()).merge(date, qty, BigDecimal::add);
    }

    private List<LocalDate> dateRange(LocalDate start, LocalDate end) {
        List<LocalDate> list = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            list.add(d);
            d = d.plusDays(1);
        }
        return list;
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
