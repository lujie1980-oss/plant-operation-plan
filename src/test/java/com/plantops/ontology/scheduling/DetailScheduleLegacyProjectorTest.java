package com.plantops.ontology.scheduling;

import com.plantops.api.dto.DetailScheduleOperationDto;
import com.plantops.testsupport.SpecRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpecRef("AC-SCH-P0-01")
class DetailScheduleLegacyProjectorTest {

    @Test
    void projectsOperationScheduleAndCapacityAssignmentFromLegacyDto() {
        LocalDate anchor = LocalDate.of(2026, 6, 12);
        var op = new DetailScheduleOperationDto(
                "OP-WO-1-1",
                "WO-1",
                "LINE-A",
                "RES-A",
                2,
                120,
                240,
                "P001",
                true,
                "BATCH-1",
                1,
                "冲压",
                15);
        DetailScheduleOntologyView view =
                DetailScheduleLegacyProjector.project("DS-TEST-01", anchor, List.of(op));

        assertEquals("DS-TEST-01", view.detailScheduleVersionId());
        assertEquals(anchor, view.planningAnchorDate());
        assertEquals(1, view.operationSchedules().size());
        assertEquals(1, view.capacityAssignments().size());

        OperationSchedule schedule = view.operationSchedules().get(0);
        assertEquals("OPS-SCH-DS-TEST-01-OP-WO-1-1", schedule.getId());
        assertEquals("WO-1", schedule.getWorkOrderNo());
        assertEquals("LINE-A", schedule.getPhysicalResourceId());
        assertEquals("RES-A", schedule.getStandardResourceId());
        assertEquals(120, schedule.getDurationMinutes());
        assertEquals(LocalDateTime.of(2026, 6, 12, 2, 0), schedule.getPlannedStartTs());
        assertTrue(schedule.isPinned());

        PhysicalResourceCapacityAssignmentSchedule rca = view.capacityAssignments().get(0);
        assertEquals("RCAS-OP-WO-1-1-LINE-A", rca.getId());
        assertEquals(schedule.getId(), rca.getOperationScheduleId());
        assertEquals(120, rca.getAssignedMinutes());
        assertEquals(LocalDate.of(2026, 6, 12), rca.getSlotDate());
        assertTrue(rca.isLocked());
    }

    @Test
    void emptyOperationsYieldEmptyView() {
        DetailScheduleOntologyView view =
                DetailScheduleLegacyProjector.project("DS-EMPTY", LocalDate.now(), List.of());
        assertTrue(view.operationSchedules().isEmpty());
        assertTrue(view.capacityAssignments().isEmpty());
    }

    @Test
    void stableIdHelpers() {
        assertEquals(
                "OPS-SCH-DS-1-OP-A",
                DetailScheduleLegacyProjector.operationScheduleId("DS-1", "OP-A"));
        assertEquals(
                "RCAS-OP-A-LINE-1",
                DetailScheduleLegacyProjector.capacityAssignmentScheduleId("OP-A", "LINE-1"));
    }
}
