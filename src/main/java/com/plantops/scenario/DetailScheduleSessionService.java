package com.plantops.scenario;

import com.plantops.api.dto.execution.ConfirmScheduleSessionResultDto;
import com.plantops.api.dto.execution.CreateScheduleSessionRequest;
import com.plantops.api.dto.execution.ScheduleSessionDto;
import com.plantops.api.dto.execution.ScheduleSessionSimulateResultDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewDto;
import com.plantops.api.dto.planning.ScheduleConstraintViolationDto;
import com.plantops.api.dto.planning.SimulateScheduleSessionRequest;
import com.plantops.scenario.execution.ProductionTaskService;
import com.plantops.scenario.planning.DetailSchedulePlanningContext;
import com.plantops.scenario.planning.DetailScheduleProblemMapper;
import com.plantops.scenario.planning.SimulationProfileService;
import com.plantops.scenario.planning.DetailScheduleSessionMutation;
import com.plantops.scenario.planning.DetailScheduleSimulationEngine;
import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.ScheduleValidationService;
import com.plantops.scenario.planning.SchedulingSession;
import com.plantops.scenario.planning.SchedulingSessionStore;
import com.plantops.scenario.planning.simulation.SimulationMode;
import com.plantops.scenario.planning.simulation.SimulationProfileResolver;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.ValidationPipeline;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class DetailScheduleSessionService {

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    DetailScheduleProblemMapper problemMapper;

    @Inject
    SchedulingSessionStore sessionStore;

    @Inject
    ProductionTaskService productionTaskService;

    @Inject
    DetailScheduleSimulationEngine simulationEngine;

    @Inject
    ScheduleValidationService validationService;

    @Inject
    SimulationProfileService simulationProfileService;

    @Inject
    SimulationProfileResolver simulationProfileResolver;

    @Inject
    ValidationPipeline validationPipeline;

    public ScheduleSessionDto create(CreateScheduleSessionRequest request)
            throws ExecutionException, InterruptedException {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        String masterPlanVersionId = request.masterPlanVersionId();
        if (masterPlanVersionId == null || masterPlanVersionId.isBlank()) {
            throw new BadRequestException("masterPlanVersionId required");
        }
        if (request.resolveSeedInitialQueues() && request.resolveSolve()) {
            throw new BadRequestException("seedInitialQueues and solve are mutually exclusive");
        }

        DetailSchedulePlanningContext context = detailScheduleService.buildPlanningContext(masterPlanVersionId);
        DetailSchedule schedule = problemMapper.toSchedule(context);

        boolean solved = false;
        Long solveDurationMs = null;
        String score = null;

        if (request.resolveSolve()) {
            long start = System.currentTimeMillis();
            schedule = detailScheduleService.solveScheduleInMemory(schedule);
            detailScheduleService.applyTiming(schedule);
            solveDurationMs = System.currentTimeMillis() - start;
            score = schedule.score() != null ? schedule.score().toString() : null;
            solved = true;
        } else if (request.resolveSeedInitialQueues()) {
            detailScheduleService.applyTiming(schedule);
        }

        LocalDateTime createdAt = LocalDateTime.now();
        String sessionId = "SS-" + UUID.randomUUID().toString().substring(0, 12);
        var profileSnapshot = simulationProfileService.resolveSnapshot(
                masterPlanVersionId,
                request != null ? request.simulationProfileId() : null);
        SchedulingSession session = new SchedulingSession(
                sessionId,
                masterPlanVersionId,
                context.planningAnchor(),
                schedule,
                createdAt,
                sessionStore.defaultExpiresAt(createdAt),
                solved,
                solveDurationMs,
                score,
                profileSnapshot);
        sessionStore.put(session);

        DetailSchedulePlanningPreviewDto preview = detailScheduleService.toSessionPreviewDto(
                context,
                createdAt,
                solved,
                false,
                request.resolveSeedInitialQueues(),
                null,
                score,
                solveDurationMs,
                detailScheduleService.operationsFromSchedule(schedule));

        return new ScheduleSessionDto(
                sessionId,
                masterPlanVersionId,
                createdAt,
                session.expiresAt(),
                preview,
                profileSnapshot.profileId());
    }

    /** 工序在当前 Session 下可分配的产线（acceptsLine）。 */
    public List<String> candidateLines(String sessionId, String operationId) {
        SchedulingSession session = sessionStore.require(sessionId);
        OperationAssignment op = findOperation(session.schedule(), operationId);
        if (op == null) {
            throw new NotFoundException("Unknown step: " + operationId);
        }
        List<String> lineIds = new ArrayList<>();
        if (session.schedule().getLines() == null) {
            return lineIds;
        }
        for (ScheduleLine line : session.schedule().getLines()) {
            if (line.getLineId() != null && op.acceptsLine(line)) {
                lineIds.add(line.getLineId());
            }
        }
        lineIds.sort(String::compareTo);
        return lineIds;
    }

    private static OperationAssignment findOperation(DetailSchedule schedule, String operationId) {
        if (schedule.getOperations() == null || operationId == null || operationId.isBlank()) {
            return null;
        }
        for (OperationAssignment op : schedule.getOperations()) {
            if (operationId.equals(op.getOperationId())) {
                return op;
            }
        }
        return null;
    }

    public ScheduleSessionDto get(String sessionId) {
        SchedulingSession session = sessionStore.require(sessionId);
        DetailSchedulePlanningContext context =
                detailScheduleService.buildPlanningContext(session.masterPlanVersionId());
        DetailSchedulePlanningPreviewDto preview = detailScheduleService.toSessionPreviewDto(
                context,
                session.createdAt(),
                session.solved(),
                false,
                true,
                null,
                session.score(),
                session.solveDurationMs(),
                detailScheduleService.operationsFromSchedule(session.schedule()));
        return toSessionDto(session, preview);
    }

    @Transactional
    public ConfirmScheduleSessionResultDto confirm(String sessionId) {
        SchedulingSession session = sessionStore.require(sessionId);
        enforceConfirmPolicy(session);
        DetailSchedule schedule = session.schedule();
        long duration = session.solveDurationMs() != null ? session.solveDurationMs() : 0L;
        String versionId = "DS-" + UUID.randomUUID().toString().substring(0, 8);
        detailScheduleService.persistSchedule(versionId, schedule, duration);

        List<OperationAssignment> scheduledOps = schedule.getOperations().stream()
                .filter(op -> op.getLine() != null && op.getStartMinute() != null)
                .sorted(Comparator
                        .comparing((OperationAssignment op) -> op.getLine().getLineId())
                        .thenComparing(OperationAssignment::getStartMinute)
                        .thenComparing(OperationAssignment::getOperationId))
                .toList();

        ConfirmScheduleSessionResultDto result = productionTaskService.releaseFromSchedule(
                session.planningAnchor(),
                versionId,
                scheduledOps);
        sessionStore.remove(sessionId);
        return result;
    }

    public ScheduleSessionDto optimize(String sessionId) throws ExecutionException, InterruptedException {
        SchedulingSession session = sessionStore.require(sessionId);
        long start = System.currentTimeMillis();
        DetailSchedule solved = detailScheduleService.solveScheduleInMemory(session.schedule());
        detailScheduleService.applyTiming(solved);
        long duration = System.currentTimeMillis() - start;
        String score = solved.score() != null ? solved.score().toString() : null;

        SchedulingSession updated = new SchedulingSession(
                session.sessionId(),
                session.masterPlanVersionId(),
                session.planningAnchor(),
                solved,
                session.createdAt(),
                session.expiresAt(),
                true,
                duration,
                score,
                session.simulationProfile());
        sessionStore.put(updated);
        return get(sessionId);
    }

    /**
     * 增量/全量内存推演：应用手动 patch → 链式赋时 → 校验（不调用 Timefold）。
     */
    public ScheduleSessionSimulateResultDto simulate(String sessionId, SimulateScheduleSessionRequest request) {
        SchedulingSession session = sessionStore.require(sessionId);
        DetailSchedule schedule = session.schedule();

        List<String> patchTouched = List.of();
        if (request != null && request.stepPatches() != null && !request.stepPatches().isEmpty()) {
            patchTouched = DetailScheduleSessionMutation.applyPatches(schedule, request.stepPatches());
        }

        java.util.LinkedHashSet<String> seeds = new java.util.LinkedHashSet<>();
        seeds.addAll(patchTouched);
        if (request != null && request.affectedOperationIds() != null) {
            seeds.addAll(request.affectedOperationIds());
        }

        boolean fullReschedule = (request != null && request.resolveFullReschedule()) || seeds.isEmpty();
        SimulationMode mode = fullReschedule ? SimulationMode.FULL : SimulationMode.INCREMENTAL;
        SimulationRuleContext ctx = simulationProfileResolver.buildContext(
                schedule,
                mode,
                seeds,
                session.simulationProfile(),
                request != null ? request.simulationProfileId() : null,
                request != null ? request.ruleOverrides() : null);

        DetailScheduleSimulationEngine.SimulationResult simulation =
                simulationEngine.simulate(schedule, ctx, fullReschedule, seeds);

        List<ScheduleConstraintViolationDto> violationDtos = validationService.toDtos(simulation.violations());
        int hardCount = countLevel(simulation.violations(), ScheduleConstraintViolation.ViolationLevel.HARD);
        int mediumCount = countLevel(simulation.violations(), ScheduleConstraintViolation.ViolationLevel.MEDIUM);

        SchedulingSession updated = new SchedulingSession(
                session.sessionId(),
                session.masterPlanVersionId(),
                session.planningAnchor(),
                schedule,
                session.createdAt(),
                session.expiresAt(),
                session.solved(),
                session.solveDurationMs(),
                session.score(),
                session.simulationProfile());
        sessionStore.put(updated);

        ScheduleSessionDto sessionDto = buildSessionDto(updated, violationDtos, simulation);
        return new ScheduleSessionSimulateResultDto(
                sessionDto,
                simulation.mode().name(),
                simulation.durationMs(),
                simulation.recalculatedOperationIds(),
                violationDtos,
                hardCount,
                mediumCount,
                simulation.appliedRules(),
                simulation.simulationProfileId());
    }

    private void enforceConfirmPolicy(SchedulingSession session) {
        if (session.simulationProfile() == null) {
            return;
        }
        SimulationRuleContext ctx = simulationProfileResolver.buildContext(
                session.schedule(),
                SimulationMode.FULL,
                java.util.Set.of(),
                session.simulationProfile(),
                null,
                null);
        if (!ctx.profileSettings().blockConfirmOnHard()) {
            return;
        }
        var violations = validationPipeline.validate(ctx);
        boolean hasHard = violations.stream()
                .anyMatch(v -> v.level() == ScheduleConstraintViolation.ViolationLevel.HARD);
        if (hasHard) {
            throw new BadRequestException("存在 HARD 级违背，推演配置禁止确认发布");
        }
    }

    private ScheduleSessionDto buildSessionDto(
            SchedulingSession session,
            List<ScheduleConstraintViolationDto> violations,
            DetailScheduleSimulationEngine.SimulationResult simulation) {
        DetailSchedulePlanningContext context =
                detailScheduleService.buildPlanningContext(session.masterPlanVersionId());
        DetailSchedulePlanningPreviewDto preview = detailScheduleService.toSessionPreviewDto(
                context,
                LocalDateTime.now(),
                session.solved(),
                false,
                true,
                null,
                session.score(),
                session.solveDurationMs(),
                detailScheduleService.operationsFromSchedule(session.schedule()),
                violations,
                simulation.mode().name(),
                simulation.durationMs(),
                simulation.recalculatedOperationIds());
        return toSessionDto(session, preview);
    }

    private static ScheduleSessionDto toSessionDto(
            SchedulingSession session,
            DetailSchedulePlanningPreviewDto preview) {
        String profileId = session.simulationProfile() != null
                ? session.simulationProfile().profileId()
                : null;
        return new ScheduleSessionDto(
                session.sessionId(),
                session.masterPlanVersionId(),
                session.createdAt(),
                session.expiresAt(),
                preview,
                profileId);
    }

    private static int countLevel(
            List<ScheduleConstraintViolation> violations,
            ScheduleConstraintViolation.ViolationLevel level) {
        if (violations == null) {
            return 0;
        }
        return (int) violations.stream().filter(v -> v.level() == level).count();
    }
}
