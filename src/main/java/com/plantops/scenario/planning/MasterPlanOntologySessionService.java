package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.OperationSnapshotDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.PispSummaryDto;
import com.plantops.api.dto.planning.OntologySimulateTargetType;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.SrpSnapshotDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.config.OntologySessionPersistenceFeature;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.persistence.OntologyP0Overlay;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.ontology.persistence.entity.OntSessionEntity;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.rol.ChangeOperation;
import com.plantops.rol.ChangeSet;
import com.plantops.rol.RolEngine;
import com.plantops.rol.RolTransaction;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerException;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerRegistry;
import com.plantops.scenario.planning.optimizer.PlanningProblem;
import com.plantops.scenario.planning.optimizer.PlanningResultApplicator;
import com.plantops.scenario.planning.persist.OntologyStatePersister;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class MasterPlanOntologySessionService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    @Inject
    OntologyTimefoldMapper ontologyTimefoldMapper;

    @Inject
    OntologyToMasterPlanScheduleMapper ontologyToMasterPlanScheduleMapper;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    RolTransaction rolTransaction;

    @Inject
    PlanningOptimizerRegistry optimizerRegistry;

    @Inject
    PlanningResultApplicator planningResultApplicator;

    @Inject
    OntologyStatePersister ontologyStatePersister;

    @Inject
    OntologySessionPersistenceFeature sessionPersistenceFeature;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    public MasterPlanSessionDto create(CreateMasterPlanSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.planVersionId() == null || request.planVersionId().isBlank()) {
            throw new BadRequestException("planVersionId required");
        }

        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, request.planVersionId());
        RolEngine rolEngine = authoritativeOntologyGraph.newRolEngine(graph);
        LocalDateTime createdAt = LocalDateTime.now();
        String sessionId = "MOS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MasterPlanSolveProfile solveProfile = resolveSolveProfile(request.planVersionId(), graph);

        MasterPlanOntologySession session = new MasterPlanOntologySession(
                sessionId,
                WorkspaceResolver.currentWorkspaceId(),
                request.planVersionId(),
                graph,
                rolEngine,
                createdAt,
                sessionStore.defaultExpiresAt(createdAt),
                solveProfile,
                null,
                null);
        sessionStore.put(session);
        if (sessionPersistenceFeature.enabled()) {
            ontologyPersistence.createDraftSession(
                    workspaceId,
                    sessionId,
                    null,
                    graph,
                    sessionStore.defaultExpiresAt(createdAt),
                    null);
            ontologyPersistence.recordMasterPlanContext(workspaceId, sessionId, request.planVersionId());
        }
        return toSessionDto(session);
    }

    /**
     * AC-PERS-02: reload in-memory Session from persisted DRAFT revision after process restart.
     */
    public MasterPlanSessionDto restoreSessionFromPersistence(String sessionId) {
        if (!sessionPersistenceFeature.enabled()) {
            throw new BadRequestException("Session persistence disabled");
        }
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntSessionEntity row = OntSessionEntity.findSession(workspaceId, sessionId)
                .orElseThrow(() -> new NotFoundException("Master plan session not found: " + sessionId));
        String planVersionId = row.solveProfileJson != null
                ? String.valueOf(row.solveProfileJson.get(
                        com.plantops.ontology.persistence.OntologySessionPersistenceService
                                .SOLVE_PROFILE_PLAN_VERSION_KEY))
                : null;
        if (planVersionId == null || planVersionId.isBlank() || "null".equals(planVersionId)) {
            throw new BadRequestException("Persisted session missing basePlanVersionId: " + sessionId);
        }
        OntologyGraph loaderGraph = ontologyLoader.loadForPlanVersion(planVersionId);
        OntologyGraph restoredP0 = ontologyPersistence.loadDraftSession(workspaceId, sessionId);
        OntologyGraph graph = OntologyP0Overlay.apply(loaderGraph, restoredP0);
        RolEngine rolEngine = authoritativeOntologyGraph.newRolEngine(graph);
        MasterPlanOntologySession session = new MasterPlanOntologySession(
                sessionId,
                workspaceId,
                planVersionId,
                graph,
                rolEngine,
                row.createdAt,
                row.expiresAt,
                resolveSolveProfile(planVersionId, graph),
                null,
                null);
        sessionStore.put(session);
        return toSessionDto(session);
    }

    public MasterPlanSessionDto get(String sessionId) {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        return toSessionDto(session);
    }

    public List<PispSummaryDto> listPisps(String sessionId) {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        OntologyGraph graph = session.graph();
        return graph.pispsById().keySet().stream()
                .sorted()
                .map(pispId -> {
                    var pisp = graph.pisp(pispId);
                    return new PispSummaryDto(pispId, pisp != null ? pisp.getProductCode() : null);
                })
                .toList();
    }

    public List<PispPeriodSnapshotDto> listPispPeriods(String sessionId, String pispId) {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        OntologyGraph graph = session.graph();
        if (graph.pisp(pispId) == null) {
            throw new NotFoundException("PISP not found: " + pispId);
        }

        Map<String, Integer> periodSeqById = new HashMap<>();
        for (var period : graph.periodsOrdered()) {
            periodSeqById.put(period.getId(), period.getSequenceNr());
        }
        return graph.pispPeriodsById().values().stream()
                .filter(period -> pispId.equals(period.getPispId()))
                .sorted(Comparator.comparingInt(period ->
                        periodSeqById.getOrDefault(period.getPeriodId(), Integer.MAX_VALUE)))
                .map(MasterPlanOntologySessionService::toSnapshot)
                .toList();
    }

    public List<SrpSnapshotDto> listResources(String sessionId) {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        OntologyGraph graph = session.graph();
        Map<String, Integer> periodSeqById = new HashMap<>();
        for (var period : graph.periodsOrdered()) {
            periodSeqById.put(period.getId(), period.getSequenceNr());
        }
        return graph.srpById().values().stream()
                .sorted(Comparator.comparing(StandardResourcePeriod::getStandardResourceId)
                        .thenComparingInt(srp -> periodSeqById.getOrDefault(srp.getPeriodId(), Integer.MAX_VALUE)))
                .map(MasterPlanOntologySessionService::toSrpSnapshot)
                .toList();
    }

    public List<OperationSnapshotDto> listOperations(String sessionId, String supplyOrderId) {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        if (session.graph().supplyOrder(supplyOrderId) == null) {
            throw new NotFoundException("Supply order not found: " + supplyOrderId);
        }
        return session.graph().operationsForSupplyOrder(supplyOrderId).stream()
                .map(MasterPlanOntologySessionService::toOperationSnapshot)
                .toList();
    }

    public MasterPlanSessionSimulateResultDto simulate(
            String sessionId,
            SimulateMasterPlanSessionRequest request) {
        validateSimulateRequest(request);
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        return switch (request.effectiveTargetType()) {
            case PISPP -> simulatePispp(session, request);
            case SRP -> simulateSrp(session, request);
            case SUPPLY_ORDER -> simulateSupplyOrder(session, request);
        };
    }

    private static void validateSimulateRequest(SimulateMasterPlanSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.effectiveTargetId() == null || request.effectiveTargetId().isBlank()) {
            throw new BadRequestException("targetId or pispPeriodId required");
        }
        if (request.property() == null || request.property().isBlank()) {
            throw new BadRequestException("property required");
        }
        if (request.effectiveTargetType() == OntologySimulateTargetType.SUPPLY_ORDER) {
            if (!"needDate".equals(request.property())) {
                throw new BadRequestException("SUPPLY_ORDER simulate supports property needDate only");
            }
            if (request.dateValue() == null || request.dateValue().isBlank()) {
                throw new BadRequestException("dateValue required (ISO date) for needDate");
            }
            return;
        }
        if (request.value() == null) {
            throw new BadRequestException("value required");
        }
    }

    private MasterPlanSessionSimulateResultDto simulatePispp(
            MasterPlanOntologySession session,
            SimulateMasterPlanSessionRequest request) {
        OntologyGraph graph = session.graph();
        ProductInStockingPointPeriod target = graph.pispPeriod(request.effectiveTargetId());
        if (target == null) {
            throw new NotFoundException("PISPP not found: " + request.effectiveTargetId());
        }

        List<ProductInStockingPointPeriod> candidates = collectAffectedCandidates(graph, target);
        Map<String, PispPeriodSnapshotDto> before = snapshotById(candidates);

        try {
            session.rolEngine().applyPropertyChange(target, request.property(), request.value());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }

        List<String> recalculatedPeriodIds = new ArrayList<>();
        List<PispPeriodSnapshotDto> snapshots = new ArrayList<>();
        for (ProductInStockingPointPeriod period : candidates) {
            PispPeriodSnapshotDto now = toSnapshot(period);
            if (hasChanged(before.get(period.getId()), now)) {
                recalculatedPeriodIds.add(period.getId());
                snapshots.add(now);
            }
        }
        if (recalculatedPeriodIds.isEmpty()) {
            recalculatedPeriodIds.add(target.getId());
            snapshots.add(toSnapshot(target));
        }
        persistSimulateIfEnabled(session, request);
        return new MasterPlanSessionSimulateResultDto(recalculatedPeriodIds, snapshots, List.of(), List.of());
    }

    private MasterPlanSessionSimulateResultDto simulateSrp(
            MasterPlanOntologySession session,
            SimulateMasterPlanSessionRequest request) {
        OntologyGraph graph = session.graph();
        StandardResourcePeriod target = graph.srp(request.effectiveTargetId());
        if (target == null) {
            throw new NotFoundException("SRP not found: " + request.effectiveTargetId());
        }

        try {
            session.rolEngine().applyPropertyChange(target, request.property(), request.value());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }

        SrpSnapshotDto after = toSrpSnapshot(target);
        persistSimulateIfEnabled(session, request);
        return new MasterPlanSessionSimulateResultDto(
                List.of(), List.of(), List.of(after), List.of());
    }

    private MasterPlanSessionSimulateResultDto simulateSupplyOrder(
            MasterPlanOntologySession session,
            SimulateMasterPlanSessionRequest request) {
        OntologyGraph graph = session.graph();
        SupplyOrder supplyOrder = graph.supplyOrder(request.effectiveTargetId());
        if (supplyOrder == null) {
            throw new NotFoundException("Supply order not found: " + request.effectiveTargetId());
        }

        LocalDate needDate;
        try {
            needDate = LocalDate.parse(request.dateValue().trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BadRequestException("dateValue must be ISO date (yyyy-MM-dd)");
        }

        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrder.getId());
        Map<String, OperationSnapshotDto> before = new LinkedHashMap<>();
        for (Operation operation : operations) {
            before.put(operation.getId(), toOperationSnapshot(operation));
        }

        session.rolEngine().applySupplyOrderNeedDateChange(supplyOrder, needDate);

        List<OperationSnapshotDto> operationSnapshots = new ArrayList<>();
        for (Operation operation : operations) {
            OperationSnapshotDto now = toOperationSnapshot(operation);
            if (hasOperationChanged(before.get(operation.getId()), now)) {
                operationSnapshots.add(now);
            }
        }
        if (operationSnapshots.isEmpty()) {
            for (Operation operation : operations) {
                operationSnapshots.add(toOperationSnapshot(operation));
            }
        }
        persistSimulateIfEnabled(session, request);
        return new MasterPlanSessionSimulateResultDto(List.of(), List.of(), List.of(), operationSnapshots);
    }

    public MasterPlanSessionOptimizeResultDto optimize(String sessionId)
            throws ExecutionException, InterruptedException {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        return optimizeDirect(session);
    }

    public MasterPlanSessionConfirmResultDto confirm(String sessionId)
            throws ExecutionException, InterruptedException {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        OptimizerResult optimizerResult = session.lastOptimizerResult();
        if (optimizerResult == null) {
            throw new BadRequestException("Call optimize before confirm");
        }
        OntologyStatePersister.PersistOutcome outcome = ontologyStatePersister.persistSession(
                new OntologyStatePersister.SessionPersistRequest(
                        session.sessionId(),
                        session.basePlanVersionId(),
                        session.solveProfile(),
                        optimizerResult,
                        session.graph()));
        if (sessionPersistenceFeature.enabled()) {
            ontologyPersistence.promoteDraftToCommitted(
                    session.workspaceId(), session.sessionId(), outcome.planVersionId());
        }
        authoritativeOntologyGraph.invalidate(session.workspaceId(), session.basePlanVersionId());
        authoritativeOntologyGraph.invalidate(session.workspaceId(), outcome.planVersionId());
        return new MasterPlanSessionConfirmResultDto(
                session.sessionId(),
                outcome.planVersionId(),
                outcome.occupancyCount());
    }

    private MasterPlanSessionOptimizeResultDto optimizeDirect(MasterPlanOntologySession session)
            throws ExecutionException, InterruptedException {
        MasterPlanSchedule problem = ontologyToMasterPlanScheduleMapper.toSchedule(
                session.graph(), session.solveProfile());
        OptimizerResult optimizerResult;
        try {
            optimizerResult = optimizerRegistry.requireDefault().optimize(
                    PlanningProblem.forOntologySchedule(problem, session.sessionId()));
        } catch (PlanningOptimizerException ex) {
            throw new BadRequestException("Optimize failed: " + ex.getMessage());
        }
        MasterPlanSessionOptimizeResultDto response = applyAllocationsToGraph(
                session,
                optimizerResult.persistAllocations(),
                optimizerResult.scoreSummary(),
                optimizerResult.solveDurationMs());
        sessionStore.put(session.withLastOptimizerResult(optimizerResult));
        persistOptimizeIfEnabled(session, optimizerResult);
        return response;
    }

    private MasterPlanSessionOptimizeResultDto applyAllocationsToGraph(
            MasterPlanOntologySession session,
            List<MasterPlanAllocationDto> allocations,
            String score,
            long solveDurationMs) {
        PeriodIndex periodIndex = PeriodIndex.of(session.graph().periodsOrdered());
        ChangeSet previewChangeSet = ontologyTimefoldMapper.toChangeSet(allocations, session.graph(), periodIndex);
        List<ProductInStockingPointPeriod> candidates = collectChangeSetCandidates(previewChangeSet, session.graph());
        Map<String, PispPeriodSnapshotDto> before = snapshotById(candidates);

        planningResultApplicator.applyAllocationDtos(session.graph(), session.rolEngine(), allocations);

        List<PispPeriodSnapshotDto> affectedSnapshots = collectChangedSnapshots(candidates, before);
        if (affectedSnapshots.isEmpty() && !candidates.isEmpty()) {
            for (ProductInStockingPointPeriod period : candidates) {
                affectedSnapshots.add(toSnapshot(period));
            }
        }

        return new MasterPlanSessionOptimizeResultDto(
                session.sessionId(),
                score,
                allocations.size(),
                solveDurationMs,
                affectedSnapshots);
    }

    private void persistSimulateIfEnabled(
            MasterPlanOntologySession session,
            SimulateMasterPlanSessionRequest request) {
        if (!sessionPersistenceFeature.enabled()) {
            return;
        }
        Object value = request.effectiveTargetType() == OntologySimulateTargetType.SUPPLY_ORDER
                ? request.dateValue()
                : request.value();
        ontologyPersistence.persistSimulateChange(
                session.workspaceId(),
                session.sessionId(),
                session.graph(),
                request.effectiveTargetType().name(),
                request.effectiveTargetId(),
                request.property(),
                value);
    }

    private void persistOptimizeIfEnabled(MasterPlanOntologySession session, OptimizerResult optimizerResult) {
        if (!sessionPersistenceFeature.enabled()) {
            return;
        }
        ontologyPersistence.persistOptimizeResult(
                session.workspaceId(),
                session.sessionId(),
                session.graph(),
                Map.of(
                        "engine", optimizerResult.engineId() != null ? optimizerResult.engineId() : "",
                        "score", optimizerResult.scoreSummary() != null ? optimizerResult.scoreSummary() : "",
                        "allocationCount", optimizerResult.persistAllocations().size(),
                        "solveDurationMs", optimizerResult.solveDurationMs()));
    }

    private MasterPlanSolveProfile resolveSolveProfile(String planVersionId, OntologyGraph graph) {
        PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(planVersionId);
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                planVersion != null ? planVersion.strategyId : null);
        LocalDate planningStart = graph.periodsOrdered().isEmpty()
                ? LocalDate.now()
                : graph.periodsOrdered().get(0).getStartDate();
        return new MasterPlanSolveProfile(
                planningStart,
                resolved.capacityStrategy(),
                resolved.objectiveSettings(),
                com.plantops.solver.masterplan.MasterPlanCapacityOverlay.empty(),
                resolved.id());
    }

    private static MasterPlanSessionDto toSessionDto(MasterPlanOntologySession session) {
        OntologyGraph graph = session.graph();
        return new MasterPlanSessionDto(
                session.sessionId(),
                session.basePlanVersionId(),
                graph.pispsById().size(),
                graph.periodsOrdered().size(),
                session.expiresAt());
    }

    private static List<ProductInStockingPointPeriod> collectAffectedCandidates(
            OntologyGraph graph,
            ProductInStockingPointPeriod target) {
        Map<String, Integer> periodSeqById = new HashMap<>();
        for (var period : graph.periodsOrdered()) {
            periodSeqById.put(period.getId(), period.getSequenceNr());
        }
        int targetSeq = periodSeqById.getOrDefault(target.getPeriodId(), Integer.MIN_VALUE);
        return graph.pispPeriodsById().values().stream()
                .filter(period -> target.getPispId().equals(period.getPispId()))
                .filter(period -> periodSeqById.getOrDefault(period.getPeriodId(), Integer.MAX_VALUE) >= targetSeq)
                .sorted(Comparator.comparingInt(period ->
                        periodSeqById.getOrDefault(period.getPeriodId(), Integer.MAX_VALUE)))
                .toList();
    }

    private static Map<String, PispPeriodSnapshotDto> snapshotById(List<ProductInStockingPointPeriod> periods) {
        Map<String, PispPeriodSnapshotDto> snapshots = new HashMap<>();
        for (ProductInStockingPointPeriod period : periods) {
            snapshots.put(period.getId(), toSnapshot(period));
        }
        return snapshots;
    }

    private static List<ProductInStockingPointPeriod> collectChangeSetCandidates(
            ChangeSet changeSet,
            OntologyGraph graph) {
        Map<String, ProductInStockingPointPeriod> candidates = new LinkedHashMap<>();
        for (ChangeOperation operation : changeSet.operations()) {
            if (!ChangeOperation.TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD.equals(operation.targetType())) {
                continue;
            }
            ProductInStockingPointPeriod target = graph.pispPeriod(operation.targetId());
            if (target == null) {
                continue;
            }
            for (ProductInStockingPointPeriod candidate : collectAffectedCandidates(graph, target)) {
                candidates.putIfAbsent(candidate.getId(), candidate);
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private static List<PispPeriodSnapshotDto> collectChangedSnapshots(
            List<ProductInStockingPointPeriod> candidates,
            Map<String, PispPeriodSnapshotDto> before) {
        List<PispPeriodSnapshotDto> snapshots = new ArrayList<>();
        for (ProductInStockingPointPeriod period : candidates) {
            PispPeriodSnapshotDto now = toSnapshot(period);
            if (hasChanged(before.get(period.getId()), now)) {
                snapshots.add(now);
            }
        }
        return snapshots;
    }

    private static boolean hasChanged(PispPeriodSnapshotDto before, PispPeriodSnapshotDto after) {
        if (before == null) {
            return true;
        }
        return Double.compare(before.onHand(), after.onHand()) != 0
                || Double.compare(before.plannedSupplyTotal(), after.plannedSupplyTotal()) != 0
                || Double.compare(before.plannedSupplyTotalMrp(), after.plannedSupplyTotalMrp()) != 0
                || Double.compare(before.plannedSupplyTotalOptimized(), after.plannedSupplyTotalOptimized()) != 0
                || Double.compare(before.plannedDemandQuantityTotal(), after.plannedDemandQuantityTotal()) != 0
                || Double.compare(before.plannedInventoryLevel(), after.plannedInventoryLevel()) != 0
                || Double.compare(before.stockShortageQuantity(), after.stockShortageQuantity()) != 0;
    }

    private static PispPeriodSnapshotDto toSnapshot(ProductInStockingPointPeriod period) {
        return new PispPeriodSnapshotDto(
                period.getId(),
                period.getPispId(),
                period.getPeriodId(),
                period.getOnHand(),
                period.getPlannedSupplyTotal(),
                period.getPlannedSupplyTotalMrp(),
                period.getPlannedSupplyTotalOptimized(),
                period.getPlannedDemandQuantityTotal(),
                period.getPlannedInventoryLevel(),
                period.getStockShortageQuantity());
    }

    private static SrpSnapshotDto toSrpSnapshot(StandardResourcePeriod srp) {
        return new SrpSnapshotDto(
                srp.getId(),
                srp.getStandardResourceId(),
                srp.getPeriodId(),
                srp.getTotalCapacity(),
                srp.getCalendarDowntime(),
                srp.getReservedCapacity(),
                srp.getAvailableCapacity(),
                srp.getFreeCapacity(),
                srp.getOverloadCapacity());
    }

    private static OperationSnapshotDto toOperationSnapshot(Operation op) {
        return new OperationSnapshotDto(
                op.getId(),
                op.getSupplyOrderId(),
                op.getSequenceNr(),
                op.getRoutingSequenceNo(),
                op.getOperationName(),
                op.getProductionDuration(),
                op.getPreprocessingTime(),
                op.getPostprocessingTime(),
                op.getSegmentIndex(),
                op.isLastSegment(),
                op.getParallelGroupId(),
                op.isLocked(),
                op.getEarliestPossibleStartOwn(),
                op.getEarliestPossibleEndOwn(),
                op.getEarliestPossibleStartTotal(),
                op.getEarliestPossibleEndTotal(),
                op.getLatestDesiredStart(),
                op.getLatestDesiredEnd(),
                op.getPlannedStartTotal(),
                op.getPlannedEndTotal(),
                op.isInfeasible());
    }

    private static boolean hasOperationChanged(OperationSnapshotDto before, OperationSnapshotDto after) {
        if (before == null) {
            return true;
        }
        return !java.util.Objects.equals(before.latestDesiredEnd(), after.latestDesiredEnd())
                || !java.util.Objects.equals(before.latestDesiredStart(), after.latestDesiredStart())
                || !java.util.Objects.equals(before.earliestPossibleStartTotal(), after.earliestPossibleStartTotal())
                || !java.util.Objects.equals(before.earliestPossibleEndTotal(), after.earliestPossibleEndTotal())
                || before.infeasible() != after.infeasible();
    }
}
