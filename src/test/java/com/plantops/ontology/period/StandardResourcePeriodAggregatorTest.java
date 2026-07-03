package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardResourcePeriodAggregatorTest {

    @Test
    void srpTotalCapacityEqualsSumOfPrpAvailableForTwoPhysicalResources() {
        String srId = "SR-MULTI-PR";
        String pr1 = "LINE-A";
        String pr2 = "LINE-B";
        String periodId = OntologyIds.periodId(0);

        Map<String, PhysicalResourcePeriod> prpByKey = new LinkedHashMap<>();
        prpByKey.put(OntologyIds.prpId(pr1, periodId), prp(pr1, srId, periodId, 480, 0));
        prpByKey.put(OntologyIds.prpId(pr2, periodId), prp(pr2, srId, periodId, 360, 60));
        prpByKey.values().forEach(PhysicalResourcePeriod::recalculateCapacityFields);

        OntologyGraph.Builder builder = OntologyGraph.builder();
        StandardResourcePeriodAggregator.aggregate(
                builder, prpByKey, Set.of(srId), List.of(period(periodId)));

        StandardResourcePeriod srp = builder.build().srp(OntologyIds.srpId(srId, 0));
        assertEquals(780, srp.getTotalCapacity(), 1e-9);
        assertEquals(780, srp.getAvailableCapacity(), 1e-9);
    }

    private static PhysicalResourcePeriod prp(
            String physicalResourceId, String standardResourceId, String periodId,
            double total, double downtime) {
        PhysicalResourcePeriod prp = new PhysicalResourcePeriod(
                OntologyIds.prpId(physicalResourceId, periodId),
                physicalResourceId,
                standardResourceId,
                periodId);
        prp.setTotalCapacity(total);
        prp.setCalendarDowntime(downtime);
        return prp;
    }

    private static Period period(String periodId) {
        Period period = new Period(periodId, 0, java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2026, 6, 1));
        period.setGranularity(PeriodGranularity.DAY);
        period.setLeaf(true);
        return period;
    }
}
