package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.supply.SupplyOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 关联的 PISPP MRP 传播：父件 {@code plannedSupplyTotalMrp} 变更时，重算组件
 * {@code plannedDemandQuantityTotal}（与 {@link PispPeriodDerivations} 的链式 onHand 协同）。
 */
public final class PispMrpDerivations {

    private PispMrpDerivations() {
    }

    public static List<Derivation> derivations(OntologyGraph graph) {
        Map<String, List<BomInbound>> inboundByComponent = indexBomInbound(graph);
        if (inboundByComponent.isEmpty()) {
            return List.of();
        }
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        Map<String, Map<String, ProductInStockingPointPeriod>> pisppByProductAndPeriod =
                indexPisppByProductAndPeriod(graph);

        List<Derivation> derivations = new ArrayList<>();
        for (Map.Entry<String, List<BomInbound>> entry : inboundByComponent.entrySet()) {
            String componentProduct = entry.getKey();
            List<BomInbound> inbound = entry.getValue();
            Map<String, ProductInStockingPointPeriod> periodsById =
                    pisppByProductAndPeriod.get(componentProduct);
            if (periodsById == null) {
                continue;
            }
            for (ProductInStockingPointPeriod componentPispp : periodsById.values()) {
                registerComponentDemandDerivation(
                        derivations,
                        periodIndex,
                        componentPispp,
                        inbound,
                        pisppByProductAndPeriod);
            }
        }
        return derivations;
    }

    private static void registerComponentDemandDerivation(
            List<Derivation> derivations,
            PeriodIndex periodIndex,
            ProductInStockingPointPeriod componentPispp,
            List<BomInbound> inbound,
            Map<String, Map<String, ProductInStockingPointPeriod>> pisppByProductAndPeriod) {
        Set<String> dependencies = new HashSet<>();
        for (BomInbound link : inbound) {
            ProductInStockingPointPeriod parentPispp = pisppByProductAndPeriod
                    .getOrDefault(link.parentProductCode(), Map.of())
                    .get(componentPispp.getPeriodId());
            if (parentPispp != null) {
                dependencies.add(Derivation.propertyKey(parentPispp.getId(), "plannedSupplyTotalMrp"));
            }
        }
        if (dependencies.isEmpty()) {
            return;
        }
        String componentPisppId = componentPispp.getId();
        derivations.add(new Derivation(
                Derivation.propertyKey(componentPisppId, "plannedDemandQuantityTotal"),
                dependencies,
                (g, targetKey) -> recomputeComponentDemand(
                        g,
                        periodIndex,
                        componentPisppId,
                        inbound,
                        pisppByProductAndPeriod)));
    }

    private static void recomputeComponentDemand(
            OntologyGraph graph,
            PeriodIndex periodIndex,
            String componentPisppId,
            List<BomInbound> inbound,
            Map<String, Map<String, ProductInStockingPointPeriod>> pisppByProductAndPeriod) {
        ProductInStockingPointPeriod componentPispp = graph.pispPeriod(componentPisppId);
        if (componentPispp == null) {
            return;
        }
        ProductInStockingPoint componentPisp = graph.pisp(componentPispp.getPispId());
        if (componentPisp == null || componentPisp.getProductCode() == null) {
            return;
        }
        String periodId = componentPispp.getPeriodId();
        double demand = independentDemand(graph, periodIndex, componentPisp.getProductCode(), periodId);
        for (BomInbound link : inbound) {
            ProductInStockingPointPeriod parentPispp = pisppByProductAndPeriod
                    .getOrDefault(link.parentProductCode(), Map.of())
                    .get(periodId);
            if (parentPispp != null) {
                demand += parentPispp.getPlannedSupplyTotalMrp() * link.qtyPerUnit();
            }
        }
        componentPispp.setPlannedDemandQuantityTotal(demand);
    }

    private static double independentDemand(
            OntologyGraph graph,
            PeriodIndex periodIndex,
            String componentProduct,
            String periodId) {
        double sum = 0.0;
        for (Demand demand : graph.demandsById().values()) {
            if (demand.getSourceType() == DemandSourceType.BOM_COMPONENT) {
                continue;
            }
            if (!componentProduct.equals(demand.getProductCode()) || demand.getNeedDate() == null) {
                continue;
            }
            Period period = periodForDate(graph, periodIndex, demand.getNeedDate());
            if (period != null && periodId.equals(period.getId())) {
                sum += demand.getQuantity();
            }
        }
        return sum;
    }

    private static Period periodForDate(OntologyGraph graph, PeriodIndex periodIndex, java.time.LocalDate date) {
        List<Period> periods = graph.periodsOrdered();
        if (periods.isEmpty()) {
            return null;
        }
        int seq = periodIndex.sequenceFor(date);
        if (seq < 0 || seq >= periods.size()) {
            return null;
        }
        return periods.get(seq);
    }

    private static Map<String, List<BomInbound>> indexBomInbound(OntologyGraph graph) {
        Map<String, List<BomInbound>> inboundByComponent = new LinkedHashMap<>();
        Map<String, BomInbound> dedupe = new HashMap<>();
        for (Demand demand : graph.demandsById().values()) {
            if (demand.getSourceType() != DemandSourceType.BOM_COMPONENT) {
                continue;
            }
            SupplyOrder supplyOrder = graph.supplyOrder(demand.getSourceId());
            if (supplyOrder == null || supplyOrder.getProductCode() == null) {
                continue;
            }
            double parentQty = supplyOrder.getQuantity();
            if (parentQty <= 0) {
                continue;
            }
            String parentProduct = supplyOrder.getProductCode();
            String componentProduct = demand.getProductCode();
            double qtyPerUnit = demand.getQuantity() / parentQty;
            String key = parentProduct + "->" + componentProduct;
            dedupe.putIfAbsent(key, new BomInbound(parentProduct, componentProduct, qtyPerUnit));
        }
        for (BomInbound link : dedupe.values()) {
            inboundByComponent
                    .computeIfAbsent(link.componentProductCode(), ignored -> new ArrayList<>())
                    .add(link);
        }
        return inboundByComponent;
    }

    private static Map<String, Map<String, ProductInStockingPointPeriod>> indexPisppByProductAndPeriod(
            OntologyGraph graph) {
        Map<String, Map<String, ProductInStockingPointPeriod>> index = new LinkedHashMap<>();
        for (ProductInStockingPointPeriod pispp : graph.pispPeriodsById().values()) {
            ProductInStockingPoint pisp = graph.pisp(pispp.getPispId());
            if (pisp == null || pisp.getProductCode() == null) {
                continue;
            }
            index.computeIfAbsent(pisp.getProductCode(), ignored -> new LinkedHashMap<>())
                    .put(pispp.getPeriodId(), pispp);
        }
        return index;
    }

    private record BomInbound(String parentProductCode, String componentProductCode, double qtyPerUnit) {
    }
}
