package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.rol.RolEngine;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MasterPlanOntologySessionService {

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    public MasterPlanSessionDto create(CreateMasterPlanSessionRequest request) {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.planVersionId() == null || request.planVersionId().isBlank()) {
            throw new BadRequestException("planVersionId required");
        }

        OntologyGraph graph = ontologyLoader.loadForPlanVersion(request.planVersionId());
        RolEngine rolEngine = RolEngine.withDefaultPispRules(graph);
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

    public void confirm(String sessionId) {
        sessionStore.require(sessionId, WorkspaceResolver.currentWorkspaceId());
        throw new WebApplicationException("M2: project to MasterPlanAllocationEntity", Response.Status.NOT_IMPLEMENTED);
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
