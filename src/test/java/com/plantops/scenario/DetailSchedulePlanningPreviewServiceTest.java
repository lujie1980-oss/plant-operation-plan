package com.plantops.scenario;

import com.plantops.api.dto.PipelineResultDto;
import com.plantops.api.dto.WorkOrderDispatchRequestDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewRequest;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DetailScheduleService#previewPlanning} 四种模式契约（共享 Context 反写）。
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DetailSchedulePlanningPreviewServiceTest {

    @Inject
    PlanningOrchestrator orchestrator;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    WorkOrderService workOrderService;

    private String masterPlanVersionId;

    @Test
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void prepareMasterPlanAndDispatchedWorkOrders() throws Exception {
        PipelineResultDto pipeline = orchestrator.runFullPipeline();
        masterPlanVersionId = pipeline.masterPlan().planVersionId();

        List<String> workOrderNos = WorkOrderEntity.listAllOrdered().stream()
                .map(wo -> wo.workOrderNo)
                .toList();
        if (!workOrderNos.isEmpty()) {
            workOrderService.dispatchForScheduling(new WorkOrderDispatchRequestDto(workOrderNos));
        }
    }

    @Test
    @Order(2)
    void previewRejectsNullRequest() {
        assertThrows(BadRequestException.class, () -> detailScheduleService.previewPlanning(null));
    }

    @Test
    @Order(3)
    void previewRejectsPersistWithoutSolve() {
        assertThrows(
                BadRequestException.class,
                () -> detailScheduleService.previewPlanning(new DetailSchedulePlanningPreviewRequest(
                        masterPlanVersionId, false, true, false, null, false)));
    }

    @Test
    @Order(4)
    void previewDiagnosticsOnlyReturnsUnscheduledCandidates() throws Exception {
        DetailSchedulePlanningPreviewDto preview = detailScheduleService.previewPlanning(
                new DetailSchedulePlanningPreviewRequest(masterPlanVersionId, false, false, false, null, false));

        assertEquals(masterPlanVersionId, preview.masterPlanVersionId());
        assertFalse(preview.solved());
        assertFalse(preview.persisted());
        assertFalse(preview.initialQueuesSeeded());
        assertNotNull(preview.diagnostics());
        assertFalse(preview.lines().isEmpty());
        assertTrue(preview.operationCount() > 0);
        assertEquals(0, preview.scheduledOperationCount());
        assertNull(preview.planVersionId());
        assertNull(preview.score());
    }

    @Test
    @Order(5)
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void previewSeedInitialQueuesAssignsLineAndTime() throws Exception {
        DetailSchedulePlanningPreviewDto preview = detailScheduleService.previewPlanning(
                new DetailSchedulePlanningPreviewRequest(masterPlanVersionId, false, false, false, null, true));

        assertFalse(preview.solved());
        assertTrue(preview.initialQueuesSeeded());
        assertTrue(preview.scheduledOperationCount() > 0);
        assertTrue(preview.operations().stream().anyMatch(op -> op.scheduled() && op.lineId() != null));
        assertTrue(preview.operations().stream()
                .anyMatch(op -> op.scheduled() && op.startMinute() != null && op.endMinute() != null));
    }

    @Test
    @Order(6)
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void previewMemorySolveReturnsScoreAndSchedule() throws Exception {
        DetailSchedulePlanningPreviewDto preview = detailScheduleService.previewPlanning(
                new DetailSchedulePlanningPreviewRequest(masterPlanVersionId, true, false, false, null, false));

        assertTrue(preview.solved());
        assertFalse(preview.persisted());
        assertFalse(preview.initialQueuesSeeded());
        assertNotNull(preview.score());
        assertNotNull(preview.solveDurationMs());
        assertTrue(preview.scheduledOperationCount() > 0);
        assertNull(preview.planVersionId());
    }
}
