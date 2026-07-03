package com.plantops.scenario;

import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.KittingResultDto;
import com.plantops.api.dto.MaterialBalanceDayDto;
import com.plantops.api.dto.MaterialBalanceRowDto;
import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialDemandTreeNodeDto;
import com.plantops.api.dto.MaterialDemandUsageDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@ApplicationScoped
public class MaterialRequirementService {

    private static final int DEMAND_OFFSET_DAYS_PER_LEVEL = 3;
    private static final int SUPPLY_OFFSET_DAYS_PER_WO_DEPTH = 4;

    @Inject
    KittingService kittingService;

    @Inject
    MasterPlanService masterPlanService;

    public MaterialRequirementReportDto buildReport() {
        return buildReport(null);
    }

    public MaterialRequirementReportDto buildReport(String masterPlanVersionId) {
        List<KittingResultDto> kittingResults = kittingService.compute();
        return buildBalance(kittingResults, masterPlanVersionId);
    }

    public MaterialRequirementReportDto getBalance() {
        return getBalance(null);
    }

    public MaterialRequirementReportDto getBalance(String masterPlanVersionId) {
        return buildBalance(loadPersistedKitting(), masterPlanVersionId);
    }

    /** ???????????? ???????????????????*/
    public MaterialDemandDetailDto buildDemandDetailTree(String materialCode) {
        return buildDemandDetailTree(materialCode, null);
    }

    public MaterialDemandDetailDto buildDemandDetailTree(String materialCode, String masterPlanVersionId) {
        List<MaterialDemandTreeNodeDto> roots = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        int pathCount = 0;

        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            List<List<String>> paths = findBomPaths(order.productCode, order.productCode, materialCode);
            if (paths.isEmpty()) {
                continue;
            }
            pathCount += paths.size();
            MaterialDemandTreeNodeDto orderRoot = null;
            for (List<String> path : paths) {
                totalQty = totalQty.add(quantityAlongPath(order, path, path.size() - 1));
                MaterialDemandTreeNodeDto chain = buildChainFromPath(order, path);
                orderRoot = orderRoot == null ? chain : mergeTrees(orderRoot, chain);
            }
            if (orderRoot != null) {
                roots.add(orderRoot);
            }
        }

        roots.sort(Comparator.comparing(MaterialDemandTreeNodeDto::label));
        return new MaterialDemandDetailDto(materialCode, roots, totalQty, pathCount);
    }

    /** ???????????BOM ??????????????????*/
    public List<MaterialDemandUsageDto> listDemandUsages(String materialCode) {
        List<MaterialDemandUsageDto> usages = new ArrayList<>();
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            collectUsagesForMaterial(
                    order,
                    order.productCode,
                    order.productCode,
                    order.orderQty,
                    order.dueDate,
                    0,
                    materialCode,
                    usages);
        }
        usages.sort(Comparator
                .comparing(MaterialDemandUsageDto::needDate)
                .thenComparing(MaterialDemandUsageDto::salesOrderNo)
                .thenComparingInt(MaterialDemandUsageDto::salesOrderLineNo)
                .thenComparing(MaterialDemandUsageDto::parentProductCode));
        return usages;
    }

    private void collectUsagesForMaterial(
            SalesOrderLineEntity order,
            String finishedProductCode,
            String productCode,
            BigDecimal qty,
            LocalDate orderDueDate,
            int level,
            String materialCode,
            List<MaterialDemandUsageDto> out) {
        LocalDate needDate = orderDueDate.minusDays((long) level * DEMAND_OFFSET_DAYS_PER_LEVEL);

        if (productCode.equals(materialCode)) {
            out.add(new MaterialDemandUsageDto(
                    "SALES_ORDER",
                    "销售订单 " + order.salesOrderNo + "-" + order.salesOrderLineNo,
                    order.salesOrderNo,
                    order.salesOrderLineNo,
                    order.productCode,
                    needDate,
                    qty,
                    level));
        }

        for (BomComponentEntity bom : BomComponentEntity.findChildren(finishedProductCode, productCode)) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(qty);
            String component = bom.componentProductCode;
            int childLevel = level + 1;
            if (component.equals(materialCode)) {
                LocalDate compNeedDate =
                        orderDueDate.minusDays((long) childLevel * DEMAND_OFFSET_DAYS_PER_LEVEL);
                String parent = productCode;
                WorkOrderEntity wo = WorkOrderEntity.find(
                                "salesOrderNo = ?1 and salesOrderLineNo = ?2 and productCode = ?3",
                                order.salesOrderNo,
                                order.salesOrderLineNo,
                                parent)
                        .firstResult();
                if (wo != null) {
                    out.add(new MaterialDemandUsageDto(
                            "WORK_ORDER",
                            "工单 · " + wo.workOrderNo + " 消耗 " + parent,
                            order.salesOrderNo,
                            order.salesOrderLineNo,
                            parent,
                            compNeedDate,
                            need,
                            childLevel));
                } else {
                    out.add(new MaterialDemandUsageDto(
                            "SALES_ORDER",
                            "订单 "
                                    + order.salesOrderNo
                                    + "-"
                                    + order.salesOrderLineNo
                                    + " 消耗 "
                                    + parent,
                            order.salesOrderNo,
                            order.salesOrderLineNo,
                            parent,
                            compNeedDate,
                            need,
                            childLevel));
                }
            }
            collectUsagesForMaterial(
                    order, finishedProductCode, component, need, orderDueDate, childLevel, materialCode, out);
        }
    }

    private List<List<String>> findBomPaths(String finishedProductCode, String parentProduct, String targetMaterial) {
        List<List<String>> paths = new ArrayList<>();
        findBomPathsDfs(
                finishedProductCode,
                parentProduct,
                targetMaterial,
                new ArrayList<>(List.of(parentProduct)),
                paths);
        return paths;
    }

    private void findBomPathsDfs(
            String finishedProductCode,
            String current,
            String target,
            List<String> path,
            List<List<String>> paths) {
        if (current.equals(target)) {
            paths.add(new ArrayList<>(path));
            return;
        }
        List<BomComponentEntity> components = BomComponentEntity.findChildren(finishedProductCode, current);
        if (components.isEmpty()) {
            return;
        }
        for (BomComponentEntity bom : components) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            path.add(bom.componentProductCode);
            findBomPathsDfs(finishedProductCode, bom.componentProductCode, target, path, paths);
            path.remove(path.size() - 1);
        }
    }

    private MaterialDemandTreeNodeDto buildChainFromPath(SalesOrderLineEntity order, List<String> path) {
        return buildChainNode(order, path, 0);
    }

    private MaterialDemandTreeNodeDto buildChainNode(
            SalesOrderLineEntity order, List<String> path, int depth) {
        String product = path.get(depth);
        BigDecimal qty = quantityAlongPath(order, path, depth);
        LocalDate needDate = order.dueDate.minusDays((long) depth * DEMAND_OFFSET_DAYS_PER_LEVEL);
        String orderKey = order.salesOrderNo + "-" + order.salesOrderLineNo;

        if (depth == path.size() - 1) {
            String nodeId = orderKey + "-mat-" + product;
            return new MaterialDemandTreeNodeDto(
                    nodeId,
                    "MATERIAL",
                    "目标物料 " + product,
                    product,
                    needDate,
                    qty,
                    List.of());
        }

        List<MaterialDemandTreeNodeDto> children = List.of(buildChainNode(order, path, depth + 1));

        if (depth == 0) {
            String nodeId = orderKey + "-so";
            return new MaterialDemandTreeNodeDto(
                    nodeId,
                    "SALES_ORDER",
                    "销售订单 " + orderKey + " · " + product,
                    product,
                    needDate,
                    qty,
                    children);
        }

        WorkOrderEntity wo = WorkOrderEntity.find(
                        "salesOrderNo = ?1 and salesOrderLineNo = ?2 and productCode = ?3",
                        order.salesOrderNo,
                        order.salesOrderLineNo,
                        product)
                .firstResult();
        String nodeId = orderKey + "-wo-" + (wo != null ? wo.workOrderNo : product);
        String label = wo != null
                ? "工单 · " + wo.workOrderNo + " 消耗 " + product
                : "计划消耗 · " + product;
        return new MaterialDemandTreeNodeDto(
                nodeId,
                wo != null ? "WORK_ORDER" : "PLANNED",
                label,
                product,
                needDate,
                qty,
                children);
    }

    private BigDecimal quantityAlongPath(SalesOrderLineEntity order, List<String> path, int depth) {
        BigDecimal qty = order.orderQty;
        for (int i = 1; i <= depth; i++) {
            String parent = path.get(i - 1);
            String component = path.get(i);
            BomComponentEntity bom = findBomLink(order.productCode, parent, component);
            if (bom == null) {
                break;
            }
            qty = qty.multiply(bom.componentQty);
        }
        return qty;
    }

    private BomComponentEntity findBomLink(String finishedProductCode, String parent, String component) {
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finishedProductCode, parent)) {
            if (bom.isCriticalComponent && bom.componentProductCode.equals(component)) {
                return bom;
            }
        }
        return null;
    }

    private MaterialDemandTreeNodeDto mergeTrees(
            MaterialDemandTreeNodeDto a, MaterialDemandTreeNodeDto b) {
        if (!a.nodeId().equals(b.nodeId())) {
            return a;
        }
        Map<String, MaterialDemandTreeNodeDto> childMap = new LinkedHashMap<>();
        for (MaterialDemandTreeNodeDto child : a.children()) {
            childMap.put(child.nodeId(), child);
        }
        for (MaterialDemandTreeNodeDto child : b.children()) {
            childMap.merge(child.nodeId(), child, this::mergeTrees);
        }
        return new MaterialDemandTreeNodeDto(
                a.nodeId(),
                a.nodeType(),
                a.label(),
                a.productCode(),
                a.needDate(),
                a.quantity(),
                new ArrayList<>(childMap.values()));
    }

    private List<KittingResultDto> loadPersistedKitting() {
        List<KittingResultDto> list = new ArrayList<>();
        for (KittingResultEntity e : KittingResultEntity.findLatest()) {
            list.add(new KittingResultDto(
                    e.salesOrderNo, e.salesOrderLineNo, e.kittingStatus, e.shortageReason));
        }
        return list;
    }

    private MaterialRequirementReportDto buildBalance(List<KittingResultDto> kittingResults) {
        return buildBalance(kittingResults, null);
    }

    private MaterialRequirementReportDto buildBalance(
            List<KittingResultDto> kittingResults, String masterPlanVersionId) {
        Map<String, BigDecimal> openingStock = loadOpeningStock();
        Set<String> materials = new HashSet<>(openingStock.keySet());
        materials.addAll(collectDemandMaterials());
        materials.addAll(collectSupplyMaterials());

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
        Map<String, Map<LocalDate, BigDecimal>> demand = new HashMap<>();
        Map<String, Map<LocalDate, BigDecimal>> supply = new HashMap<>();

        accumulateOrderDemand(demand);
        accumulateWorkOrderSupply(supply, masterPlanVersionId);

        List<MaterialBalanceRowDto> rows = new ArrayList<>();
        BigDecimal totalShortage = BigDecimal.ZERO;
        int materialsWithGap = 0;

        List<String> sortedMaterials = materials.stream().sorted().toList();
        for (String productCode : sortedMaterials) {
            Map<LocalDate, BigDecimal> demandByDay = demand.getOrDefault(productCode, Map.of());
            Map<LocalDate, BigDecimal> supplyByDay = supply.getOrDefault(productCode, Map.of());
            BigDecimal carry = openingStock.getOrDefault(productCode, BigDecimal.ZERO);
            boolean critical = isCriticalMaterial(productCode);

            List<MaterialBalanceDayDto> days = new ArrayList<>();
            BigDecimal rowShortage = BigDecimal.ZERO;

            for (LocalDate d : dates) {
                BigDecimal opening = carry;
                BigDecimal demandQty = demandByDay.getOrDefault(d, BigDecimal.ZERO);
                BigDecimal supplyQty = supplyByDay.getOrDefault(d, BigDecimal.ZERO);
                BigDecimal closing = opening.add(supplyQty).subtract(demandQty);
                BigDecimal shortage = BigDecimal.ZERO;
                if (closing.compareTo(BigDecimal.ZERO) < 0) {
                    shortage = closing.negate();
                    closing = BigDecimal.ZERO;
                }
                days.add(new MaterialBalanceDayDto(d, opening, demandQty, supplyQty, closing, shortage));
                carry = closing;
                rowShortage = rowShortage.add(shortage);
            }

            if (rowShortage.compareTo(BigDecimal.ZERO) > 0) {
                materialsWithGap++;
            }
            totalShortage = totalShortage.add(rowShortage);
            rows.add(new MaterialBalanceRowDto(
                    productCode,
                    null,
                    critical,
                    rowShortage,
                    days,
                    List.of()));
        }

        rows.sort(Comparator
                .comparing(MaterialBalanceRowDto::totalShortageQty)
                .reversed()
                .thenComparing(MaterialBalanceRowDto::productCode));

        List<DemandPoolKpiDto> kpis = buildKpis(
                sortedMaterials.size(),
                materialsWithGap,
                totalShortage,
                kittingResults);

        return new MaterialRequirementReportDto(
                kpis,
                horizonStart,
                horizonEnd,
                dates,
                List.of(),
                rows,
                kittingResults);
    }

    private List<DemandPoolKpiDto> buildKpis(
            int materialCount,
            int materialsWithGap,
            BigDecimal totalShortage,
            List<KittingResultDto> kittingResults) {
        long shortageOrders = kittingResults.stream().filter(k -> "SHORTAGE".equals(k.kittingStatus())).count();
        long totalOrders = kittingResults.isEmpty()
                ? SalesOrderLineEntity.count("workspaceId = ?1 and status != ?2", SalesOrderLineEntity.ws(), "CANCELLED")
                : kittingResults.size();
        double coverage = materialCount == 0
                ? 100
                : (double) (materialCount - materialsWithGap) / materialCount * 100;

        List<DemandPoolKpiDto> kpis = new ArrayList<>();
        kpis.add(new DemandPoolKpiDto("mrp_material_count", "物料种类", materialCount, "种", "info"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_gap_materials",
                "缺料物料",
                materialsWithGap,
                "种",
                materialsWithGap > 0 ? "danger" : "ok"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_total_shortage",
                "缺料总量",
                totalShortage.setScale(0, RoundingMode.HALF_UP).doubleValue(),
                "件",
                totalShortage.compareTo(BigDecimal.ZERO) > 0 ? "warn" : "ok"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_coverage",
                "覆盖率",
                Math.round(coverage * 10) / 10.0,
                "%",
                coverage >= 90 ? "ok" : coverage >= 70 ? "warn" : "danger"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_order_shortage",
                "缺料订单",
                shortageOrders,
                "单",
                shortageOrders > 0 ? "danger" : "ok"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_orders_total",
                "订单总数",
                totalOrders,
                "单",
                "info"));
        return kpis;
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
            if (!bom.isCriticalComponent) {
                continue;
            }
            BigDecimal componentQty = bom.componentQty != null ? bom.componentQty : BigDecimal.ONE;
            if (bom.scrapRate != null && bom.scrapRate.compareTo(BigDecimal.ZERO) > 0) {
                componentQty = componentQty.multiply(BigDecimal.ONE.add(bom.scrapRate));
            }
            BigDecimal need = componentQty.multiply(qty);
            explodeDemand(demand, finishedProductCode, bom.componentProductCode, need, orderDueDate, level + 1);
        }
    }

    private void accumulateWorkOrderSupply(
            Map<String, Map<LocalDate, BigDecimal>> supply, String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            List<MasterPlanAllocationEntity> allocations = MasterPlanAllocationEntity
                    .find("planVersionId = ?1 order by workOrderNo, slotDate, slotIndex", masterPlanVersionId)
                    .list();
            Map<String, MasterPlanAllocationEntity> lastByWo = new LinkedHashMap<>();
            for (MasterPlanAllocationEntity alloc : allocations) {
                if (alloc.workOrderNo != null) {
                    lastByWo.put(alloc.workOrderNo, alloc);
                }
            }
            for (Map.Entry<String, MasterPlanAllocationEntity> entry : lastByWo.entrySet()) {
                WorkOrderEntity wo = WorkOrderEntity.findByNo(entry.getKey());
                if (wo == null) {
                    continue;
                }
                MasterPlanService.WorkOrderPlannedWindow window =
                        masterPlanService.resolveWorkOrderWindow(masterPlanVersionId, wo.workOrderNo);
                LocalDate finishDate = window != null
                        ? window.plannedEnd().toLocalDate()
                        : entry.getValue().slotDate;
                addQty(supply, wo.productCode, finishDate, wo.quantity);
            }
            return;
        }
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            SalesOrderLineEntity order = SalesOrderLineEntity.find(
                    "salesOrderNo = ?1 and salesOrderLineNo = ?2",
                    wo.salesOrderNo,
                    wo.salesOrderLineNo)
                    .firstResult();
            if (order == null || "CANCELLED".equals(order.status)) {
                continue;
            }
            LocalDate finishDate = order.dueDate.minusDays((long) workOrderDepth(wo) * SUPPLY_OFFSET_DAYS_PER_WO_DEPTH);
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

    private Set<String> collectDemandMaterials() {
        Set<String> set = new HashSet<>();
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            collectBomMaterials(set, order.productCode, order.productCode);
        }
        return set;
    }

    private void collectBomMaterials(Set<String> set, String finishedProductCode, String parent) {
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finishedProductCode, parent)) {
            if (bom.isCriticalComponent) {
                set.add(bom.componentProductCode);
                collectBomMaterials(set, finishedProductCode, bom.componentProductCode);
            }
        }
        set.add(parent);
    }

    private Set<String> collectSupplyMaterials() {
        Set<String> set = new HashSet<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            set.add(wo.productCode);
        }
        return set;
    }

    private boolean isCriticalMaterial(String productCode) {
        return BomComponentEntity.<BomComponentEntity>find(
                "componentProductCode = ?1 and isCriticalComponent = true",
                productCode)
                .count() > 0
                || SalesOrderLineEntity.count("workspaceId = ?1 and productCode = ?2", SalesOrderLineEntity.ws(), productCode) > 0;
    }

    private Map<String, BigDecimal> loadOpeningStock() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
            map.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
        }
        return map;
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
