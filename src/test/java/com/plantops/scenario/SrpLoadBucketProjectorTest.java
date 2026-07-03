package com.plantops.scenario;

import com.plantops.api.dto.CapacityBucketWorkOrderDto;
import com.plantops.api.dto.LoadBucketDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.period.StandardResourcePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrpLoadBucketProjectorTest {

    @Test
    void projectsSingleDaySrpToLoadBucketWithUtilization() {
        LocalDate start = LocalDate.of(2026, 6, 12);
        Period period = new Period(OntologyIds.periodId(0), 0, start, start);
        StandardResourcePeriod srp = srp("RES-A", 0, period, 480, 240);

        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(List.of(period))
                .standardResourcePeriod(srp)
                .build();

        SrpLoadBucketProjector.SrpLoadBucketResult result = project(graph);

        assertEquals(start, result.horizonStart());
        assertEquals(start, result.horizonEnd());
        assertEquals(1, result.leafSrpCount());
        assertEquals(0, result.overloadedLeafSrpCount());
        assertEquals(1, result.buckets().size());
        LoadBucketDto bucket = result.buckets().get(0);
        assertEquals("RES-A", bucket.resourceId());
        assertEquals(start, bucket.date());
        assertEquals("DAY", bucket.shiftId());
        assertEquals(480, bucket.availableMinutes());
        assertEquals(240, bucket.demandMinutes());
        assertEquals(50, bucket.utilizationPct());
        assertTrue(result.openings().isEmpty());
    }

    @Test
    void projectsLeafShiftSrpsAndSkipsParentDayBucket() {
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(LocalDate.of(2026, 6, 5));
        Period parent = periods.get(0);
        Period s1 = periods.get(1);
        Period s2 = periods.get(2);

        StandardResourcePeriod parentSrp = srp("RES-SHIFT", parent.getSequenceNr(), parent, 0, 0);
        StandardResourcePeriod s1Srp = srp("RES-SHIFT", s1.getSequenceNr(), s1, 480, 120);
        StandardResourcePeriod s2Srp = srp("RES-SHIFT", s2.getSequenceNr(), s2, 360, 370);

        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(periods)
                .standardResourcePeriod(parentSrp)
                .standardResourcePeriod(s1Srp)
                .standardResourcePeriod(s2Srp)
                .build();

        SrpLoadBucketProjector.SrpLoadBucketResult result = project(graph);

        assertEquals(2, result.leafSrpCount());
        assertEquals(1, result.overloadedLeafSrpCount());
        assertEquals(2, result.buckets().size());
        assertEquals("S1", result.buckets().get(0).shiftId());
        assertEquals("S2", result.buckets().get(1).shiftId());
        assertTrue(!result.buckets().get(1).overloaded());
    }

    private static StandardResourcePeriod srp(
            String resourceId, int seq, Period period, double total, double reserved) {
        StandardResourcePeriod srp = new StandardResourcePeriod(
                OntologyIds.srpId(resourceId, seq), resourceId, period.getId());
        srp.setTotalCapacity(total);
        srp.setReservedCapacity(reserved);
        srp.recalculateCapacityFields();
        return srp;
    }

    private static SrpLoadBucketProjector.SrpLoadBucketResult project(OntologyGraph graph) {
        SrpLoadBucketProjector projector = new SrpLoadBucketProjector();
        projector.workOrderResolver = new CapacityBucketWorkOrderResolver() {
            @Override
            public List<CapacityBucketWorkOrderDto> resolve(
                    String resourceId, LocalDate date, String shiftId, String masterPlanVersionId) {
                return List.of();
            }
        };
        projector.scheduleFeedbackService = new ScheduleFeedbackService() {
            @Override
            public int frozenMinutesForCapacityBucket(
                    String resourceId, TimeslotHorizonService.BucketKey key) {
                return 0;
            }
        };
        return projector.project(graph, "MP-TEST", 110);
    }
}
