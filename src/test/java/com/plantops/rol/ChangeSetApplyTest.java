package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeSetApplyTest {

    @Test
    void changeSetPropertyChangeMatchesDirectApplyPropertyChange() {
        OntologyGraph directGraph = buildTwoPeriodGraph();
        OntologyGraph changeSetGraph = buildTwoPeriodGraph();
        RolEngine directEngine = RolEngine.withDefaultPispRules(directGraph);
        RolEngine changeSetEngine = RolEngine.withDefaultPispRules(changeSetGraph);

        ProductInStockingPointPeriod directTarget = period(directGraph, "P-1");
        ProductInStockingPointPeriod changeSetTarget = period(changeSetGraph, "P-1");

        directEngine.applyPropertyChange(directTarget, "plannedSupplyTotal", 100.0);

        var changeSet = new ChangeSet(List.of(new ChangeOperation(
                ChangeOperation.TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD,
                changeSetTarget.getId(),
                "plannedSupplyTotal",
                100.0)));
        new RolTransaction().apply(changeSet, changeSetGraph, changeSetEngine);

        assertEquals(
                directTarget.getPlannedSupplyTotal(),
                changeSetTarget.getPlannedSupplyTotal(),
                1e-6);
        assertEquals(directTarget.getOnHand(), changeSetTarget.getOnHand(), 1e-6);
        assertEquals(
                directTarget.getPlannedInventoryLevel(),
                changeSetTarget.getPlannedInventoryLevel(),
                1e-6);
        assertEquals(
                period(directGraph, "P-2").getOnHand(),
                period(changeSetGraph, "P-2").getOnHand(),
                1e-6);
    }

    private static OntologyGraph buildTwoPeriodGraph() {
        String pispId = "PISP-TEST-DEFAULT-FG";
        List<Period> periods = List.of(
                new Period("P-0", 0, LocalDate.now(), LocalDate.now()),
                new Period("P-1", 1, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)),
                new Period("P-2", 2, LocalDate.now().plusDays(2), LocalDate.now().plusDays(2)));

        OntologyGraph.Builder builder = OntologyGraph.builder()
                .pisp(new ProductInStockingPoint(pispId, "TEST", OntologyIds.DEFAULT_FG, "TEST"))
                .periodsOrdered(periods);

        List<ProductInStockingPointPeriod> ordered = new ArrayList<>();
        for (Period period : periods) {
            var pispp = new ProductInStockingPointPeriod(
                    OntologyIds.pisppId(pispId, period.getSequenceNr()),
                    pispId,
                    period.getId());
            builder.pispPeriod(pispp);
            ordered.add(pispp);
        }

        var p0 = ordered.get(0);
        p0.setOnHand(100);
        p0.setPlannedDemandQuantityTotal(100);
        p0.recalculatePlanningFields();

        var p1 = ordered.get(1);
        p1.setOnHand(0);
        p1.setPlannedSupplyTotal(0);
        p1.setPlannedDemandQuantityTotal(0);
        p1.recalculatePlanningFields();

        PispRolling.rollChain(ordered);

        return builder.build();
    }

    private static ProductInStockingPointPeriod period(OntologyGraph graph, String periodId) {
        return graph.pispPeriodsById().values().stream()
                .filter(p -> periodId.equals(p.getPeriodId()))
                .findFirst()
                .orElseThrow();
    }
}
