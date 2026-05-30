package com.plantops.solver.masterplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * 主计划求解用问题事实：按日滚动后的物料期末可用量（MRP 快照）+ BOM/工艺快照（求解线程内只读）。
 */
public class MaterialFeasibilityContext {

    public record ComponentNeed(
            String componentProductCode,
            BigDecimal componentQty,
            boolean critical,
            boolean manufactured) {
    }

    private final Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial;
    private final Map<String, List<ComponentNeed>> bomByParent;
    private final Map<String, List<ComponentNeed>> bomByFinishedAndParent;
    private final Set<String> manufacturedProducts;

    public MaterialFeasibilityContext(
            Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial,
            Map<String, List<ComponentNeed>> bomByParent,
            Set<String> manufacturedProducts) {
        this(closingByMaterial, bomByParent, Map.of(), manufacturedProducts);
    }

    public MaterialFeasibilityContext(
            Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial,
            Map<String, List<ComponentNeed>> bomByParent,
            Map<String, List<ComponentNeed>> bomByFinishedAndParent,
            Set<String> manufacturedProducts) {
        this.closingByMaterial = closingByMaterial != null ? closingByMaterial : Map.of();
        this.bomByParent = bomByParent != null ? bomByParent : Map.of();
        this.bomByFinishedAndParent = bomByFinishedAndParent != null ? bomByFinishedAndParent : Map.of();
        this.manufacturedProducts = manufacturedProducts != null ? manufacturedProducts : Set.of();
    }

    public MaterialFeasibilityContext(Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial) {
        this(closingByMaterial, Map.of(), Set.of());
    }

    public BigDecimal closingOn(String productCode, LocalDate date) {
        if (productCode == null || date == null) {
            return BigDecimal.ZERO;
        }
        NavigableMap<LocalDate, BigDecimal> series = closingByMaterial.get(productCode);
        if (series == null || series.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map.Entry<LocalDate, BigDecimal> floor = series.floorEntry(date);
        return floor != null ? floor.getValue() : BigDecimal.ZERO;
    }

    public List<ComponentNeed> componentsOf(String parentProductCode) {
        if (parentProductCode == null) {
            return List.of();
        }
        return bomByParent.getOrDefault(parentProductCode, List.of());
    }

    public List<ComponentNeed> componentsOfFinished(String finishedProductCode, String parentProductCode) {
        if (parentProductCode == null) {
            return List.of();
        }
        if (finishedProductCode != null && !finishedProductCode.isBlank()) {
            List<ComponentNeed> scoped = bomByFinishedAndParent.get(
                    finishedAndParentKey(finishedProductCode, parentProductCode));
            if (scoped != null && !scoped.isEmpty()) {
                return scoped;
            }
        }
        return componentsOf(parentProductCode);
    }

    public static String finishedAndParentKey(String finishedProductCode, String parentProductCode) {
        return finishedProductCode + "\u0001" + parentProductCode;
    }

    public boolean isManufactured(String productCode) {
        return productCode != null && manufacturedProducts.contains(productCode);
    }

    public Map<String, NavigableMap<LocalDate, BigDecimal>> getClosingByMaterial() {
        return Collections.unmodifiableMap(closingByMaterial);
    }
}
