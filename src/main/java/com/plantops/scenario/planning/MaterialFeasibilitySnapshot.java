package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.MaterialFeasibilityContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/** 从本体 PISPP 派生的 MRP 求解快照（只读投影，非第二套物料模型）。 */
public record MaterialFeasibilitySnapshot(
        Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial,
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bomByParent,
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bomByFinishedAndParent,
        Set<String> manufacturedProducts) {

    public MaterialFeasibilityContext toContext() {
        return new MaterialFeasibilityContext(
                closingByMaterial != null ? closingByMaterial : Map.of(),
                bomByParent != null ? bomByParent : Map.of(),
                bomByFinishedAndParent != null ? bomByFinishedAndParent : Map.of(),
                manufacturedProducts != null ? manufacturedProducts : Set.of());
    }
}
