package com.plantops.scenario;

import com.plantops.api.dto.PipelineResultDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MasterPlanPlanningPreviewServiceTest {

    @Inject
    PlanningOrchestrator orchestrator;

    @Inject
    MasterPlanService masterPlanService;

    @Test
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void prepareWorkOrdersViaPipeline() throws Exception {
        PipelineResultDto pipeline = orchestrator.runFullPipeline();
        assertNotNull(pipeline.masterPlan());
    }

    @Test
    @Order(2)
    void previewRejectsNullRequest() {
        assertThrows(BadRequestException.class, () -> masterPlanService.previewPlanning(null));
    }

    @Test
    @Order(3)
    void previewDiagnosticsOnlyReturnsUnscheduledCandidates() throws Exception {
        MasterPlanPlanningPreviewDto preview = masterPlanService.previewPlanning(
                new MasterPlanPlanningPreviewRequest(null, false, false, null));

        assertFalse(preview.solved());
        assertFalse(preview.persisted());
        assertNotNull(preview.diagnostics());
        assertTrue(preview.allocationCount() > 0);
        assertEquals(0, preview.scheduledAllocationCount());
        assertNull(preview.planVersionId());
    }

    @Test
    @Order(4)
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void previewMemorySolveReturnsScoreAndSlots() throws Exception {
        MasterPlanPlanningPreviewDto preview = masterPlanService.previewPlanning(
                new MasterPlanPlanningPreviewRequest(null, true, false, null));

        assertTrue(preview.solved());
        assertFalse(preview.persisted());
        assertNotNull(preview.score());
        assertTrue(preview.scheduledAllocationCount() > 0);
        assertNull(preview.planVersionId());
    }
}
