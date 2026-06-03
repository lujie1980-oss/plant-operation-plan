package com.plantops.scenario;

import com.plantops.api.dto.PipelineResultDto;
import com.plantops.api.dto.WorkOrderDispatchRequestDto;
import com.plantops.api.dto.execution.ConfirmScheduleSessionResultDto;
import com.plantops.api.dto.execution.CreateScheduleSessionRequest;
import com.plantops.api.dto.execution.ScheduleSessionSimulateResultDto;
import com.plantops.api.dto.planning.SimulateScheduleSessionRequest;
import com.plantops.api.dto.execution.ProductionTaskDto;
import com.plantops.api.dto.execution.ScheduleSessionDto;
import com.plantops.persistence.entity.ProductionTaskEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.execution.StepExecutionState;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DetailScheduleSessionServiceTest {

    @Inject
    PlanningOrchestrator orchestrator;

    @Inject
    DetailScheduleSessionService sessionService;

    @Inject
    com.plantops.scenario.execution.ProductionTaskService productionTaskService;

    @Inject
    WorkOrderService workOrderService;

    private String masterPlanVersionId;
    private String sessionId;
    private String releasedStepId;

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
    void createSessionRejectsMissingMasterPlan() {
        assertThrows(BadRequestException.class, () -> sessionService.create(
                new CreateScheduleSessionRequest(null, true, false)));
    }

    @Test
    @Order(3)
    void createSessionWithSeedQueues() throws Exception {
        ScheduleSessionDto session = sessionService.create(
                new CreateScheduleSessionRequest(masterPlanVersionId, true, false));
        assertNotNull(session.sessionId());
        assertEquals(masterPlanVersionId, session.masterPlanVersionId());
        assertNotNull(session.preview());
        sessionId = session.sessionId();
        if (session.preview().scheduledOperationCount() > 0) {
            releasedStepId = session.preview().operations().stream()
                    .filter(op -> op.scheduled() && op.operationId() != null)
                    .findFirst()
                    .map(op -> op.operationId())
                    .orElse(null);
        }
    }

    @Test
    @Order(4)
    void simulateSessionRecalculatesWithoutTimefold() {
        ScheduleSessionSimulateResultDto result = sessionService.simulate(
                sessionId, new SimulateScheduleSessionRequest(null, null, false));
        assertNotNull(result.session());
        assertTrue(result.simulationDurationMs() >= 0);
        assertNotNull(result.session().preview().simulationMode());
    }

    @Test
    @Order(5)
    void confirmSessionPublishesPlanAndTasks() {
        ConfirmScheduleSessionResultDto result = sessionService.confirm(sessionId);
        assertNotNull(result.planVersionId());
        assertTrue(result.planVersionId().startsWith("DS-"));
        if (releasedStepId != null) {
            assertTrue(result.releasedCount() >= 1);
            ProductionTaskEntity task = ProductionTaskEntity.findByStepId(releasedStepId);
            assertNotNull(task);
            assertEquals(StepExecutionState.RELEASED.name(), task.executionState);
            assertEquals(result.planVersionId(), task.planVersionId);
            assertNotNull(task.plannedStartTs);
            assertNotNull(task.plannedEndTs);
        }
    }

    @Test
    @Order(6)
    void runningTaskDoesNotOverwriteOnReconfirm() throws Exception {
        if (releasedStepId == null) {
            return;
        }
        productionTaskService.start(releasedStepId);
        ScheduleSessionDto session = sessionService.create(
                new CreateScheduleSessionRequest(masterPlanVersionId, true, false));
        ConfirmScheduleSessionResultDto result = sessionService.confirm(session.sessionId());
        ProductionTaskEntity task = ProductionTaskEntity.findByStepId(releasedStepId);
        assertEquals(StepExecutionState.RUNNING.name(), task.executionState);
        ProductionTaskDto dto = productionTaskService.get(releasedStepId);
        assertNotNull(dto.actualStartTs());
    }
}
