package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.api.dto.MasterPlanResultDto;
import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.PispSummaryDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.SrpSnapshotDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.rol.ChangeOperation;
import com.plantops.rol.ChangeSet;
import com.plantops.rol.RolEngine;
import com.plantops.rol.RolTransaction;
import com.plantops.scenario.MasterPlanService;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
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
    OntologyLoader ontologyLoader;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    OntologyTimefoldMapper ontologyTimefoldMapper;

    @Inject
    RolTransaction rolTransaction;

    public MasterPlanSessionDto create(CreateMasterPlanSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.planVersionId() == null || request.planVersionId().isBlank()) {
            throw new BadRequestException("planVersionId required");
        }

        OntologyGraph graph = ontologyLoader.loadForPlanVersion(request.planVersionId());
        RolEngine rolEngine = RolEngine.withMasterPlanRules(graph);
        LocalDateTime createdAt = LocalDateTime.now();
        String sessionId = "MOS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        MasterPlanOntologySession session = new MasterPlanOntologySession(
                sessionId,
                WorkspaceResolver.currentWorkspaceId(),
                request.planVersionId(),
                graph,
                rolEngine,
                createdAt,
                sessionStore.defaultExpiresAt(createdAt));
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
                .map(srp -> new SrpSnapshotDto(srp.getId(), srp.getStandardResourceId(), srp.getPeriodId(),
                        srp.getTotalCapacity(), srp.getCalendarDowntime(), srp.getReservedCapacity(),
                        srp.getAvailableCapacity(), srp.getFreeCapacity(), srp.getOverloadCapacity()))
                .toList();
    }

    public MasterPlanSessionSimulateResultDto simulate(
            String sessionId,
            SimulateMasterPlanSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.pispPeriodId() == null || request.pispPeriodId().isBlank()) {
            throw new BadRequestException("pispPeriodId required");
        }
        if (request.property() == null || request.property().isBlank()) {
            throw new BadRequestException("property required");
        }
        if (request.value() == null) {
            throw new BadRequestException("value required");
        }

        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        OntologyGraph graph = session.graph();
        ProductInStockingPointPeriod target = graph.pispPeriod(request.pispPeriodId());
        if (target == null) {
            throw new NotFoundException("PISPP not found: " + request.pispPeriodId());
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
        return new MasterPlanSessionSimulateResultDto(recalculatedPeriodIds, snapshots);
    }

    public MasterPlanSessionOptimizeResultDto optimize(String sessionId)
            throws ExecutionException, InterruptedException {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());

        MasterPlanResultDto solveResult = masterPlanService.getResult(session.basePlanVersionId());
        if (solveResult == null) {
            solveResult = masterPlanService.solve();
        }

        List<MasterPlanAllocationDto> allocations = solveResult != null && solveResult.allocations() != null
                ? solveResult.allocations()
                : List.of();
        PeriodIndex periodIndex = PeriodIndex.of(session.graph().periodsOrdered());
        ChangeSet changeSet = ontologyTimefoldMapper.toChangeSet(allocations, session.graph(), periodIndex);

        List<ProductInStockingPointPeriod> candidates = collectChangeSetCandidates(changeSet, session.graph());
        Map<String, PispPeriodSnapshotDto> before = snapshotById(candidates);

        rolTransaction.apply(changeSet, session.graph(), session.rolEngine());

        List<PispPeriodSnapshotDto> affectedSnapshots = collectChangedSnapshots(candidates, before);
        if (affectedSnapshots.isEmpty() && !candidates.isEmpty()) {
            for (ProductInStockingPointPeriod period : candidates) {
                affectedSnapshots.add(toSnapshot(period));
            }
        }

        return new MasterPlanSessionOptimizeResultDto(
                session.sessionId(),
                solveResult != null ? solveResult.score() : null,
                allocations.size(),
                solveResult != null && solveResult.solveDurationMs() != null ? solveResult.solveDurationMs() : 0L,
                affectedSnapshots);
    }

    public MasterPlanSessionConfirmResultDto confirm(String sessionId)
            throws ExecutionException, InterruptedException {
        MasterPlanOntologySession session = sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        MasterPlanResultDto solveResult = masterPlanService.solve();
        String planVersionId = solveResult.planVersionId();
        int allocationCount = (int) MasterPlanAllocationEntity.count("planVersionId = ?1", planVersionId);
        return new MasterPlanSessionConfirmResultDto(session.sessionId(), planVersionId, allocationCount);
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
                period.getPlannedDemandQuantityTotal(),
                period.getPlannedInventoryLevel(),
                period.getStockShortageQuantity());
    }
}
