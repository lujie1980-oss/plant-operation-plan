package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataValidationDtos;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.BlockedSalesOrderLine;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.Severity;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.ValidationIssue;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.ValidationReport;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.ContinuousProductionRuleEntity;
import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.ChangeoverAttributeKey;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class MasterDataValidationService {

    // -------- rule IDs (stable contract for UI / logs) --------
    public static final String SO_LINE_DUP = "SO_LINE_DUP";
    public static final String PR_DUP = "PR_DUP";
    public static final String SO_PRODUCT_EMPTY = "SO_PRODUCT_EMPTY";
    public static final String SO_QTY_NONPOSITIVE = "SO_QTY_NONPOSITIVE";
    public static final String SO_DUEDATE_EMPTY = "SO_DUEDATE_EMPTY";
    public static final String PR_RESOURCE_MISSING = "PR_RESOURCE_MISSING";
    public static final String PRODUCT_NO_ROUTING = "PRODUCT_NO_ROUTING";
    public static final String RES_RATE_NONPOSITIVE = "RES_RATE_NONPOSITIVE";
    public static final String PR_PROCESS_TIME_NONPOSITIVE = "PR_PROCESS_TIME_NONPOSITIVE";
    public static final String BOM_CRITICAL_CHILD_NO_ROUTING = "BOM_CRITICAL_CHILD_NO_ROUTING";
    public static final String BOM_SELF_REF = "BOM_SELF_REF";
    public static final String BOM_QTY_NONPOSITIVE = "BOM_QTY_NONPOSITIVE";
    public static final String BOM_CYCLE = "BOM_CYCLE";
    public static final String CALENDAR_MISSING = "CALENDAR_MISSING";
    public static final String CALENDAR_CAPACITY_NEGATIVE = "CALENDAR_CAPACITY_NEGATIVE";
    public static final String INVENTORY_NEGATIVE = "INVENTORY_NEGATIVE";
    public static final String INVENTORY_RESERVED_GT_ONHAND = "INVENTORY_RESERVED_GT_ONHAND";
    public static final String LINE_RESOURCE_MISSING = "LINE_RESOURCE_MISSING";
    public static final String LINE_RESOURCE_MULTI_LINE = "LINE_RESOURCE_MULTI_LINE";
    public static final String CHANGEOVER_NEGATIVE = "CHANGEOVER_NEGATIVE";
    public static final String CHANGEOVER_SELF = "CHANGEOVER_SELF";
    public static final String PARALLEL_OP_LINE_MISSING = "PARALLEL_OP_LINE_MISSING";
    public static final String PARALLEL_OP_PRODUCT_NO_ROUTING = "PARALLEL_OP_PRODUCT_NO_ROUTING";
    public static final String PARALLEL_OP_SAME_PRODUCT = "PARALLEL_OP_SAME_PRODUCT";
    public static final String OP_TRANSFER_NEGATIVE = "OP_TRANSFER_NEGATIVE";
    public static final String OP_TRANSFER_MIN_GT_TRANSFER = "OP_TRANSFER_MIN_GT_TRANSFER";
    public static final String OP_TRANSFER_SAME_OPERATION = "OP_TRANSFER_SAME_OPERATION";
    public static final String OP_TRANSFER_PRODUCT_NO_ROUTING = "OP_TRANSFER_PRODUCT_NO_ROUTING";
    public static final String OP_TRANSFER_OP_NOT_ON_ROUTING = "OP_TRANSFER_OP_NOT_ON_ROUTING";
    public static final String CP_LINE_MISSING = "CP_LINE_MISSING";
    public static final String CP_NO_PRODUCT = "CP_NO_PRODUCT";
    public static final String CP_PRODUCT_NO_ROUTING = "CP_PRODUCT_NO_ROUTING";

    public ValidationReport validateAll() {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        List<BlockedSalesOrderLine> blocked = new ArrayList<>();

        List<SalesOrderLineEntity> orders = SalesOrderLineEntity.listInWorkspace();
        List<ProductResourceEntity> prs = ProductResourceEntity.listInWorkspace();
        List<ProductionResourceEntity> resources = ProductionResourceEntity.listInWorkspace();
        List<BomComponentEntity> boms = BomComponentEntity.listInWorkspace();
        List<ResourceCalendarEntity> calendars = ResourceCalendarEntity.listInWorkspace();
        List<InventoryEntity> inventory = InventoryEntity.listInWorkspace();
        List<ProductionLineEntity> lines = ProductionLineEntity.listInWorkspace();
        List<ChangeoverMatrixEntity> changeovers = ChangeoverMatrixEntity.listInWorkspace();
        List<ParallelOperationRuleEntity> parallelOperations = ParallelOperationRuleEntity.listInWorkspace();
        List<OperationTransferTimeRuleEntity> operationTransferTimes = OperationTransferTimeRuleEntity.listInWorkspace();
        List<ContinuousProductionRuleEntity> continuousProductionRules = ContinuousProductionRuleEntity.listInWorkspace();

        Set<String> resourceIds = new HashSet<>();
        for (ProductionResourceEntity r : resources) {
            if (r.resourceId != null && !r.resourceId.isBlank()) {
                resourceIds.add(r.resourceId);
            }
        }

        Set<String> productsWithRouting = new HashSet<>();
        Set<String> prPairs = new HashSet<>();
        for (ProductResourceEntity pr : prs) {
            if (pr.productCode != null && !pr.productCode.isBlank()) {
                productsWithRouting.add(pr.productCode);
            }
            String key = pairKey(pr.productCode, pr.resourceId);
            if (!prPairs.add(key)) {
                addIssue(
                        errors,
                        PR_DUP,
                        Severity.ERROR,
                        "ProductResource",
                        key,
                        "工艺路线 (productCode, resourceId) 重复",
                        Map.of("productCode", pr.productCode, "resourceId", pr.resourceId));
            }
            if (pr.resourceId == null || pr.resourceId.isBlank() || !resourceIds.contains(pr.resourceId)) {
                addIssue(
                        errors,
                        PR_RESOURCE_MISSING,
                        Severity.ERROR,
                        "ProductResource",
                        key,
                        "工艺路线引用的 resourceId 在生产资源中不存在",
                        Map.of("productCode", pr.productCode, "resourceId", pr.resourceId));
            }
            if (pr.processTimeSeconds != null && pr.processTimeSeconds.compareTo(BigDecimal.ZERO) <= 0) {
                addIssue(
                        warnings,
                        PR_PROCESS_TIME_NONPOSITIVE,
                        Severity.WARNING,
                        "ProductResource",
                        key,
                        "工艺 processTimeSeconds <= 0，主计划工时计算可能异常",
                        Map.of("productCode", pr.productCode, "resourceId", pr.resourceId, "processTimeSeconds", pr.processTimeSeconds));
            }
        }

        Set<String> calendarKeys = new HashSet<>();
        Set<String> resourcesWithAnyCalendar = new HashSet<>();
        for (ResourceCalendarEntity c : calendars) {
            String k = String.valueOf(c.resourceId) + "|" + String.valueOf(c.shiftId) + "|" + String.valueOf(c.calendarDate);
            calendarKeys.add(k);
            if (c.resourceId != null && !c.resourceId.isBlank()) {
                resourcesWithAnyCalendar.add(c.resourceId);
            }
            if (c.availableCapacityMinutes < 0 || c.unavailableCapacityMinutes < 0) {
                addIssue(
                        warnings,
                        CALENDAR_CAPACITY_NEGATIVE,
                        Severity.WARNING,
                        "ResourceCalendar",
                        k,
                        "资源日历可用/不可用产能为负",
                        Map.of(
                                "resourceId", c.resourceId,
                                "shiftId", c.shiftId,
                                "calendarDate", c.calendarDate,
                                "availableCapacityMinutes", c.availableCapacityMinutes,
                                "unavailableCapacityMinutes", c.unavailableCapacityMinutes));
            }
        }
        for (String rid : ProductionResourceEntity.routingResourceIds()) {
            if (rid == null || rid.isBlank()) {
                continue;
            }
            if (!resourceHasCalendarCoverage(rid, resourcesWithAnyCalendar)) {
                List<ProductionLineEntity> resourceLines = ProductionLineEntity.findByResourceId(rid);
                String reason = resourceLines.size() > 1
                        ? "生产资源下有多条产线，请按产线 ID 维护资源日历，主计划将汇总各产线产能"
                        : "资源未维护日历，主计划将使用产线默认班产能或全局默认班产能";
                addIssue(
                        warnings,
                        CALENDAR_MISSING,
                        Severity.WARNING,
                        "ResourceCalendar",
                        rid,
                        reason,
                        Map.of("resourceId", rid, "lineCount", resourceLines.size()));
            }
        }

        for (ProductionResourceEntity r : resources) {
            if (r.runRatePerHour != null && r.runRatePerHour.compareTo(BigDecimal.ZERO) <= 0) {
                addIssue(
                        warnings,
                        RES_RATE_NONPOSITIVE,
                        Severity.WARNING,
                        "ProductionResource",
                        String.valueOf(r.resourceId),
                        "生产资源 runRatePerHour <= 0，产能分析可能异常",
                        Map.of("resourceId", r.resourceId, "runRatePerHour", r.runRatePerHour));
            }
        }

        for (InventoryEntity inv : inventory) {
            String k = String.valueOf(inv.stockingPointCode) + "|" + String.valueOf(inv.productCode);
            if (inv.onhandQty != null && inv.onhandQty.compareTo(BigDecimal.ZERO) < 0) {
                addIssue(
                        warnings,
                        INVENTORY_NEGATIVE,
                        Severity.WARNING,
                        "Inventory",
                        k,
                        "库存 onhandQty 为负，MRP/齐套计算可能异常",
                        Map.of("stockingPointCode", inv.stockingPointCode, "productCode", inv.productCode, "onhandQty", inv.onhandQty));
            }
            if (inv.reservedQty != null && inv.onhandQty != null && inv.reservedQty.compareTo(inv.onhandQty) > 0) {
                addIssue(
                        warnings,
                        INVENTORY_RESERVED_GT_ONHAND,
                        Severity.WARNING,
                        "Inventory",
                        k,
                        "占用数量 reservedQty 超过 onhandQty，可用量将按 0 处理",
                        Map.of(
                                "stockingPointCode", inv.stockingPointCode,
                                "productCode", inv.productCode,
                                "onhandQty", inv.onhandQty,
                                "reservedQty", inv.reservedQty));
            }
        }

        Map<String, String> firstLineByResource = new LinkedHashMap<>();
        for (ProductionLineEntity line : lines) {
            if (line.resourceId == null || line.resourceId.isBlank()) {
                continue;
            }
            if (!resourceIds.contains(line.resourceId)) {
                addIssue(
                        errors,
                        LINE_RESOURCE_MISSING,
                        Severity.ERROR,
                        "ProductionLine",
                        String.valueOf(line.lineId),
                        "产线引用的生产资源不存在",
                        Map.of("lineId", line.lineId, "resourceId", line.resourceId));
            }
            String existing = firstLineByResource.putIfAbsent(line.resourceId, line.lineId);
            if (existing != null && !existing.equals(line.lineId)) {
                addIssue(
                        warnings,
                        LINE_RESOURCE_MULTI_LINE,
                        Severity.WARNING,
                        "ProductionLine",
                        line.resourceId,
                        "同一生产资源绑定多条产线（" + existing + "、" + line.lineId + "），主计划产能=各产线日历之和",
                        Map.of("resourceId", line.resourceId, "lineA", existing, "lineB", line.lineId));
            }
        }

        for (ChangeoverMatrixEntity c : changeovers) {
            String k = String.valueOf(c.operationName) + "|" + String.valueOf(c.attributeKey) + "|"
                    + String.valueOf(c.fromAttributeValue) + "->" + String.valueOf(c.toAttributeValue);
            if (c.setupMinutes < 0) {
                addIssue(
                        warnings,
                        CHANGEOVER_NEGATIVE,
                        Severity.WARNING,
                        "ChangeoverMatrix",
                        k,
                        "换型 setupMinutes 为负",
                        Map.of(
                                "operationName", c.operationName,
                                "attributeKey", c.attributeKey,
                                "fromAttributeValue", c.fromAttributeValue,
                                "toAttributeValue", c.toAttributeValue,
                                "setupMinutes", c.setupMinutes));
            }
            if (ChangeoverAttributeKey.parse(c.attributeKey).isEmpty()) {
                addIssue(
                        warnings,
                        CHANGEOVER_SELF,
                        Severity.WARNING,
                        "ChangeoverMatrix",
                        k,
                        "未知属性键，支持: 线材/关键物料/分支/料号",
                        Map.of("attributeKey", c.attributeKey));
            }
        }

        Set<String> lineIds = new HashSet<>();
        for (ProductionLineEntity line : lines) {
            if (line.lineId != null) {
                lineIds.add(line.lineId);
            }
        }

        Set<String> warnedParallelProducts = new HashSet<>();
        for (ParallelOperationRuleEntity pair : parallelOperations) {
            String k = pair.lineId + "|" + pair.firstProductCode + "+" + pair.secondProductCode;
            if (pair.firstProductCode != null && pair.firstProductCode.equals(pair.secondProductCode)) {
                addIssue(
                        warnings,
                        PARALLEL_OP_SAME_PRODUCT,
                        Severity.WARNING,
                        "ParallelOperationRule",
                        k,
                        "第一头与第二头料号相同，请确认是否为有效配对",
                        Map.of("productCode", pair.firstProductCode));
            }
            if (pair.lineId != null && !lineIds.contains(pair.lineId)) {
                addIssue(
                        warnings,
                        PARALLEL_OP_LINE_MISSING,
                        Severity.WARNING,
                        "ParallelOperationRule",
                        k,
                        "产线未在产线主数据中维护（机台=产线ID）",
                        Map.of("lineId", pair.lineId));
            }
            for (String productCode : List.of(pair.firstProductCode, pair.secondProductCode)) {
                if (productCode == null || productCode.isBlank() || !warnedParallelProducts.add(productCode)) {
                    continue;
                }
                if (!ProductResourceEntity.hasRouting(productCode)) {
                    addIssue(
                            warnings,
                            PARALLEL_OP_PRODUCT_NO_ROUTING,
                            Severity.WARNING,
                            "ParallelOperationRule",
                            productCode,
                            "配对料号无工艺路线，排程将无法识别工序",
                            Map.of("productCode", productCode));
                }
            }
        }

        Set<String> warnedTransferProducts = new HashSet<>();
        for (OperationTransferTimeRuleEntity rule : operationTransferTimes) {
            String k = rule.productCode + "|" + rule.fromOperationName + "->" + rule.toOperationName;
            int maxMinutes = rule.maxTransferMinutes > 0 ? rule.maxTransferMinutes : rule.transferMinutes;
            if (rule.minTransferMinutes < 0 || maxMinutes < 0) {
                addIssue(
                        errors,
                        OP_TRANSFER_NEGATIVE,
                        Severity.ERROR,
                        "OperationTransferTimeRule",
                        k,
                        "流转时间或最小流转时间为负",
                        Map.of("productCode", rule.productCode));
            }
            if (maxMinutes > 0 && maxMinutes <= rule.minTransferMinutes) {
                addIssue(
                        warnings,
                        OP_TRANSFER_MIN_GT_TRANSFER,
                        Severity.WARNING,
                        "OperationTransferTimeRule",
                        k,
                        "最大流转时间必须大于最小流转时间",
                        Map.of("productCode", rule.productCode));
            }
            if (rule.fromOperationName != null && rule.fromOperationName.equals(rule.toOperationName)) {
                addIssue(
                        warnings,
                        OP_TRANSFER_SAME_OPERATION,
                        Severity.WARNING,
                        "OperationTransferTimeRule",
                        k,
                        "前工序与后工序相同",
                        Map.of("productCode", rule.productCode));
            }
            if (rule.productCode != null && !rule.productCode.isBlank() && warnedTransferProducts.add(rule.productCode)) {
                if (!ProductResourceEntity.hasRouting(rule.productCode)) {
                    addIssue(
                            warnings,
                            OP_TRANSFER_PRODUCT_NO_ROUTING,
                            Severity.WARNING,
                            "OperationTransferTimeRule",
                            rule.productCode,
                            "产品无工艺路线，流转时间规则可能无法生效",
                            Map.of("productCode", rule.productCode));
                }
            }
            if (rule.productCode != null && ProductResourceEntity.hasRouting(rule.productCode)) {
                Set<String> operationNames = new HashSet<>();
                for (ProductResourceEntity pr : ProductResourceEntity.findByProductOrdered(rule.productCode)) {
                    if (pr.operationName != null && !pr.operationName.isBlank()) {
                        operationNames.add(pr.operationName.trim());
                    }
                }
                for (String opName : List.of(rule.fromOperationName, rule.toOperationName)) {
                    if (opName == null || opName.isBlank() || operationNames.contains(opName.trim())) {
                        continue;
                    }
                    addIssue(
                            warnings,
                            OP_TRANSFER_OP_NOT_ON_ROUTING,
                            Severity.WARNING,
                            "OperationTransferTimeRule",
                            k,
                            "工序不在产品工艺路线中: " + opName,
                            Map.of("productCode", rule.productCode, "operationName", opName));
                }
            }
        }

        Set<String> warnedCpProducts = new HashSet<>();
        for (ContinuousProductionRuleEntity rule : continuousProductionRules) {
            String k = rule.lineId + "|" + rule.firstProductCode + "+" + rule.secondProductCode + "+" + rule.finishedProductCode;
            if (rule.firstProductCode.isBlank() && rule.secondProductCode.isBlank() && rule.finishedProductCode.isBlank()) {
                addIssue(
                        errors,
                        CP_NO_PRODUCT,
                        Severity.ERROR,
                        "ContinuousProductionRule",
                        k,
                        "连续生产规则至少需要一个料号",
                        Map.of("lineId", rule.lineId));
            }
            if (rule.lineId != null && !lineIds.contains(rule.lineId)) {
                addIssue(
                        warnings,
                        CP_LINE_MISSING,
                        Severity.WARNING,
                        "ContinuousProductionRule",
                        k,
                        "机台未在产线主数据中维护",
                        Map.of("lineId", rule.lineId));
            }
            for (String productCode : List.of(rule.firstProductCode, rule.secondProductCode, rule.finishedProductCode)) {
                if (productCode == null || productCode.isBlank() || !warnedCpProducts.add(productCode)) {
                    continue;
                }
                if (!ProductResourceEntity.hasRouting(productCode)) {
                    addIssue(
                            warnings,
                            CP_PRODUCT_NO_ROUTING,
                            Severity.WARNING,
                            "ContinuousProductionRule",
                            productCode,
                            "连续生产料号无工艺路线，排程可能无法识别",
                            Map.of("productCode", productCode));
                }
            }
        }

        Map<String, Set<String>> bomGraph = new LinkedHashMap<>();
        Set<String> bomBadParents = new HashSet<>();
        Set<String> bomPairs = new HashSet<>();
        for (BomComponentEntity bom : boms) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            String key = pairKey(bom.parentProductCode, bom.componentProductCode);
            if (!bomPairs.add(key)) {
                continue;
            }
            if (bom.parentProductCode != null
                    && bom.componentProductCode != null
                    && bom.parentProductCode.equals(bom.componentProductCode)) {
                addIssue(
                        errors,
                        BOM_SELF_REF,
                        Severity.ERROR,
                        "BomComponent",
                        key,
                        "BOM 存在自引用",
                        Map.of("parentProductCode", bom.parentProductCode, "componentProductCode", bom.componentProductCode));
                if (bom.parentProductCode != null && !bom.parentProductCode.isBlank()) {
                    bomBadParents.add(bom.parentProductCode);
                }
            }
            if (bom.componentQty == null || bom.componentQty.compareTo(BigDecimal.ZERO) <= 0) {
                addIssue(
                        errors,
                        BOM_QTY_NONPOSITIVE,
                        Severity.ERROR,
                        "BomComponent",
                        key,
                        "BOM componentQty <= 0，MRP/齐套无效",
                        Map.of("parentProductCode", bom.parentProductCode, "componentProductCode", bom.componentProductCode, "componentQty", bom.componentQty));
                if (bom.parentProductCode != null && !bom.parentProductCode.isBlank()) {
                    bomBadParents.add(bom.parentProductCode);
                }
            }
            if (bom.componentProductCode != null
                    && !bom.componentProductCode.isBlank()
                    && !productsWithRouting.contains(bom.componentProductCode)) {
                addIssue(
                        warnings,
                        BOM_CRITICAL_CHILD_NO_ROUTING,
                        Severity.WARNING,
                        "BomComponent",
                        key,
                        "关键子件无工艺路线，齐套/MRP 可能误判",
                        Map.of("parentProductCode", bom.parentProductCode, "componentProductCode", bom.componentProductCode));
            }
            if (bom.parentProductCode != null && bom.componentProductCode != null) {
                bomGraph.computeIfAbsent(bom.parentProductCode, k -> new LinkedHashSet<>()).add(bom.componentProductCode);
            }
        }

        Set<String> cycleNodes = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String n : bomGraph.keySet()) {
            if (!visited.contains(n)) {
                dfsCycle(n, bomGraph, visiting, visited, new ArrayList<>(), cycleNodes);
            }
        }
        for (String node : cycleNodes) {
            addIssue(
                    errors,
                    BOM_CYCLE,
                    Severity.ERROR,
                    "BomComponent",
                    node,
                    "BOM 关键件存在循环引用，影响 MRP/工单展开",
                    Map.of("productCode", node));
        }

        Set<String> soKeys = new HashSet<>();
        for (SalesOrderLineEntity so : orders) {
            String key = soLineKey(so.salesOrderNo, so.salesOrderLineNo);
            if (!soKeys.add(key)) {
                addIssue(
                        errors,
                        SO_LINE_DUP,
                        Severity.ERROR,
                        "SalesOrderLine",
                        key,
                        "销售订单行 (salesOrderNo, salesOrderLineNo) 重复",
                        Map.of("salesOrderNo", so.salesOrderNo, "salesOrderLineNo", so.salesOrderLineNo));
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_LINE_DUP, "订单行重复"));
                continue;
            }

            if (so.productCode == null || so.productCode.isBlank()) {
                addIssue(
                        errors,
                        SO_PRODUCT_EMPTY,
                        Severity.ERROR,
                        "SalesOrderLine",
                        key,
                        "订单行 productCode 为空",
                        Map.of("salesOrderNo", so.salesOrderNo, "salesOrderLineNo", so.salesOrderLineNo));
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_PRODUCT_EMPTY, "产品为空"));
                continue;
            }
            if (so.orderQty == null || so.orderQty.compareTo(BigDecimal.ZERO) <= 0) {
                addIssue(
                        errors,
                        SO_QTY_NONPOSITIVE,
                        Severity.ERROR,
                        "SalesOrderLine",
                        key,
                        "订单行 orderQty <= 0",
                        Map.of("salesOrderNo", so.salesOrderNo, "salesOrderLineNo", so.salesOrderLineNo, "orderQty", so.orderQty));
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_QTY_NONPOSITIVE, "数量无效"));
                continue;
            }
            if (so.dueDate == null) {
                addIssue(
                        errors,
                        SO_DUEDATE_EMPTY,
                        Severity.ERROR,
                        "SalesOrderLine",
                        key,
                        "订单行 dueDate 为空",
                        Map.of("salesOrderNo", so.salesOrderNo, "salesOrderLineNo", so.salesOrderLineNo));
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, SO_DUEDATE_EMPTY, "交期为空"));
                continue;
            }

            if (!productsWithRouting.contains(so.productCode)) {
                addIssue(
                        errors,
                        PRODUCT_NO_ROUTING,
                        Severity.ERROR,
                        "SalesOrderLine",
                        key,
                        "产品无工艺路线，无法生成工单",
                        Map.of("productCode", so.productCode, "salesOrderNo", so.salesOrderNo, "salesOrderLineNo", so.salesOrderLineNo));
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, PRODUCT_NO_ROUTING, "无工艺路线"));
            }

            if (cycleNodes.contains(so.productCode)) {
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, BOM_CYCLE, "BOM 循环"));
            }

            if (bomBadParents.contains(so.productCode)) {
                blocked.add(new BlockedSalesOrderLine(so.salesOrderNo, so.salesOrderLineNo, BOM_QTY_NONPOSITIVE, "BOM 定义错误"));
            }
        }

        Map<String, BlockedSalesOrderLine> blockedByKey = new LinkedHashMap<>();
        for (BlockedSalesOrderLine b : blocked) {
            blockedByKey.putIfAbsent(soLineKey(b.salesOrderNo(), b.salesOrderLineNo()), b);
        }

        return new MasterDataValidationDtos.ValidationReport(
                List.copyOf(errors),
                List.copyOf(warnings),
                List.copyOf(blockedByKey.values()));
    }

    private static void addIssue(
            List<ValidationIssue> target,
            String ruleId,
            Severity severity,
            String entityType,
            String entityKey,
            String reason,
            Map<String, Object> fields) {
        target.add(new ValidationIssue(ruleId, severity, entityType, entityKey, reason, fields));
    }

    private static String soLineKey(String salesOrderNo, int salesOrderLineNo) {
        return String.valueOf(salesOrderNo) + ":" + salesOrderLineNo;
    }

    private static String pairKey(String a, String b) {
        return String.valueOf(a) + "->" + String.valueOf(b);
    }

    /**
     * 是否具备有效日历输入：资源级 calendar，或多产线下至少一条产线有日历行。
     */
    private static boolean resourceHasCalendarCoverage(String resourceId, Set<String> calendarOwners) {
        if (calendarOwners.contains(resourceId)) {
            return true;
        }
        List<ProductionLineEntity> lines = ProductionLineEntity.findByResourceId(resourceId);
        if (lines.isEmpty()) {
            return false;
        }
        for (ProductionLineEntity line : lines) {
            if (line.lineId != null && calendarOwners.contains(line.lineId)) {
                return true;
            }
        }
        return false;
    }

    private static void dfsCycle(
            String node,
            Map<String, Set<String>> g,
            Set<String> visiting,
            Set<String> visited,
            List<String> stack,
            Set<String> cycleNodes) {
        visiting.add(node);
        stack.add(node);
        for (String next : g.getOrDefault(node, Set.of())) {
            if (visiting.contains(next)) {
                int idx = stack.indexOf(next);
                if (idx >= 0) {
                    cycleNodes.addAll(stack.subList(idx, stack.size()));
                } else {
                    cycleNodes.add(next);
                }
                continue;
            }
            if (!visited.contains(next) && g.containsKey(next)) {
                dfsCycle(next, g, visiting, visited, stack, cycleNodes);
            }
        }
        visiting.remove(node);
        visited.add(node);
        stack.remove(stack.size() - 1);
    }
}
