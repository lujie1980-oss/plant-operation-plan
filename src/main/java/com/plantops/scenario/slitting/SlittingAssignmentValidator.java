package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.SlittingAssignmentDto;
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
        Map<String, RollNode> nodesById = new HashMap<>();
        for (SlittingRollNodeEntity entity : SlittingRollNodeEntity.listByPlanVersionId(planVersionId)) {
            nodesById.put(entity.nodeId, toRollNode(entity));
        }
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
            for (NestAssignment existing : placed) {
                if (SlittingGeometryUtil.overlaps(existing, assignment)) {
                    throw new BadRequestException("assignment " + dto.assignmentId() + " overlaps another on parent "
                            + dto.parentNodeId());
                }
            }
            placed.add(assignment);
        }
    }

    private static void validateParentChildTypes(RollNode child, RollNode parent) {
        if (child.getType() == RollType.CHILD && parent.getType() != RollType.INTERMEDIATE) {
            throw new BadRequestException("child assignment must use intermediate parent");
        }
        if (child.getType() == RollType.INTERMEDIATE && parent.getType() != RollType.MASTER) {
            throw new BadRequestException("intermediate assignment must use master parent");
        }
    }

    private static boolean overflows(RollNode parent, NestAssignment assignment) {
        double w = SlittingGeometryUtil.effectiveWidth(assignment.getPlacedNode(), assignment.isRotated());
        double h = SlittingGeometryUtil.effectiveLength(assignment.getPlacedNode(), assignment.isRotated());
        int px = assignment.getPositionX() != null ? assignment.getPositionX() : 0;
        int py = assignment.getPositionY() != null ? assignment.getPositionY() : 0;
        return px + w > parent.getDimensions().widthMm() || py + h > parent.getDimensions().lengthMm();
    }

    private static RollNode toRollNode(SlittingRollNodeEntity entity) {
        RollType type = RollType.valueOf(entity.nodeType);
        Dimensions dims = new Dimensions(
                toDouble(entity.widthMm),
                toDouble(entity.lengthMm),
                entity.thicknessMm != null ? toDouble(entity.thicknessMm) : 0);
        return new RollNode(entity.nodeId, type, dims);
    }

    private static int toInt(BigDecimal value) {
        return value != null ? value.intValue() : 0;
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
