package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.SaveSlittingAssignmentsRequest;
import com.plantops.api.dto.slitting.SlittingAssignmentDto;
import com.plantops.api.dto.slitting.SlittingPlanTreeDto;
import com.plantops.api.dto.slitting.SlittingRollNodeDto;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

@ApplicationScoped
public class SlittingStudioOptimizeService {

    private static final Pattern CHILD_NODE_ID = Pattern.compile("^CHILD-(.+)-\\d+$");

    @Inject
    SlittingPlanningContextBuilder contextBuilder;

    @Inject
    SlittingSessionProblemMapper sessionProblemMapper;

    @Inject
    SlittingLoggedSolver loggedSolver;

    @Inject
    SlittingSolverRunService runService;

    @Inject
    SlittingPlanService planService;

    @Inject
    ChildSlittingOrderService childSlittingOrderService;

    @Transactional
    public SlittingPlanTreeDto optimizeMaster(String planVersionId, String masterNodeId, List<String> orderCodes)
            throws ExecutionException, InterruptedException {
        if (planVersionId == null || planVersionId.isBlank()) {
            throw new BadRequestException("planVersionId required");
        }
        if (masterNodeId == null || masterNodeId.isBlank()) {
            throw new BadRequestException("masterNodeId required");
        }
        SlittingPlanningContext ctx = contextBuilder.build(planVersionId);
        SlittingRollNodeEntity master = ctx.existingNodes().stream()
                .filter(n -> masterNodeId.equals(n.nodeId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("master node not found: " + masterNodeId));
        if (!"MASTER".equals(master.nodeType)) {
            throw new BadRequestException("node is not a master roll: " + masterNodeId);
        }

        String regionId = ensureFullRegion(planVersionId, master);
        List<String> feasibleOrders = filterOrderCodesForMaster(master, orderCodes);
        syncChildOrderNodes(planVersionId, regionId, feasibleOrders);

        String runId = runService.startRun(
                SlittingSolverRunService.TYPE_STUDIO_OPTIMIZE,
                planVersionId,
                masterNodeId,
                null,
                "工作台母卷优化开始：" + masterNodeId + "，候选订单 " + feasibleOrders.size() + " 条");
        long started = System.currentTimeMillis();
        try {
            SlittingPlanningContext refreshed = contextBuilder.build(planVersionId);
            List<String> regionIds = refreshed.existingNodes().stream()
                    .filter(n -> masterNodeId.equals(n.parentNodeId) && "INTERMEDIATE".equals(n.nodeType))
                    .map(n -> n.nodeId)
                    .toList();

            for (String rid : regionIds) {
                optimizeRegionLayer(planVersionId, rid, runId);
            }

            runService.finishSuccess(
                    runId,
                    System.currentTimeMillis() - started,
                    null,
                    "母卷 " + masterNodeId + " 优化完成，区域 " + regionIds.size() + " 个");
            return planService.getTree(planVersionId);
        } catch (RuntimeException | ExecutionException | InterruptedException ex) {
            runService.finishFailed(runId, System.currentTimeMillis() - started, ex.getMessage());
            throw ex;
        }
    }

    private String ensureFullRegion(String planVersionId, SlittingRollNodeEntity master) {
        List<SlittingRollNodeEntity> regions = SlittingRollNodeEntity.listByPlanVersionId(planVersionId).stream()
                .filter(n -> master.nodeId.equals(n.parentNodeId) && "INTERMEDIATE".equals(n.nodeType))
                .toList();
        if (!regions.isEmpty()) {
            return regions.get(0).nodeId;
        }
        String regionId = "REG-" + master.nodeId + "-" + System.currentTimeMillis();
        SlittingRollNodeEntity region = new SlittingRollNodeEntity();
        region.stampWorkspace();
        region.planVersionId = planVersionId;
        region.nodeId = regionId;
        region.nodeType = "INTERMEDIATE";
        region.parentNodeId = master.nodeId;
        region.widthMm = master.widthMm;
        region.lengthMm = master.lengthMm;
        region.persist();

        SlittingAssignmentEntity assignment = new SlittingAssignmentEntity();
        assignment.stampWorkspace();
        assignment.planVersionId = planVersionId;
        assignment.assignmentId = "ASN-" + regionId;
        assignment.childNodeId = regionId;
        assignment.parentNodeId = master.nodeId;
        assignment.posXMm = BigDecimal.ZERO;
        assignment.posYMm = BigDecimal.ZERO;
        assignment.rotated = false;
        assignment.sequence = 0;
        assignment.pinned = false;
        assignment.persist();
        return regionId;
    }

    private void syncChildOrderNodes(String planVersionId, String regionNodeId, List<String> orderCodes) {
        if (orderCodes == null || orderCodes.isEmpty()) {
            return;
        }
        Set<String> placedOrderCodes = new HashSet<>();
        for (SlittingRollNodeEntity node : SlittingRollNodeEntity.listByPlanVersionId(planVersionId)) {
            if (!"CHILD".equals(node.nodeType)) {
                continue;
            }
            var matcher = CHILD_NODE_ID.matcher(node.nodeId);
            if (matcher.matches()) {
                placedOrderCodes.add(matcher.group(1));
            }
        }
        for (String orderCode : orderCodes) {
            if (orderCode == null || orderCode.isBlank() || placedOrderCodes.contains(orderCode.trim())) {
                continue;
            }
            ChildSlittingOrderEntity order = childSlittingOrderService.requireEntity(orderCode.trim());
            String childId = "CHILD-" + order.orderCode + "-" + System.currentTimeMillis();
            SlittingRollNodeEntity child = new SlittingRollNodeEntity();
            child.stampWorkspace();
            child.planVersionId = planVersionId;
            child.nodeId = childId;
            child.nodeType = "CHILD";
            child.parentNodeId = regionNodeId;
            child.widthMm = order.widthMm;
            child.lengthMm = order.lengthMm;
            child.thicknessMm = order.thicknessMm;
            child.persist();
            placedOrderCodes.add(order.orderCode);
        }
    }

    private void optimizeRegionLayer(String planVersionId, String regionNodeId, String runId)
            throws ExecutionException, InterruptedException {
        SlittingPlanningContext ctx = contextBuilder.build(planVersionId);
        SlittingNestSolution solution = sessionProblemMapper.buildLayerSolution(ctx, regionNodeId);
        clearUnpinnedPlacements(solution);

        Map<String, RollNode> nodeById = sessionProblemMapper.indexNodes(ctx.existingNodes());
        List<RollNode> candidates = SlittingSessionAutoNest.collectLayerCandidates(nodeById, regionNodeId);
        SlittingSessionAutoNest.fillUnplaced(solution, candidates, regionNodeId);

        SlittingNestSolution solved = loggedSolver.solveSessionLayer(runId, "区域 " + regionNodeId, solution);
        mergeRegionAssignments(planVersionId, regionNodeId, solved, ctx);
    }

    private static void clearUnpinnedPlacements(SlittingNestSolution solution) {
        for (NestAssignment assignment : solution.getAssignments()) {
            if (!assignment.isPinned()) {
                assignment.setParentNode(null);
                assignment.setPositionX(null);
                assignment.setPositionY(null);
                assignment.setRotated(null);
            }
        }
    }

    private void mergeRegionAssignments(
            String planVersionId,
            String regionNodeId,
            SlittingNestSolution solved,
            SlittingPlanningContext ctx) {
        SlittingPlanTreeDto tree = planService.getTree(planVersionId);
        Map<String, String> nodeTypeById = new HashMap<>();
        for (SlittingRollNodeDto node : tree.nodes()) {
            nodeTypeById.put(node.nodeId(), node.nodeType());
        }

        List<SlittingAssignmentDto> kept = new ArrayList<>();
        for (SlittingAssignmentDto dto : tree.assignments()) {
            if (belongsToRegionChildLayer(dto, nodeTypeById, regionNodeId)) {
                continue;
            }
            kept.add(dto);
        }

        for (NestAssignment assignment : solved.getAssignments()) {
            if (assignment.getPlacedNode() == null || assignment.getParentNode() == null) {
                continue;
            }
            if (assignment.getPositionX() == null || assignment.getPositionY() == null) {
                continue;
            }
            if (!regionNodeId.equals(assignment.getParentNode().getNodeId())) {
                continue;
            }
            SlittingAssignmentDto candidate = new SlittingAssignmentDto(
                    assignment.getAssignmentId(),
                    assignment.getPlacedNode().getNodeId(),
                    assignment.getParentNode().getNodeId(),
                    BigDecimal.valueOf(assignment.getPositionX() != null ? assignment.getPositionX() : 0),
                    BigDecimal.valueOf(assignment.getPositionY() != null ? assignment.getPositionY() : 0),
                    assignment.isRotated(),
                    assignment.getSequence(),
                    assignment.isPinned());
            List<SlittingAssignmentDto> trial = new ArrayList<>(kept);
            trial.add(candidate);
            try {
                SlittingAssignmentValidator.validateStudio(planVersionId, trial);
                kept.add(candidate);
            } catch (BadRequestException ignored) {
                // solver/heuristic may leave an infeasible strip; skip rather than fail whole optimize
            }
        }

        planService.saveAssignments(planVersionId, new SaveSlittingAssignmentsRequest(kept));
    }

    private static boolean belongsToRegionChildLayer(
            SlittingAssignmentDto dto,
            Map<String, String> nodeTypeById,
            String regionNodeId) {
        if (!regionNodeId.equals(dto.parentNodeId())) {
            return false;
        }
        return "CHILD".equals(nodeTypeById.get(dto.childNodeId()));
    }

    private List<String> filterOrderCodesForMaster(SlittingRollNodeEntity master, List<String> orderCodes) {
        if (orderCodes == null || orderCodes.isEmpty()) {
            return List.of();
        }
        double masterWidth = master.widthMm != null ? master.widthMm.doubleValue() : 0;
        double masterLength = master.lengthMm != null ? master.lengthMm.doubleValue() : 0;
        List<String> feasible = new ArrayList<>();
        for (String orderCode : orderCodes) {
            if (orderCode == null || orderCode.isBlank()) {
                continue;
            }
            ChildSlittingOrderEntity order = ChildSlittingOrderEntity.findByOrderCode(orderCode.trim());
            if (order == null) {
                continue;
            }
            double orderWidth = order.widthMm != null ? order.widthMm.doubleValue() : 0;
            double orderLength = order.lengthMm != null ? order.lengthMm.doubleValue() : 0;
            if (orderFitsMaster(orderWidth, orderLength, masterWidth, masterLength)) {
                feasible.add(order.orderCode);
            }
        }
        return feasible;
    }

    private static boolean orderFitsMaster(
            double orderWidth, double orderLength, double masterWidth, double masterLength) {
        if (masterWidth <= 0 || masterLength <= 0 || orderWidth <= 0 || orderLength <= 0) {
            return false;
        }
        return (orderWidth <= masterWidth && orderLength <= masterLength)
                || (orderLength <= masterWidth && orderWidth <= masterLength);
    }
}
