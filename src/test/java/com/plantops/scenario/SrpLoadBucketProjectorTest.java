package com.plantops.scenario;

import com.plantops.api.dto.CapacityBucketWorkOrderDto;
import com.plantops.api.dto.LoadBucketDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
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
        StandardResourcePeriod srp = new StandardResourcePeriod(
                OntologyIds.srpId("RES-A", 0),
                "RES-A",
                period.getId());
        srp.setTotalCapacity(480);
        srp.setReservedCapacity(240);
        srp.recalculateCapacityFields();

        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(List.of(period))
                .standardResourcePeriod(srp)
                .build();

        SrpLoadBucketProjector projector = new SrpLoadBucketProjector();
        projector.workOrderResolver = new CapacityBucketWorkOrderResolver() {
            @Override
            public List<CapacityBucketWorkOrderDto> resolve(
                    String resourceId, LocalDate date, String shiftId, String masterPlanVersionId) {
                return List.of();
            }
        };
        projector.timeslotHorizonService = new TimeslotHorizonService();
        projector.scheduleFeedbackService = new ScheduleFeedbackService() {
            @Override
            public int frozenMinutesForCapacityBucket(
                    String resourceId, TimeslotHorizonService.BucketKey key) {
                return 0;
            }
        };

        SrpLoadBucketProjector.SrpLoadBucketResult result = projector.project(graph, "MP-TEST", 110);

        assertEquals(start, result.horizonStart());
        assertEquals(start, result.horizonEnd());
        assertEquals(1, result.buckets().size());
        LoadBucketDto bucket = result.buckets().get(0);
        assertEquals("RES-A", bucket.resourceId());
        assertEquals(start, bucket.date());
        assertEquals(480, bucket.availableMinutes());
        assertEquals(240, bucket.demandMinutes());
        assertEquals(50, bucket.utilizationPct());
        assertTrue(result.openings().isEmpty());
    }
}
