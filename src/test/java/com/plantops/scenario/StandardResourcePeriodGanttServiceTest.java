package com.plantops.scenario;

import com.plantops.api.dto.SrpCapacityGanttDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.StandardResourcePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardResourcePeriodGanttServiceTest {

    @Test
    void projectsSrpToDailyCellsWithUtilization() {
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

        SrpCapacityGanttDto dto = StandardResourcePeriodGanttService.project(graph, 110);

        assertEquals(start, dto.horizonStart());
        assertEquals(start, dto.horizonEnd());
        assertEquals(1, dto.cells().size());
        assertEquals("RES-A", dto.cells().get(0).resourceId());
        assertEquals(50, dto.cells().get(0).utilizationPct());
        assertTrue(dto.cells().get(0).reservedMinutes() > 0);
    }
}
