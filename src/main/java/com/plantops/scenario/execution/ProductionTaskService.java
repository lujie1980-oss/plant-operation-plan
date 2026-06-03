package com.plantops.scenario.execution;

import com.plantops.api.dto.execution.ConfirmScheduleSessionResultDto;
import com.plantops.api.dto.execution.PlanningConflictDto;
import com.plantops.api.dto.execution.ProductionTaskDto;
import com.plantops.persistence.entity.PlanningConflictEntity;
import com.plantops.persistence.entity.ProductionTaskEntity;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import com.plantops.solver.detailschedule.ScheduleTimingUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class ProductionTaskService {

    public static final String CONFLICT_RUNNING_SCHEDULE_MISMATCH = "RUNNING_SCHEDULE_MISMATCH";

    public List<ProductionTaskDto> listAll() {
        return ProductionTaskEntity.listAllOrdered().stream().map(this::toDto).toList();
    }

    public List<ProductionTaskDto> listByState(String executionState) {
        return ProductionTaskEntity.listByState(executionState).stream().map(this::toDto).toList();
    }

    public ProductionTaskDto get(String stepId) {
        ProductionTaskEntity entity = ProductionTaskEntity.findByStepId(stepId);
        if (entity == null) {
            throw new NotFoundException("Production task not found: " + stepId);
        }
        return toDto(entity);
    }

    @Transactional
    public ProductionTaskDto start(String stepId) {
        ProductionTaskEntity task = requireTask(stepId);
        StepExecutionState state = StepExecutionState.parse(task.executionState);
        if (state != StepExecutionState.RELEASED) {
            throw new BadRequestException("Task must be RELEASED to start: " + stepId);
        }
        task.executionState = StepExecutionState.RUNNING.name();
        task.actualStartTs = LocalDateTime.now();
        task.updatedTs = LocalDateTime.now();
        return toDto(task);
    }

    @Transactional
    public ProductionTaskDto complete(String stepId) {
        ProductionTaskEntity task = requireTask(stepId);
        StepExecutionState state = StepExecutionState.parse(task.executionState);
        if (state != StepExecutionState.RUNNING) {
            throw new BadRequestException("Task must be RUNNING to complete: " + stepId);
        }
        task.executionState = StepExecutionState.COMPLETED.name();
        task.actualEndTs = LocalDateTime.now();
        task.updatedTs = LocalDateTime.now();
        return toDto(task);
    }

    /**
     * 确认发布：对已排产 step 写入/更新 RELEASED；RUNNING 不覆盖 planned；不一致则记 conflict。
     */
    @Transactional
    public ConfirmScheduleSessionResultDto releaseFromSchedule(
            LocalDate planningAnchor,
            String planVersionId,
            List<OperationAssignment> scheduledOps) {
        LocalDateTime now = LocalDateTime.now();
        int releasedCount = 0;
        List<PlanningConflictDto> conflicts = new ArrayList<>();

        for (OperationAssignment op : scheduledOps) {
            if (op.getLine() == null || op.getStartMinute() == null) {
                continue;
            }
            PlannedStep planned = toPlannedStep(planningAnchor, op);
            ProductionTaskEntity existing = ProductionTaskEntity.findByStepId(planned.stepId());
            StepExecutionState state = existing != null
                    ? StepExecutionState.parse(existing.executionState)
                    : StepExecutionState.UNPLANNED;

            if (state == StepExecutionState.COMPLETED || state == StepExecutionState.ARCHIVED) {
                continue;
            }

            if (state == StepExecutionState.RUNNING) {
                if (existing != null && scheduleDiffers(existing, planned)) {
                    conflicts.add(recordConflict(planVersionId, planned));
                }
                continue;
            }

            ProductionTaskEntity task = existing != null ? existing : new ProductionTaskEntity();
            if (existing == null) {
                task.stepId = planned.stepId();
                task.stampWorkspace();
            }
            applyPlannedFields(task, planned, planVersionId, now);
            task.executionState = StepExecutionState.RELEASED.name();
            task.releasedTs = now;
            task.updatedTs = now;
            if (existing == null) {
                task.persist();
            }
            releasedCount++;
        }

        return new ConfirmScheduleSessionResultDto(planVersionId, releasedCount, conflicts);
    }

    private static ProductionTaskEntity requireTask(String stepId) {
        ProductionTaskEntity task = ProductionTaskEntity.findByStepId(stepId);
        if (task == null) {
            throw new NotFoundException("Production task not found: " + stepId);
        }
        return task;
    }

    private PlanningConflictDto recordConflict(String planVersionId, PlannedStep planned) {
        PlanningConflictEntity row = new PlanningConflictEntity();
        row.conflictId = "PC-" + UUID.randomUUID().toString().substring(0, 8);
        row.stepId = planned.stepId();
        row.planVersionId = planVersionId;
        row.reasonCode = CONFLICT_RUNNING_SCHEDULE_MISMATCH;
        row.message = "RUNNING step schedule differs from published version "
                + planVersionId
                + " (line="
                + planned.lineId()
                + ", start="
                + planned.plannedStartTs()
                + ", end="
                + planned.plannedEndTs()
                + ")";
        row.detectedTs = LocalDateTime.now();
        row.resolved = false;
        row.stampWorkspace();
        row.persist();
        return toConflictDto(row);
    }

    private static boolean scheduleDiffers(ProductionTaskEntity existing, PlannedStep planned) {
        return !Objects.equals(existing.lineId, planned.lineId())
                || !Objects.equals(existing.plannedStartTs, planned.plannedStartTs())
                || !Objects.equals(existing.plannedEndTs, planned.plannedEndTs());
    }

    private static void applyPlannedFields(
            ProductionTaskEntity task,
            PlannedStep planned,
            String planVersionId,
            LocalDateTime now) {
        task.batchNo = planned.batchNo();
        task.workOrderNo = planned.workOrderNo();
        task.operationSeq = planned.operationSeq();
        task.operationName = planned.operationName();
        task.productCode = planned.productCode();
        task.lineId = planned.lineId();
        task.resourceId = planned.resourceId();
        task.quantity = planned.quantity();
        task.plannedStartTs = planned.plannedStartTs();
        task.plannedEndTs = planned.plannedEndTs();
        task.planVersionId = planVersionId;
        task.updatedTs = now;
    }

    private static PlannedStep toPlannedStep(LocalDate planningAnchor, OperationAssignment op) {
        ScheduleLine line = op.getLine();
        int startMinute = op.getStartMinute();
        int endMinute = op.getEndMinute() != null ? op.getEndMinute() : startMinute + op.getDurationMinutes();
        LocalDateTime startTs = ScheduleTimingUtil.startDateTime(planningAnchor, startMinute);
        LocalDateTime endTs = ScheduleTimingUtil.completionDateTime(planningAnchor, startMinute, endMinute - startMinute);
        String resourceId = op.getResourceId();
        if (resourceId == null || resourceId.isBlank()) {
            resourceId = line.getResourceId() != null ? line.getResourceId() : line.getLineId();
        }
        return new PlannedStep(
                op.getOperationId(),
                op.getBatchNo(),
                op.getWorkOrderNo(),
                op.getOperationSeq(),
                op.getOperationName(),
                op.getProductCode(),
                line.getLineId(),
                resourceId,
                op.getBatchQuantity(),
                startTs,
                endTs);
    }

    private ProductionTaskDto toDto(ProductionTaskEntity entity) {
        return new ProductionTaskDto(
                entity.stepId,
                entity.batchNo,
                entity.workOrderNo,
                entity.operationSeq,
                entity.operationName,
                entity.productCode,
                entity.lineId,
                entity.resourceId,
                entity.quantity,
                entity.plannedStartTs,
                entity.plannedEndTs,
                entity.planVersionId,
                entity.executionState,
                entity.releasedTs,
                entity.actualStartTs,
                entity.actualEndTs,
                entity.updatedTs);
    }

    private static PlanningConflictDto toConflictDto(PlanningConflictEntity entity) {
        return new PlanningConflictDto(
                entity.conflictId,
                entity.stepId,
                entity.planVersionId,
                entity.reasonCode,
                entity.message,
                entity.detectedTs,
                entity.resolved);
    }

    private record PlannedStep(
            String stepId,
            String batchNo,
            String workOrderNo,
            int operationSeq,
            String operationName,
            String productCode,
            String lineId,
            String resourceId,
            java.math.BigDecimal quantity,
            LocalDateTime plannedStartTs,
            LocalDateTime plannedEndTs) {
    }
}
