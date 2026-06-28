package com.plantops.scenario.slitting;

import com.plantops.persistence.entity.SlittingAssignmentEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import com.plantops.solver.slitting.CuttingMethod;
import com.plantops.solver.slitting.Dimensions;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingNestSolution;
import com.plantops.solver.slitting.SlittingProblemFacts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SlittingSessionProblemMapper {

    public SlittingNestSolution buildLayerSolution(SlittingPlanningContext ctx, String activeParentNodeId) {
        Map<String, RollNode> nodeById = indexNodes(ctx.existingNodes());
        List<RollNode> containers;
        List<NestAssignment> assignments = new ArrayList<>();

        if (activeParentNodeId == null || activeParentNodeId.isBlank()) {
            containers = nodeById.values().stream().filter(n -> n.getType() == RollType.MASTER).toList();
            for (SlittingAssignmentEntity entity : ctx.existingAssignments()) {
                RollNode parent = nodeById.get(entity.parentNodeId);
                RollNode child = nodeById.get(entity.childNodeId);
                if (parent == null || child == null || parent.getType() != RollType.MASTER) {
                    continue;
                }
                if (child.getType() != RollType.INTERMEDIATE) {
                    continue;
                }
                assignments.add(toAssignment(entity, child, parent));
            }
        } else {
            RollNode container = nodeById.get(activeParentNodeId);
            if (container == null || container.getType() != RollType.INTERMEDIATE) {
                throw new BadRequestException("activeParentNodeId must reference an INTERMEDIATE node");
            }
            containers = List.of(container);
            for (SlittingAssignmentEntity entity : ctx.existingAssignments()) {
                if (!activeParentNodeId.equals(entity.parentNodeId)) {
                    continue;
                }
                RollNode child = nodeById.get(entity.childNodeId);
                if (child == null || child.getType() != RollType.CHILD) {
                    continue;
                }
                assignments.add(toAssignment(entity, child, container));
            }
        }

        if (containers.isEmpty()) {
            throw new BadRequestException("no containers for session layer");
        }

        SlittingProblemFacts facts = new SlittingProblemFacts();
        int max = containers.stream()
                .mapToInt(c -> (int) Math.max(c.getDimensions().widthMm(), c.getDimensions().lengthMm()))
                .max()
                .orElse(5000);
        facts.setMaxPositionMm(Math.max(1000, max));
        facts.setStandardIntermediateSizes(ctx.catalog().stream()
                .map(c -> new Dimensions(toDouble(c.widthMm), toDouble(c.lengthMm), 0))
                .toList());

        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setProblemFacts(facts);
        solution.setContainers(new ArrayList<>(containers));
        solution.setAssignments(assignments);
        return solution;
    }

    private static NestAssignment toAssignment(SlittingAssignmentEntity entity, RollNode child, RollNode parent) {
        NestAssignment assignment = new NestAssignment(entity.assignmentId, child);
        assignment.setParentNode(parent);
        assignment.setPositionX(entity.posXMm != null ? entity.posXMm.intValue() : 0);
        assignment.setPositionY(entity.posYMm != null ? entity.posYMm.intValue() : 0);
        assignment.setRotated(Boolean.valueOf(entity.rotated));
        assignment.setSequence(entity.sequence != null ? entity.sequence : 0);
        assignment.setPinned(entity.pinned);
        return assignment;
    }

    public Map<String, RollNode> indexNodes(List<SlittingRollNodeEntity> entities) {
        Map<String, RollNode> nodeById = new HashMap<>();
        for (SlittingRollNodeEntity entity : entities) {
            nodeById.put(entity.nodeId, toRollNode(entity));
        }
        for (SlittingRollNodeEntity entity : entities) {
            if (entity.parentNodeId != null) {
                RollNode node = nodeById.get(entity.nodeId);
                RollNode parent = nodeById.get(entity.parentNodeId);
                if (node != null && parent != null) {
                    node.setParent(parent);
                }
            }
        }
        return nodeById;
    }

    private static RollNode toRollNode(SlittingRollNodeEntity entity) {
        RollType type = RollType.valueOf(entity.nodeType);
        Dimensions dims = new Dimensions(
                toDouble(entity.widthMm),
                toDouble(entity.lengthMm),
                entity.thicknessMm != null ? toDouble(entity.thicknessMm) : 0);
        RollNode node = new RollNode(entity.nodeId, type, dims);
        if (entity.cuttingMethod != null) {
            node.setCuttingMethod(CuttingMethod.fromString(entity.cuttingMethod));
        }
        if (entity.kerfMm != null) {
            node.setKerfMm(toDouble(entity.kerfMm));
        }
        node.setSourceSpecCode(entity.sourceSpecCode);
        node.setSourceChildOrderId(entity.sourceChildOrderId);
        node.setSourceMasterRollId(entity.sourceMasterRollId);
        return node;
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
