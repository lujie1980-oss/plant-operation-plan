package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.PhysicalResource;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.persistence.entity.ScheduleFeedbackScope;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhysicalResourceSchedulerFeedbackApplierTest {

    @Test
    void frozenFeedbackReducesPrpAvailableAndRollsUpToSrp() {
        String srId = "SR-FB";
        String lineA = "LINE-FB-A";
        String lineB = "LINE-FB-B";
        LocalDate day = LocalDate.of(2026, 6, 1);
        Period period = dayPeriod(day, 0);
        PeriodIndex periodIndex = PeriodIndex.of(List.of(period));

        PhysicalResourceRegistry registry = testRegistry(srId, lineA, lineB);
        Map<String, PhysicalResourcePeriod> prpByKey = new LinkedHashMap<>();
        PhysicalResourcePeriod prpA = prp(lineA, srId, period.getId(), 480, 0);
        PhysicalResourcePeriod prpB = prp(lineB, srId, period.getId(), 360, 0);
        prpByKey.put(prpA.getId(), prpA);
        prpByKey.put(prpB.getId(), prpB);

        ScheduleFeedbackEntity fb = new ScheduleFeedbackEntity();
        fb.slotDate = day;
        fb.durationMinutes = 120;
        fb.scope = ScheduleFeedbackScope.FROZEN.name();
        fb.resourceId = srId;
        fb.physicalResourceId = lineA;

        PhysicalResourceSchedulerFeedbackApplier.apply(
                prpByKey, registry, List.of(period), periodIndex, List.of(fb));
        prpByKey.values().forEach(PhysicalResourcePeriod::recalculateCapacityFields);

        assertEquals(120, prpA.getSchedulerFeedbackMinutes(), 1e-9);
        assertEquals(360, prpA.getAvailableCapacity(), 1e-9);
        assertEquals(360, prpB.getAvailableCapacity(), 1e-9);

        OntologyGraph.Builder builder = OntologyGraph.builder();
        StandardResourcePeriodAggregator.aggregate(
                builder, prpByKey, Set.of(srId), List.of(period));
        StandardResourcePeriod srp = builder.build().srp(OntologyIds.srpId(srId, 0));
        assertEquals(720, srp.getTotalCapacity(), 1e-9);
        assertEquals(720, srp.getAvailableCapacity(), 1e-9);
    }

    @Test
    void srKeyedFeedbackSplitsAcrossPhysicalResources() {
        String srId = "SR-SPLIT";
        String lineA = "LINE-SPLIT-A";
        String lineB = "LINE-SPLIT-B";
        LocalDate day = LocalDate.of(2026, 6, 2);
        Period period = dayPeriod(day, 0);
        PeriodIndex periodIndex = PeriodIndex.of(List.of(period));

        PhysicalResourceRegistry registry = testRegistry(srId, lineA, lineB);
        Map<String, PhysicalResourcePeriod> prpByKey = new LinkedHashMap<>();
        PhysicalResourcePeriod prpA = prp(lineA, srId, period.getId(), 400, 0);
        PhysicalResourcePeriod prpB = prp(lineB, srId, period.getId(), 400, 0);
        prpByKey.put(prpA.getId(), prpA);
        prpByKey.put(prpB.getId(), prpB);

        ScheduleFeedbackEntity fb = new ScheduleFeedbackEntity();
        fb.slotDate = day;
        fb.durationMinutes = 101;
        fb.scope = ScheduleFeedbackScope.FROZEN.name();
        fb.resourceId = srId;

        PhysicalResourceSchedulerFeedbackApplier.apply(
                prpByKey, registry, List.of(period), periodIndex, List.of(fb));
        prpByKey.values().forEach(PhysicalResourcePeriod::recalculateCapacityFields);

        assertEquals(51, prpA.getSchedulerFeedbackMinutes(), 1e-9);
        assertEquals(50, prpB.getSchedulerFeedbackMinutes(), 1e-9);
    }

    private static PhysicalResourceRegistry testRegistry(String srId, String lineA, String lineB) {
        return PhysicalResourceRegistry.forPhysicalResources(
                List.of(
                        new PhysicalResource(lineA, srId),
                        new PhysicalResource(lineB, srId)),
                Set.of(srId));
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

    private static Period dayPeriod(LocalDate day, int sequenceNr) {
        Period period = new Period(OntologyIds.periodId(sequenceNr), sequenceNr, day, day);
        period.setGranularity(PeriodGranularity.DAY);
        period.setLeaf(true);
        return period;
    }
}
