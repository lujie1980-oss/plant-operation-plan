package com.plantops.ontology.fulfillment;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.scenario.RuleScopeHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 成品料号下的 BOM 闭包与按父项索引的子件行，建链时一次加载、避免重复查询。
 */
public final class BomClosureIndex {

    private final String finishedProductCode;
    private final Set<String> productClosure;
    private final Map<String, List<BomComponentEntity>> childrenByParent;

    private BomClosureIndex(
            String finishedProductCode,
            Set<String> productClosure,
            Map<String, List<BomComponentEntity>> childrenByParent) {
        this.finishedProductCode = finishedProductCode;
        this.productClosure = productClosure;
        this.childrenByParent = childrenByParent;
    }

    public static BomClosureIndex forFinishedProduct(String finishedProductCode, RuleScopeHelper ruleScopeHelper) {
        if (finishedProductCode == null || finishedProductCode.isBlank()) {
            return empty(finishedProductCode);
        }
        List<BomComponentEntity> scopedRows = BomComponentEntity.findByFinishedProduct(finishedProductCode);
        if (scopedRows.isEmpty()) {
            scopedRows = BomComponentEntity.listInWorkspace().stream()
                    .filter(b -> finishedProductCode.equals(b.parentProductCode)
                            || finishedProductCode.equals(b.finishedProductCode))
                    .toList();
        }

        Map<String, List<BomComponentEntity>> childrenByParent = new LinkedHashMap<>();
        for (BomComponentEntity bom : scopedRows) {
            if (bom.parentProductCode == null || bom.componentProductCode == null) {
                continue;
            }
            if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                continue;
            }
            childrenByParent
                    .computeIfAbsent(bom.parentProductCode, k -> new ArrayList<>())
                    .add(bom);
        }

        Set<String> closure = new LinkedHashSet<>();
        closure.add(finishedProductCode);
        Queue<String> queue = new ArrayDeque<>();
        queue.add(finishedProductCode);
        while (!queue.isEmpty()) {
            String parent = queue.poll();
            for (BomComponentEntity bom : childrenByParent.getOrDefault(parent, List.of())) {
                if (closure.add(bom.componentProductCode)) {
                    queue.add(bom.componentProductCode);
                }
            }
        }

        return new BomClosureIndex(finishedProductCode, Set.copyOf(closure), Map.copyOf(childrenByParent));
    }

    private static BomClosureIndex empty(String finishedProductCode) {
        return new BomClosureIndex(finishedProductCode, Set.of(), Map.of());
    }

    public String finishedProductCode() {
        return finishedProductCode;
    }

    public boolean containsProduct(String productCode) {
        return productCode != null && productClosure.contains(productCode);
    }

    public Set<String> productClosure() {
        return productClosure;
    }

    /** 等价于 {@link BomComponentEntity#findChildren(String, String)}，但使用内存索引。 */
    public List<BomComponentEntity> children(String parentProductCode) {
        if (parentProductCode == null || parentProductCode.isBlank()) {
            return List.of();
        }
        return childrenByParent.getOrDefault(parentProductCode, List.of());
    }
}
