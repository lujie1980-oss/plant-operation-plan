package com.plantops.scenario.planning;

import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.DetailScheduleService;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已持久化详细排程恢复 {@link DetailSchedule}，供 {@code SolutionManager.explain()} 使用。
 */
@ApplicationScoped
public class DetailScheduleSolutionRestorer {

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    DetailScheduleProblemMapper problemMapper;

    @Inject
    SampleDataLoader sampleDataLoader;

    public DetailSchedule restore(String detailScheduleVersionId, String masterPlanVersionId) {
        if (masterPlanVersionId == null || masterPlanVersionId.isBlank()) {
            throw new BadRequestException("masterPlanVersionId is required for detail schedule score explanation");
        }
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(detailScheduleVersionId);
        if (version == null || !"DETAIL_SCHEDULE".equals(version.planType)) {
            throw new NotFoundException("Detail schedule version not found: " + detailScheduleVersionId);
        }
        PlanVersionEntity master = PlanVersionEntity.findByVersionId(masterPlanVersionId);
        if (master == null || !"MASTER_PLAN".equals(master.planType)) {
            throw new NotFoundException("Master plan version not found: " + masterPlanVersionId);
        }
        sampleDataLoader.extendCalendarsToHorizon();
        DetailSchedulePlanningContext context = detailScheduleService.buildPlanningContext(masterPlanVersionId);
        DetailSchedule schedule = problemMapper.toSchedule(context);
        applyPersistedAssignments(schedule, detailScheduleVersionId);
        return schedule;
    }

    static void applyPersistedAssignments(DetailSchedule schedule, String planVersionId) {
        List<DetailScheduleOperationEntity> rows = DetailScheduleOperationEntity
                .find("planVersionId = ?1", planVersionId)
                .list();
        Map<String, DetailScheduleOperationEntity> byOperationId = new HashMap<>();
        for (DetailScheduleOperationEntity row : rows) {
            if (row.operationId != null) {
                byOperationId.put(row.operationId, row);
            }
        }
        Map<String, ScheduleLine> lineById = new HashMap<>();
        for (ScheduleLine line : schedule.getLineRange()) {
            lineById.put(line.getLineId(), line);
        }
        for (OperationAssignment op : schedule.getOperations()) {
            DetailScheduleOperationEntity row = byOperationId.get(op.getOperationId());
            if (row == null || row.lineId == null) {
                continue;
            }
            ScheduleLine line = lineById.get(row.lineId);
            if (line != null) {
                op.setLine(line);
            }
            op.setStartMinute(row.startMinute);
        }
    }
}
