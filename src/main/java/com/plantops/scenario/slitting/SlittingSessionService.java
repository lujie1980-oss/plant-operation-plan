package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.CreateSlittingSessionRequest;
import com.plantops.api.dto.slitting.PatchSlittingSessionRequest;
import com.plantops.api.dto.slitting.SaveSlittingAssignmentsRequest;
import com.plantops.api.dto.slitting.SlittingAssignmentDto;
import com.plantops.api.dto.slitting.SlittingAssignmentPatchDto;
import com.plantops.api.dto.slitting.SlittingPlanTreeDto;
import com.plantops.api.dto.slitting.SlittingRollNodeDto;
import com.plantops.api.dto.slitting.SlittingSessionDto;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class SlittingSessionService {

    @Inject
    SlittingPlanningContextBuilder contextBuilder;

    @Inject
    SlittingSessionProblemMapper sessionProblemMapper;

    @Inject
    SlittingSessionStore sessionStore;

    @Inject
    SlittingPlanService planService;

    @Inject
    SlittingLoggedSolver loggedSolver;

    @Inject
    SlittingSolverRunService runService;

    public SlittingSessionDto create(CreateSlittingSessionRequest request) {
        if (request == null || request.planVersionId() == null || request.planVersionId().isBlank()) {
            throw new BadRequestException("planVersionId required");
        }
        SlittingPlanningContext ctx = contextBuilder.build(request.planVersionId());
        if (ctx.existingNodes().isEmpty()) {
            throw new BadRequestException("plan has no tree; run solve first");
        }
        SlittingNestSolution solution =
                sessionProblemMapper.buildLayerSolution(ctx, request.activeParentNodeId());
        LocalDateTime createdAt = LocalDateTime.now();
        String sessionId = "SN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        SlittingSession session = new SlittingSession(
                sessionId,
                request.planVersionId(),
                request.activeParentNodeId(),
                solution,
                createdAt,
                sessionStore.defaultExpiresAt(createdAt),
                null,
                null);
        sessionStore.put(session);
        return toDto(session, ctx);
    }

    public SlittingSessionDto get(String sessionId) {
        SlittingSession session = sessionStore.require(sessionId);
        SlittingPlanningContext ctx = contextBuilder.build(session.planVersionId());
        return toDto(session, ctx);
    }

    public SlittingSessionDto patch(String sessionId, PatchSlittingSessionRequest request) {
        SlittingSession session = sessionStore.require(sessionId);
        if (request == null || request.assignmentPatches() == null) {
            return get(sessionId);
        }
        Map<String, NestAssignment> byId = new HashMap<>();
        for (NestAssignment assignment : session.solution().getAssignments()) {
            byId.put(assignment.getAssignmentId(), assignment);
        }
        for (SlittingAssignmentPatchDto patch : request.assignmentPatches()) {
            if (patch == null || patch.assignmentId() == null) {
                continue;
            }
            NestAssignment assignment = byId.get(patch.assignmentId());
            if (assignment == null) {
                throw new BadRequestException("unknown assignment: " + patch.assignmentId());
            }
            if (patch.posXMm() != null) {
                assignment.setPositionX(patch.posXMm().intValue());
            }
            if (patch.posYMm() != null) {
                assignment.setPositionY(patch.posYMm().intValue());
            }
            if (patch.rotated() != null) {
                assignment.setRotated(patch.rotated());
            }
            if (patch.pinned() != null) {
                assignment.setPinned(patch.pinned());
            }
        }
        return get(sessionId);
    }

    public SlittingSessionDto localOptimize(String sessionId) throws ExecutionException, InterruptedException {
        SlittingSession session = sessionStore.require(sessionId);
        long start = System.currentTimeMillis();
        String runId = runService.startRun(
                SlittingSolverRunService.TYPE_SESSION_OPTIMIZE,
                session.planVersionId(),
                null,
                sessionId,
                "会话局部优化开始，会话 " + sessionId);
        try {
            SlittingNestSolution solved =
                    loggedSolver.solveSessionLayer(runId, "会话层 Timefold", session.solution());
            long duration = System.currentTimeMillis() - start;
            String score = solved.getScore() != null ? solved.getScore().toString() : null;
            runService.finishSuccess(runId, duration, score, "会话优化完成");
            SlittingSession updated = new SlittingSession(
                    session.sessionId(),
                    session.planVersionId(),
                    session.activeParentNodeId(),
                    solved,
                    session.createdAt(),
                    session.expiresAt(),
                    score,
                    duration);
            sessionStore.put(updated);
            SlittingPlanningContext ctx = contextBuilder.build(session.planVersionId());
            return toDto(updated, ctx);
        } catch (RuntimeException | ExecutionException | InterruptedException ex) {
            runService.finishFailed(runId, System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }

    public SlittingSessionDto autoNest(String sessionId) {
        SlittingSession session = sessionStore.require(sessionId);
        SlittingPlanningContext ctx = contextBuilder.build(session.planVersionId());
        Map<String, RollNode> nodeById = sessionProblemMapper.indexNodes(ctx.existingNodes());
        List<RollNode> candidates = SlittingSessionAutoNest.collectLayerCandidates(nodeById, session.activeParentNodeId());
        int added = SlittingSessionAutoNest.fillUnplaced(session.solution(), candidates, session.activeParentNodeId());
        if (added == 0) {
            throw new BadRequestException("no unplaced blocks to auto-nest on this layer");
        }
        return toDto(session, ctx);
    }

    public SlittingPlanTreeDto confirm(String sessionId) {
        SlittingSession session = sessionStore.require(sessionId);
        SlittingPlanTreeDto tree = planService.getTree(session.planVersionId());
        List<SlittingAssignmentDto> merged = mergeLayerAssignments(tree, session);
        SlittingPlanTreeDto saved = planService.saveAssignments(
                session.planVersionId(),
                new SaveSlittingAssignmentsRequest(merged));
        sessionStore.remove(sessionId);
        return saved;
    }

    private List<SlittingAssignmentDto> mergeLayerAssignments(SlittingPlanTreeDto tree, SlittingSession session) {
        Map<String, String> nodeTypeById = new HashMap<>();
        for (SlittingRollNodeDto node : tree.nodes()) {
            nodeTypeById.put(node.nodeId(), node.nodeType());
        }
        List<SlittingAssignmentDto> kept = new ArrayList<>();
        for (SlittingAssignmentDto dto : tree.assignments()) {
            if (!belongsToLayer(dto, nodeTypeById, session.activeParentNodeId())) {
                kept.add(dto);
            }
        }
        for (NestAssignment assignment : session.solution().getAssignments()) {
            if (assignment.getPlacedNode() == null || assignment.getParentNode() == null) {
                continue;
            }
            kept.add(new SlittingAssignmentDto(
                    assignment.getAssignmentId(),
                    assignment.getPlacedNode().getNodeId(),
                    assignment.getParentNode().getNodeId(),
                    BigDecimal.valueOf(assignment.getPositionX() != null ? assignment.getPositionX() : 0),
                    BigDecimal.valueOf(assignment.getPositionY() != null ? assignment.getPositionY() : 0),
                    assignment.isRotated(),
                    assignment.getSequence(),
                    assignment.isPinned()));
        }
        return kept;
    }

    private static boolean belongsToLayer(
            SlittingAssignmentDto dto,
            Map<String, String> nodeTypeById,
            String activeParentNodeId) {
        if (activeParentNodeId == null || activeParentNodeId.isBlank()) {
            return "MASTER".equals(nodeTypeById.get(dto.parentNodeId()));
        }
        return activeParentNodeId.equals(dto.parentNodeId());
    }

    private SlittingSessionDto toDto(SlittingSession session, SlittingPlanningContext ctx) {
        Map<String, RollNode> nodeById = sessionProblemMapper.indexNodes(ctx.existingNodes());
        List<RollNode> masters = nodeById.values().stream().filter(n -> n.getType() == RollType.MASTER).toList();
        BigDecimal utilization = SlittingUtilizationCalculator.computeUtilizationPct(
                masters,
                session.solution().getAssignments());
        List<SlittingAssignmentDto> assignmentDtos = session.solution().getAssignments().stream()
                .filter(a -> a.getPlacedNode() != null && a.getParentNode() != null)
                .map(a -> new SlittingAssignmentDto(
                        a.getAssignmentId(),
                        a.getPlacedNode().getNodeId(),
                        a.getParentNode().getNodeId(),
                        BigDecimal.valueOf(a.getPositionX() != null ? a.getPositionX() : 0),
                        BigDecimal.valueOf(a.getPositionY() != null ? a.getPositionY() : 0),
                        a.isRotated(),
                        a.getSequence(),
                        a.isPinned()))
                .toList();
        return new SlittingSessionDto(
                session.sessionId(),
                session.planVersionId(),
                session.activeParentNodeId(),
                session.score(),
                session.lastOptimizeMs(),
                utilization,
                assignmentDtos);
    }
}
