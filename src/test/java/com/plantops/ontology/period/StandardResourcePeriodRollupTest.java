package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardResourcePeriodRollupTest {

    @Test
    void rollsUpChildCapacitiesToParentDaySrp() {
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(java.time.LocalDate.of(2026, 6, 1));
        PeriodIndex index = PeriodIndex.of(periods);
        Period parent = periods.get(0);
        Period s1 = periods.get(1);
        Period s2 = periods.get(2);

        String resourceId = "RES-ROLLUP";
        Map<String, StandardResourcePeriod> srps = new java.util.LinkedHashMap<>();
        srps.put(
                OntologyIds.srpId(resourceId, parent.getSequenceNr()),
                srp(OntologyIds.srpId(resourceId, parent.getSequenceNr()), resourceId, parent.getId(), 0, 0));
        srps.put(
                OntologyIds.srpId(resourceId, s1.getSequenceNr()),
                srp(OntologyIds.srpId(resourceId, s1.getSequenceNr()), resourceId, s1.getId(), 480, 60));
        srps.put(
                OntologyIds.srpId(resourceId, s2.getSequenceNr()),
                srp(OntologyIds.srpId(resourceId, s2.getSequenceNr()), resourceId, s2.getId(), 360, 30));

        StandardResourcePeriodRollup.rollupParentCapacities(srps, periods, index);
        srps.values().forEach(StandardResourcePeriod::recalculateCapacityFields);

        StandardResourcePeriod parentSrp = srps.get(OntologyIds.srpId(resourceId, parent.getSequenceNr()));
        assertEquals(840, parentSrp.getTotalCapacity());
        assertEquals(90, parentSrp.getReservedCapacity());
    }

    @Test
    void rollsUpReservedOnOntologyGraph() {
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(java.time.LocalDate.of(2026, 6, 1));
        Period parent = periods.get(0);
        Period s1 = periods.get(1);
        Period s2 = periods.get(2);
        String resourceId = "RES-ROLLUP-2";

        StandardResourcePeriod parentSrp = srp(
                OntologyIds.srpId(resourceId, parent.getSequenceNr()), resourceId, parent.getId(), 0, 0);
        StandardResourcePeriod s1Srp = srp(
                OntologyIds.srpId(resourceId, s1.getSequenceNr()), resourceId, s1.getId(), 100, 40);
        StandardResourcePeriod s2Srp = srp(
                OntologyIds.srpId(resourceId, s2.getSequenceNr()), resourceId, s2.getId(), 100, 20);

        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(periods)
                .standardResourcePeriod(parentSrp)
                .standardResourcePeriod(s1Srp)
                .standardResourcePeriod(s2Srp)
                .build();

        StandardResourcePeriodRollup.rollupParentReserved(graph);
        assertEquals(60, parentSrp.getReservedCapacity());
    }

    private static StandardResourcePeriod srp(
            String id, String resourceId, String periodId, double total, double reserved) {
        StandardResourcePeriod srp = new StandardResourcePeriod(id, resourceId, periodId);
        srp.setTotalCapacity(total);
        srp.setReservedCapacity(reserved);
        srp.recalculateCapacityFields();
        return srp;
    }
}
