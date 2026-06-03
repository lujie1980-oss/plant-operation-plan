package com.plantops.scenario;

import com.plantops.api.dto.WorkOrderGenerationBatchResultDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;
import com.plantops.domain.SalesOrderLineId;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import com.plantops.scenario.mrp.MrpDemandBucket;
import com.plantops.scenario.mrp.MrpDemandBucket.Key;
import com.plantops.scenario.mrp.MrpDemandBucket.PegLine;
import com.plantops.scenario.mrp.MrpLotSizing;
import com.plantops.scenario.mrp.MrpLotSizing.LotRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 场景级 MRP：按 BOM 层级汇总 (productCode, needDate) 需求，生成合并工单与 pegging。
 */
@ApplicationScoped
public class MrpExplosionService {

    public static final int DEMAND_OFFSET_DAYS_PER_LEVEL = 3;

    @Inject
    Instance<MrpExplosionService> self;

    @Inject
    RuleScopeHelper ruleScopeHelper;

    @Transactional(TxType.NOT_SUPPORTED)
    public WorkOrderGenerationBatchResultDto regenerateMergedWorkOrders(boolean replaceExisting) {
        return regenerateMergedWorkOrdersSkipping(Set.of(), replaceExisting);
    }

    @Transactional(TxType.NOT_SUPPORTED)
    public WorkOrderGenerationBatchResultDto regenerateMergedWorkOrdersSkipping(Set<SalesOrderLineId> blocked) {
        return regenerateMergedWorkOrdersSkipping(blocked, true);
    }

    @Transactional(TxType.NOT_SUPPORTED)
    public WorkOrderGenerationBatchResultDto regenerateMergedWorkOrdersSkipping(
            Set<SalesOrderLineId> blocked,
            boolean replaceExisting) {
        int woCount = self.get().runExplosion(blocked != null ? blocked : Set.of(), replaceExisting);
        List<WorkOrderGenerationResultDto> details = new ArrayList<>();
        int lines = 0;
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            if (blocked != null && blocked.contains(order.toId())) {
                continue;
            }
            lines++;
            List<String> woNos = WorkOrderPeggingEntity.findByOrderLine(order.salesOrderNo, order.salesOrderLineNo)
                    .stream()
                    .map(p -> p.workOrderNo)
                    .distinct()
                    .sorted()
                    .toList();
            details.add(new WorkOrderGenerationResultDto(
                    order.salesOrderNo, order.salesOrderLineNo, woNos.size(), woNos));
        }
        return new WorkOrderGenerationBatchResultDto(lines, woCount, details);
    }

    @Transactional
    public int runExplosion(Set<SalesOrderLineId> blocked, boolean replaceExisting) {
        if (replaceExisting) {
            WorkOrderEntity.deleteMrpRegeneratable();
        }

        List<BomComponentEntity> allBom = BomComponentEntity.listInWorkspace();
        Map<String, List<BomComponentEntity>> childrenByParent = indexChildrenByParent(allBom);
        Map<String, LotRule> lotRules = buildLotRules(allBom);

        Map<Key, MrpDemandBucket> levelBuckets = buildLevelZero(blocked);
        if (levelBuckets.isEmpty()) {
            return 0;
        }

        Map<Key, String> keyToWoNo = new HashMap<>();
        Map<Key, List<String>> keyToParentWoNos = new HashMap<>();
        int sequence = WorkOrderEntity.nextSequenceNo();
        int woCount = 0;
        int level = 0;

        while (!levelBuckets.isEmpty()) {
            Map<Key, MrpDemandBucket> childAccum = new LinkedHashMap<>();
            final int currentLevel = level;

            for (var entry : levelBuckets.entrySet()) {
                Key key = entry.getKey();
                MrpDemandBucket bucket = entry.getValue();
                if (bucket.grossQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                String product = key.productCode();
                BigDecimal plannedQty = bucket.grossQty;
                String woNo = null;

                if (hasRouting(product)) {
                    LotRule rule = lotRules.getOrDefault(
                            product, MrpLotSizing.lotRuleForProduct(product, allBom));
                    plannedQty = MrpLotSizing.apply(bucket.grossQty, rule);
                    woNo = allocateUniqueWorkOrderNo(product, key.needDate(), keyToWoNo.size() + 1);
                    sequence = persistWorkOrder(
                            woNo,
                            keyToParentWoNos.getOrDefault(key, List.of()),
                            product,
                            plannedQty,
                            key.needDate(),
                            currentLevel,
                            sequence,
                            bucket.pegLines);
                    keyToWoNo.put(key, woNo);
                    woCount++;
                    linkDependencies(keyToParentWoNos.getOrDefault(key, List.of()), woNo);
                }

                List<BomComponentEntity> children = childrenByParent.getOrDefault(product, List.of());
                for (BomComponentEntity bom : children) {
                    if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                        continue;
                    }
                    BigDecimal componentQty = bom.componentQty != null ? bom.componentQty : BigDecimal.ONE;
                    if (bom.scrapRate != null && bom.scrapRate.compareTo(BigDecimal.ZERO) > 0) {
                        componentQty = componentQty.multiply(BigDecimal.ONE.add(bom.scrapRate));
                    }
                    BigDecimal childGross = componentQty.multiply(plannedQty);
                    LocalDate childNeedDate = key.needDate().minusDays((long) (currentLevel + 1) * DEMAND_OFFSET_DAYS_PER_LEVEL);
                    Key childKey = new Key(bom.componentProductCode, childNeedDate);

                    List<PegLine> childPegs = propagatePegs(bucket.pegLines, bucket.grossQty, plannedQty, componentQty);
                    MrpDemandBucket childBucket = childAccum.computeIfAbsent(childKey, k -> new MrpDemandBucket(currentLevel + 1));
                    childBucket.addGross(childGross, null);
                    for (PegLine peg : childPegs) {
                        childBucket.addPeg(peg);
                    }

                    if (woNo != null && hasRouting(bom.componentProductCode)) {
                        keyToParentWoNos
                                .computeIfAbsent(childKey, k -> new ArrayList<>())
                                .add(woNo);
                    } else if (woNo != null) {
                        // 采购件无 WO，但仍向下传递 peg（已在 childAccum）
                    }
                }
            }

            level++;
            levelBuckets = childAccum;
        }

        return woCount;
    }

    private Map<Key, MrpDemandBucket> buildLevelZero(Set<SalesOrderLineId> blocked) {
        Map<Key, MrpDemandBucket> buckets = new LinkedHashMap<>();
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            if (blocked.contains(order.toId())) {
                continue;
            }
            if (!hasRouting(order.productCode)) {
                continue;
            }
            Key key = new Key(order.productCode, order.dueDate);
            PegLine peg = new PegLine(
                    order.salesOrderNo,
                    order.salesOrderLineNo,
                    order.productCode,
                    order.orderQty);
            MrpDemandBucket.addTo(buckets, key, 0, order.orderQty, peg);
        }
        return buckets;
    }

    private static List<PegLine> propagatePegs(
            List<PegLine> parentPegs,
            BigDecimal parentGross,
            BigDecimal parentPlanned,
            BigDecimal componentQtyPerParent) {
        if (parentPegs.isEmpty() || parentGross.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<PegLine> out = new ArrayList<>();
        for (PegLine peg : parentPegs) {
            BigDecimal share = peg.qty().divide(parentGross, 8, RoundingMode.HALF_UP);
            BigDecimal childQty = share.multiply(parentPlanned).multiply(componentQtyPerParent);
            out.add(new PegLine(
                    peg.salesOrderNo(),
                    peg.salesOrderLineNo(),
                    peg.finishedProductCode(),
                    childQty));
        }
        return out;
    }

    private int persistWorkOrder(
            String woNo,
            List<String> parentWoNos,
            String productCode,
            BigDecimal quantity,
            LocalDate needDate,
            int bomLevel,
            int sequence,
            List<PegLine> pegLines) {
        WorkOrderEntity existing = WorkOrderEntity.findByNo(woNo);
        WorkOrderEntity wo;
        if (existing != null && WorkOrderEntity.isMrpRegeneratable(existing)) {
            wo = existing;
            WorkOrderPeggingEntity.deleteForWorkOrders(List.of(woNo));
        } else {
            wo = new WorkOrderEntity();
            wo.workOrderNo = woNo;
            wo.ensureWorkspace();
            wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        }
        wo.salesOrderNo = null;
        wo.salesOrderLineNo = 0;
        wo.productCode = productCode;
        wo.quantity = quantity;
        wo.resourceId = resolveResourceId(productCode);
        wo.sequenceNo = sequence++;
        wo.parentWorkOrderNo = parentWoNos.isEmpty() ? null : parentWoNos.get(0);
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        wo.needDate = needDate;
        wo.bomLevel = bomLevel;
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.pendingScheduleEligible = Boolean.TRUE;
        wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_NONE;
        if (existing == null) {
            wo.persist();
        }

        for (PegLine peg : pegLines) {
            WorkOrderPeggingEntity p = new WorkOrderPeggingEntity();
            p.workOrderNo = woNo;
            p.salesOrderNo = peg.salesOrderNo();
            p.salesOrderLineNo = peg.salesOrderLineNo();
            p.finishedProductCode = peg.finishedProductCode();
            p.peggedQty = peg.qty();
            p.needDate = needDate;
            p.ensureWorkspace();
            p.persist();
        }
        return sequence;
    }

    private static void linkDependencies(List<String> parentWoNos, String childWoNo) {
        for (String parent : parentWoNos) {
            if (parent == null || parent.equals(childWoNo)) {
                continue;
            }
            WorkOrderBomDependencyEntity dep = new WorkOrderBomDependencyEntity();
            dep.parentWorkOrderNo = parent;
            dep.childWorkOrderNo = childWoNo;
            dep.ensureWorkspace();
            dep.persist();
        }
    }

    /**
     * 生成唯一工单号：已存在且不可重建（如已下发）时递增序号，避免 UK_WORK_ORDER_WS 冲突。
     */
    static String allocateUniqueWorkOrderNo(String productCode, LocalDate needDate, int preferredSeq) {
        int seq = Math.max(1, preferredSeq);
        while (seq < 10_000) {
            String candidate = formatWorkOrderNo(productCode, needDate, seq);
            WorkOrderEntity existing = WorkOrderEntity.findByNo(candidate);
            if (existing == null || WorkOrderEntity.isMrpRegeneratable(existing)) {
                return candidate;
            }
            seq++;
        }
        throw new IllegalStateException(
                "无法为产品 " + productCode + " / " + needDate + " 分配唯一 MRP 工单号（序号已用尽）");
    }

    private static String formatWorkOrderNo(String productCode, LocalDate needDate, int seq) {
        String safeProduct = productCode.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeProduct.length() > 24) {
            safeProduct = safeProduct.substring(0, 24);
        }
        String datePart = needDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return "WO-MRP-" + safeProduct + "-" + datePart + "-" + seq;
    }

    private static Map<String, List<BomComponentEntity>> indexChildrenByParent(List<BomComponentEntity> allBom) {
        Map<String, Map<String, BomComponentEntity>> deduped = new TreeMap<>();
        for (BomComponentEntity row : allBom) {
            if (row.parentProductCode == null || row.componentProductCode == null) {
                continue;
            }
            deduped
                    .computeIfAbsent(row.parentProductCode, k -> new LinkedHashMap<>())
                    .merge(
                            row.componentProductCode,
                            row,
                            (a, b) -> a.componentQty != null
                                            && b.componentQty != null
                                            && b.componentQty.compareTo(a.componentQty) > 0
                                    ? b
                                    : a);
        }
        Map<String, List<BomComponentEntity>> out = new LinkedHashMap<>();
        deduped.forEach((parent, byChild) -> out.put(parent, List.copyOf(byChild.values())));
        return out;
    }

    private static Map<String, LotRule> buildLotRules(List<BomComponentEntity> allBom) {
        Map<String, LotRule> byProduct = new HashMap<>();
        for (BomComponentEntity row : allBom) {
            if (row.parentProductCode == null) {
                continue;
            }
            LotRule existing = byProduct.get(row.parentProductCode);
            LotRule rowRule = new LotRule(row.lotSize, row.lotSizeMultiple);
            if (existing == null) {
                byProduct.put(row.parentProductCode, rowRule);
            } else {
                BigDecimal maxLot = maxNullable(existing.lotSize(), rowRule.lotSize());
                BigDecimal maxMult = maxNullable(existing.lotSizeMultiple(), rowRule.lotSizeMultiple());
                byProduct.put(row.parentProductCode, new LotRule(maxLot, maxMult));
            }
        }
        return byProduct;
    }

    private static BigDecimal maxNullable(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.max(b);
    }

    private static boolean hasRouting(String productCode) {
        return ProductResourceEntity.findFirstByProduct(productCode) != null;
    }

    private static String resolveResourceId(String productCode) {
        ProductResourceEntity pr = ProductResourceEntity.findFirstByProduct(productCode);
        return pr != null ? pr.resourceId : "UNKNOWN";
    }
}
