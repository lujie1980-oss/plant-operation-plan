package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.scheduling.PispDailyClosingProjection;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.scenario.RuleScopeHelper;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
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
import java.util.stream.Collectors;

@ApplicationScoped
public class MaterialFeasibilitySnapshotBuilder {

    @Inject
    RuleScopeHelper ruleScopeHelper;

    public MaterialFeasibilitySnapshot fromGraph(OntologyGraph graph) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> closingByMaterial =
                PispDailyClosingProjection.projectGraph(graph);
        BomSnapshot bomSnapshot = loadBomSnapshot();
        return new MaterialFeasibilitySnapshot(
                closingByMaterial,
                bomSnapshot.byParent(),
                bomSnapshot.byFinishedAndParent(),
                loadManufacturedProducts());
    }

    public MaterialFeasibilityContext toContext(OntologyGraph graph) {
        return fromGraph(graph).toContext();
    }

    private record BomSnapshot(
            Map<String, List<MaterialFeasibilityContext.ComponentNeed>> byParent,
            Map<String, List<MaterialFeasibilityContext.ComponentNeed>> byFinishedAndParent) {
    }

    private BomSnapshot loadBomSnapshot() {
        Set<String> manufactured = loadManufacturedProducts();
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bomByParent = new HashMap<>();
        Map<String, List<MaterialFeasibilityContext.ComponentNeed>> bomByFinishedAndParent = new HashMap<>();
        for (BomComponentEntity row : BomComponentEntity.listInWorkspace()) {
            boolean critical = ruleScopeHelper.criticalForMasterPlan(row);
            MaterialFeasibilityContext.ComponentNeed need = new MaterialFeasibilityContext.ComponentNeed(
                    row.componentProductCode,
                    row.componentQty != null ? row.componentQty : BigDecimal.ZERO,
                    critical,
                    manufactured.contains(row.componentProductCode));
            bomByParent.computeIfAbsent(row.parentProductCode, ignored -> new ArrayList<>()).add(need);
            if (row.finishedProductCode != null && !row.finishedProductCode.isBlank()) {
                String key = MaterialFeasibilityContext.finishedAndParentKey(
                        row.finishedProductCode, row.parentProductCode);
                bomByFinishedAndParent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(need);
            }
        }
        return new BomSnapshot(bomByParent, bomByFinishedAndParent);
    }

    private Set<String> loadManufacturedProducts() {
        return ProductResourceEntity.listInWorkspace().stream()
                .map(pr -> pr.productCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
