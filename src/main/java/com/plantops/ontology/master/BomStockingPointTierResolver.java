package com.plantops.ontology.master;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 按 BOM 阶层将物料映射到四级库存点：RAW → SFG-A → SFG-B → FG（自底向上对应四阶 BOM）。
 */
@ApplicationScoped
public class BomStockingPointTierResolver {

    public record TierAssignment(String productCode, int tierFromTop, String stockingPointId, String tierLabel) {
    }

    public List<StockingPoint> orderedStockingPoints() {
        return List.of(
                StockingPoint.raw(),
                StockingPoint.sfgA(),
                StockingPoint.sfgB(),
                StockingPoint.fg());
    }

    public Map<String, TierAssignment> assignAllMaterials() {
        Map<String, Integer> tierFromTop = computeTierFromTop();
        Map<String, TierAssignment> assignments = new LinkedHashMap<>();

        Set<String> productCodes = collectAllProductCodes();
        for (String productCode : productCodes) {
            int tier = tierFromTop.getOrDefault(productCode, inferTierWithoutBom(productCode));
            String spId = stockingPointForTier(tier);
            assignments.put(productCode, new TierAssignment(productCode, tier, spId, tierLabel(tier)));
        }
        return assignments;
    }

    public String stockingPointForProduct(String productCode) {
        return assignAllMaterials()
                .getOrDefault(productCode, new TierAssignment(productCode, 3, StockingPoint.RAW, "原料"))
                .stockingPointId();
    }

    public static String tierLabel(int tierFromTop) {
        return switch (tierFromTop) {
            case 0 -> "总成";
            case 1 -> "二阶";
            case 2 -> "一阶";
            default -> "原料";
        };
    }

    public static String stockingPointForTier(int tierFromTop) {
        return switch (tierFromTop) {
            case 0 -> StockingPoint.FG;
            case 1 -> StockingPoint.SFG_B;
            case 2 -> StockingPoint.SFG_A;
            default -> StockingPoint.RAW;
        };
    }

    private Map<String, Integer> computeTierFromTop() {
        Map<String, Integer> tierByProduct = new HashMap<>();
        Set<String> finishedProducts = new LinkedHashSet<>();
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (bom.finishedProductCode != null && !bom.finishedProductCode.isBlank()) {
                finishedProducts.add(bom.finishedProductCode);
            }
        }

        Map<String, Set<String>> childrenByParentScoped = new HashMap<>();
        Set<String> manufacturedParents = new HashSet<>();
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (bom.parentProductCode == null || bom.componentProductCode == null) {
                continue;
            }
            manufacturedParents.add(bom.parentProductCode);
            if (bom.finishedProductCode != null && !bom.finishedProductCode.isBlank()) {
                String scopedKey = bom.finishedProductCode + ">" + bom.parentProductCode;
                childrenByParentScoped
                        .computeIfAbsent(scopedKey, k -> new LinkedHashSet<>())
                        .add(bom.componentProductCode);
            }
        }

        for (String finished : finishedProducts) {
            bfsFromRoot(finished, finished, tierByProduct, childrenByParentScoped, manufacturedParents);
        }

        return tierByProduct;
    }

    private static void bfsFromRoot(
            String finishedProduct,
            String rootProduct,
            Map<String, Integer> tierByProduct,
            Map<String, Set<String>> childrenByParentScoped,
            Set<String> manufacturedParents) {
        Queue<TierNode> queue = new ArrayDeque<>();
        queue.add(new TierNode(rootProduct, 0));
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            TierNode current = queue.poll();
            if (!visited.add(finishedProduct + ">" + current.productCode() + "@" + current.tier())) {
                continue;
            }
            tierByProduct.merge(current.productCode(), current.tier(), Math::min);

            String scopedKey = finishedProduct + ">" + current.productCode();
            Set<String> children = childrenByParentScoped.getOrDefault(scopedKey, Set.of());
            for (String child : children) {
                int childTier = current.tier() + 1;
                tierByProduct.merge(child, childTier, Math::min);
                if (manufacturedParents.contains(child)) {
                    queue.add(new TierNode(child, childTier));
                }
            }
        }
    }

    private record TierNode(String productCode, int tier) {
    }

    private static int inferTierWithoutBom(String productCode) {
        if (MaterialEntity.findByCode(productCode) == null) {
            return 3;
        }
        if (isFinishedGoodCandidate(productCode)) {
            return 0;
        }
        if (ProductResourceEntity.hasRouting(productCode)) {
            return 2;
        }
        return 3;
    }

    private static boolean isFinishedGoodCandidate(String productCode) {
        for (SalesOrderLineEntity line : SalesOrderLineEntity.listInWorkspace()) {
            if (productCode.equals(line.productCode)) {
                return true;
            }
        }
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (productCode.equals(bom.finishedProductCode)
                    && productCode.equals(bom.parentProductCode)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> collectAllProductCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (MaterialEntity material : MaterialEntity.listInWorkspace()) {
            if (material.materialCode != null && !material.materialCode.isBlank()) {
                codes.add(material.materialCode);
            }
        }
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (bom.parentProductCode != null && !bom.parentProductCode.isBlank()) {
                codes.add(bom.parentProductCode);
            }
            if (bom.componentProductCode != null && !bom.componentProductCode.isBlank()) {
                codes.add(bom.componentProductCode);
            }
            if (bom.finishedProductCode != null && !bom.finishedProductCode.isBlank()) {
                codes.add(bom.finishedProductCode);
            }
        }
        return codes;
    }
}
