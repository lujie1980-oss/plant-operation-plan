package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.CreateSlittingPlanRequest;
import com.plantops.api.dto.slitting.SaveSlittingAssignmentsRequest;
import com.plantops.api.dto.slitting.SlittingAssignmentDto;
import com.plantops.api.dto.slitting.SlittingPlanSummaryDto;
import com.plantops.api.dto.slitting.SlittingPlanTreeDto;
import com.plantops.api.dto.slitting.SlittingRollNodeDto;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingPlanChildOrderEntity;
import com.plantops.persistence.entity.SlittingPlanMasterRollEntity;
import com.plantops.persistence.entity.SlittingPlanVersionEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class SlittingPlanService {

    @Inject
    SlittingPlanningContextBuilder contextBuilder;

    @Inject
    SlittingLayeredSolverPipeline solverPipeline;

    @Inject
    MasterRollService masterRollService;

    @Inject
    ChildSlittingOrderService childSlittingOrderService;

    public List<SlittingPlanSummaryDto> listPlans() {
        return SlittingPlanVersionEntity.listInWorkspace().stream().map(SlittingPlanService::toSummary).toList();
    }

    public SlittingPlanSummaryDto getPlan(String planVersionId) {
        return toSummary(requirePlan(planVersionId));
    }

    public SlittingPlanTreeDto getTree(String planVersionId) {
        SlittingPlanVersionEntity plan = requirePlan(planVersionId);
        List<SlittingRollNodeDto> nodes = SlittingRollNodeEntity.listByPlanVersionId(planVersionId).stream()
                .map(SlittingPlanService::toNodeDto)
                .toList();
        List<SlittingAssignmentDto> assignments = SlittingAssignmentEntity.listByPlanVersionId(planVersionId).stream()
                .map(SlittingPlanService::toAssignmentDto)
                .toList();
        return new SlittingPlanTreeDto(plan.planVersionId, nodes, assignments, plan.utilizationPct);
    }

    @Transactional
    public SlittingPlanSummaryDto createPlan(CreateSlittingPlanRequest request) {
        if (request == null || request.masterRollCodes() == null || request.masterRollCodes().isEmpty()) {
            throw new BadRequestException("masterRollCodes required");
        }
        if (request.childOrderCodes() == null || request.childOrderCodes().isEmpty()) {
            throw new BadRequestException("childOrderCodes required");
        }
        String planVersionId = "SLIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SlittingPlanVersionEntity plan = new SlittingPlanVersionEntity();
        plan.stampWorkspace();
        plan.planVersionId = planVersionId;
        plan.name = request.name() != null ? request.name() : planVersionId;
        plan.status = SlittingPlanVersionEntity.STATUS_DRAFT;
        plan.persist();

        for (String rollCode : request.masterRollCodes()) {
            MasterRollEntity roll = masterRollService.requireEntity(rollCode);
            SlittingPlanMasterRollEntity link = new SlittingPlanMasterRollEntity();
            link.stampWorkspace();
            link.planVersionId = planVersionId;
            link.masterRollId = roll.id;
            link.persist();
        }
        for (String orderCode : request.childOrderCodes()) {
            ChildSlittingOrderEntity order = childSlittingOrderService.requireEntity(orderCode);
            SlittingPlanChildOrderEntity link = new SlittingPlanChildOrderEntity();
            link.stampWorkspace();
            link.planVersionId = planVersionId;
            link.childSlittingOrderId = order.id;
            link.persist();
        }
        return toSummary(plan);
    }

    @Transactional
    public SlittingPlanSummaryDto solvePlan(String planVersionId) throws ExecutionException, InterruptedException {
        SlittingPlanVersionEntity plan = requirePlan(planVersionId);
        long start = System.currentTimeMillis();
        SlittingPlanningContext ctx = contextBuilder.build(planVersionId);
        SlittingLayeredResult result = solverPipeline.solve(ctx);
        long duration = System.currentTimeMillis() - start;
        persistResult(planVersionId, result, duration);
        plan = requirePlan(planVersionId);
        return toSummary(plan);
    }

    @Transactional
    public SlittingPlanTreeDto saveAssignments(String planVersionId, SaveSlittingAssignmentsRequest request) {
        requirePlan(planVersionId);
        if (request == null || request.assignments() == null) {
            throw new BadRequestException("assignments required");
        }
        SlittingAssignmentEntity.deleteByPlanVersionId(planVersionId);
        for (SlittingAssignmentDto dto : request.assignments()) {
            SlittingAssignmentEntity entity = new SlittingAssignmentEntity();
            entity.stampWorkspace();
            entity.planVersionId = planVersionId;
            entity.assignmentId = dto.assignmentId();
            entity.childNodeId = dto.childNodeId();
            entity.parentNodeId = dto.parentNodeId();
            entity.posXMm = dto.posXMm();
            entity.posYMm = dto.posYMm();
            entity.rotated = dto.rotated();
            entity.sequence = dto.sequence();
            entity.persist();
        }
        return getTree(planVersionId);
    }

    private void persistResult(String planVersionId, SlittingLayeredResult result, long durationMs) {
        SlittingRollNodeEntity.deleteByPlanVersionId(planVersionId);
        SlittingAssignmentEntity.deleteByPlanVersionId(planVersionId);

        for (RollNode node : result.allNodes()) {
            SlittingRollNodeEntity entity = new SlittingRollNodeEntity();
            entity.stampWorkspace();
            entity.planVersionId = planVersionId;
            entity.nodeId = node.getNodeId();
            entity.nodeType = node.getType().name();
            entity.parentNodeId = node.getParent() != null ? node.getParent().getNodeId() : null;
            entity.widthMm = BigDecimal.valueOf(node.getDimensions().widthMm());
            entity.lengthMm = BigDecimal.valueOf(node.getDimensions().lengthMm());
            entity.thicknessMm = BigDecimal.valueOf(node.getDimensions().thicknessMm());
            if (node.getCuttingMethod() != null) {
                entity.cuttingMethod = node.getCuttingMethod().name();
            }
            entity.kerfMm = BigDecimal.valueOf(node.getKerfMm());
            entity.sourceSpecCode = node.getSourceSpecCode();
            entity.sourceChildOrderId = node.getSourceChildOrderId();
            entity.sourceMasterRollId = node.getSourceMasterRollId();
            entity.persist();
        }

        int seq = 0;
        for (NestAssignment assignment : result.allAssignments()) {
            SlittingAssignmentEntity entity = new SlittingAssignmentEntity();
            entity.stampWorkspace();
            entity.planVersionId = planVersionId;
            entity.assignmentId = assignment.getAssignmentId();
            entity.childNodeId = assignment.getPlacedNode().getNodeId();
            entity.parentNodeId = assignment.getParentNode().getNodeId();
            entity.posXMm = BigDecimal.valueOf(assignment.getPositionX() != null ? assignment.getPositionX() : 0);
            entity.posYMm = BigDecimal.valueOf(assignment.getPositionY() != null ? assignment.getPositionY() : 0);
            entity.rotated = assignment.isRotated();
            entity.sequence = seq++;
            entity.persist();
        }

        SlittingPlanVersionEntity plan = requirePlan(planVersionId);
        plan.status = SlittingPlanVersionEntity.STATUS_SOLVED;
        plan.score = result.score();
        plan.utilizationPct = SlittingUtilizationCalculator.computeUtilizationPct(result.allNodes(), result.allAssignments());
        plan.solveDurationMs = durationMs;
        plan.solverPhase = "COMPLETE";
    }

    private static SlittingPlanVersionEntity requirePlan(String planVersionId) {
        SlittingPlanVersionEntity plan = SlittingPlanVersionEntity.findByPlanVersionId(planVersionId);
        if (plan == null) {
            throw new NotFoundException("slitting plan not found: " + planVersionId);
        }
        return plan;
    }

    private static SlittingPlanSummaryDto toSummary(SlittingPlanVersionEntity plan) {
        return new SlittingPlanSummaryDto(
                plan.planVersionId,
                plan.name,
                plan.status,
                plan.score,
                plan.utilizationPct,
                plan.solveDurationMs,
                plan.solverPhase);
    }

    private static SlittingRollNodeDto toNodeDto(SlittingRollNodeEntity entity) {
        return new SlittingRollNodeDto(
                entity.nodeId,
                entity.nodeType,
                entity.parentNodeId,
                entity.widthMm,
                entity.lengthMm,
                entity.thicknessMm,
                entity.cuttingMethod,
                entity.sourceSpecCode);
    }

    private static SlittingAssignmentDto toAssignmentDto(SlittingAssignmentEntity entity) {
        return new SlittingAssignmentDto(
                entity.assignmentId,
                entity.childNodeId,
                entity.parentNodeId,
                entity.posXMm,
                entity.posYMm,
                entity.rotated,
                entity.sequence);
    }
}
