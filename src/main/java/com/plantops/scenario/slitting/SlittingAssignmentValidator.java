package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.SlittingAssignmentDto;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.persistence.entity.SlittingRollNodeEntity;
import com.plantops.solver.slitting.Dimensions;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingGeometryUtil;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SlittingAssignmentValidator {

    private SlittingAssignmentValidator() {
    }

    public static void validate(String planVersionId, List<SlittingAssignmentDto> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        List<SlittingRollNodeEntity> entities = SlittingRollNodeEntity.listByPlanVersionId(planVersionId);
        Map<String, SlittingRollNodeEntity> entityById = new HashMap<>();
        Map<String, RollNode> nodesById = new HashMap<>();
        for (SlittingRollNodeEntity entity : entities) {
            entityById.put(entity.nodeId, entity);
            nodesById.put(entity.nodeId, toRollNode(entity));
        }
        inheritKerfFromMaster(nodesById, entityById);
        List<NestAssignment> placed = new ArrayList<>();
        for (SlittingAssignmentDto dto : assignments) {
            if (dto.assignmentId() == null || dto.assignmentId().isBlank()) {
                throw new BadRequestException("assignmentId required");
            }
            if (dto.childNodeId() == null || dto.parentNodeId() == null) {
                throw new BadRequestException("childNodeId and parentNodeId required");
            }
            RollNode child = nodesById.get(dto.childNodeId());
            RollNode parent = nodesById.get(dto.parentNodeId());
            if (child == null) {
                throw new BadRequestException("unknown child node: " + dto.childNodeId());
            }
            if (parent == null) {
                throw new BadRequestException("unknown parent node: " + dto.parentNodeId());
            }
            validateParentChildTypes(child, parent);
            NestAssignment assignment = new NestAssignment(dto.assignmentId(), child);
            assignment.setParentNode(parent);
            assignment.setPositionX(toInt(dto.posXMm()));
            assignment.setPositionY(toInt(dto.posYMm()));
            assignment.setRotated(Boolean.valueOf(dto.rotated()));
            if (overflows(parent, assignment)) {
                throw new BadRequestException("assignment " + dto.assignmentId() + " exceeds parent boundary");
            }
            double kerfW = SlittingGeometryUtil.kerfWidthMm(parent);
            double kerfL = SlittingGeometryUtil.kerfLengthMm(parent);
            for (NestAssignment existing : placed) {
                if (!existing.getParentNode().getNodeId().equals(assignment.getParentNode().getNodeId())) {
                    continue;
                }
                if (SlittingGeometryUtil.violatesKerfClearance(existing, assignment, kerfW, kerfL)) {
                    throw new BadRequestException("assignment " + dto.assignmentId()
                            + " overlaps or violates kerf clearance on parent " + dto.parentNodeId()
                            + " (width kerf " + kerfW + " mm, length kerf " + kerfL + " mm)");
                }
            }
            placed.add(assignment);
        }
    }

    private static void validateParentChildTypes(RollNode child, RollNode parent) {
        if (child.getType() == RollType.CHILD && parent.getType() != RollType.INTERMEDIATE) {
            throw new BadRequestException("child assignment must use intermediate parent");
        }
        if (child.getType() == RollType.INTERMEDIATE
                && parent.getType() != RollType.MASTER
                && parent.getType() != RollType.INTERMEDIATE) {
            throw new BadRequestException("intermediate assignment must use master or intermediate parent");
        }
    }

    /** Studio editor: same geometry checks, allows nested intermediate regions. */
    public static void validateStudio(String planVersionId, List<SlittingAssignmentDto> assignments) {
        validate(planVersionId, assignments);
    }

    private static boolean overflows(RollNode parent, NestAssignment assignment) {
        double w = SlittingGeometryUtil.effectiveWidth(assignment.getPlacedNode(), assignment.isRotated());
        double h = SlittingGeometryUtil.effectiveLength(assignment.getPlacedNode(), assignment.isRotated());
        int px = assignment.getPositionX() != null ? assignment.getPositionX() : 0;
        int py = assignment.getPositionY() != null ? assignment.getPositionY() : 0;
        return px + w > parent.getDimensions().widthMm() || py + h > parent.getDimensions().lengthMm();
    }

    private static void inheritKerfFromMaster(
            Map<String, RollNode> nodesById, Map<String, SlittingRollNodeEntity> entityById) {
        for (RollNode node : nodesById.values()) {
            if (node.getType() == RollType.MASTER) {
                continue;
            }
            RollNode master = findAncestorMaster(node.getNodeId(), nodesById, entityById);
            if (master == null) {
                continue;
            }
            if (node.getKerfTransverseMm() <= 0 && master.getKerfTransverseMm() > 0) {
                node.setKerfTransverseMm(master.getKerfTransverseMm());
            }
            if (node.getKerfLongitudinalMm() <= 0 && master.getKerfLongitudinalMm() > 0) {
                node.setKerfLongitudinalMm(master.getKerfLongitudinalMm());
            }
            if (node.getKerfMm() <= 0 && master.getKerfMm() > 0) {
                node.setKerfMm(master.getKerfMm());
            }
        }
    }

    private static RollNode findAncestorMaster(
            String nodeId, Map<String, RollNode> nodesById, Map<String, SlittingRollNodeEntity> entityById) {
        SlittingRollNodeEntity current = entityById.get(nodeId);
        while (current != null) {
            RollNode roll = nodesById.get(current.nodeId);
            if (roll != null && roll.getType() == RollType.MASTER) {
                return roll;
            }
            current = current.parentNodeId != null ? entityById.get(current.parentNodeId) : null;
        }
        return null;
    }

    private static RollNode toRollNode(SlittingRollNodeEntity entity) {
        RollType type = RollType.valueOf(entity.nodeType);
        Dimensions dims = new Dimensions(
                toDouble(entity.widthMm),
                toDouble(entity.lengthMm),
                entity.thicknessMm != null ? toDouble(entity.thicknessMm) : 0);
        RollNode node = new RollNode(entity.nodeId, type, dims);
        if (entity.kerfMm != null && entity.kerfMm.signum() > 0) {
            double k = toDouble(entity.kerfMm);
            node.setKerfMm(k);
            node.setKerfTransverseMm(k);
            node.setKerfLongitudinalMm(k);
        }
        if (type == RollType.MASTER && entity.nodeId != null && entity.nodeId.startsWith("MASTER-")) {
            String rollCode = entity.nodeId.substring("MASTER-".length());
            MasterRollEntity roll = MasterRollEntity.findByRollCode(rollCode);
            if (roll != null) {
                node.setKerfTransverseMm(toDouble(roll.kerfTransverseMm));
                node.setKerfLongitudinalMm(toDouble(roll.kerfLongitudinalMm));
                node.setKerfMm(Math.max(node.getKerfTransverseMm(), node.getKerfLongitudinalMm()));
            }
        }
        return node;
    }

    private static int toInt(BigDecimal value) {
        return value != null ? value.intValue() : 0;
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
