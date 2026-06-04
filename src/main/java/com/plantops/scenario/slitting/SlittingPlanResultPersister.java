package com.plantops.scenario.slitting;

import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingPlanVersionEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;

@ApplicationScoped
public class SlittingPlanResultPersister {

    @Transactional
    public void persistResult(String planVersionId, SlittingLayeredResult result, long durationMs) {
        if (SlittingPlanVersionEntity.findByPlanVersionId(planVersionId) == null) {
            throw new NotFoundException("slitting plan not found: " + planVersionId);
        }

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
            if (assignment.getPlacedNode() == null || assignment.getParentNode() == null) {
                continue;
            }
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

        BigDecimal utilizationPct =
                SlittingUtilizationCalculator.computeUtilizationPct(result.allNodes(), result.allAssignments());
        long updated = SlittingPlanVersionEntity.update(
                "status = ?1, score = ?2, utilizationPct = ?3, solveDurationMs = ?4, solverPhase = ?5 "
                        + "where workspaceId = ?6 and planVersionId = ?7",
                SlittingPlanVersionEntity.STATUS_SOLVED,
                result.score(),
                utilizationPct,
                durationMs,
                "COMPLETE",
                SlittingPlanVersionEntity.ws(),
                planVersionId);
        if (updated != 1) {
            throw new IllegalStateException("Failed to mark slitting plan solved: " + planVersionId);
        }
    }
}
