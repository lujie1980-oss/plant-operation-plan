package com.plantops.solver.slitting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlittingConstructionHeuristic {

    private SlittingConstructionHeuristic() {
    }

    public static void seedFFD(SlittingNestSolution solution) {
        if (solution == null || solution.getAssignments() == null) {
            return;
        }
        List<NestAssignment> sorted = new ArrayList<>(solution.getAssignments());
        sorted.sort(Comparator.comparingDouble(
                (NestAssignment a) -> a.getPlacedNode() != null && a.getPlacedNode().getDimensions() != null
                        ? -a.getPlacedNode().getDimensions().area()
                        : 0));
        int seq = 0;
        List<NestAssignment> placedSoFar = new ArrayList<>();
        for (NestAssignment assignment : sorted) {
            if (assignment.isPinned() && assignment.getParentNode() != null && assignment.getPositionX() != null) {
                assignment.setSequence(seq++);
                placedSoFar.add(assignment);
                continue;
            }
            if (!placeFirstFit(solution.getContainers(), assignment, placedSoFar)) {
                assignment.setParentNode(null);
                assignment.setPositionX(null);
                assignment.setPositionY(null);
                assignment.setRotated(null);
            } else {
                placedSoFar.add(assignment);
            }
            assignment.setSequence(seq++);
        }
    }

    private static boolean placeFirstFit(
            List<RollNode> containers, NestAssignment assignment, List<NestAssignment> existing) {
        RollNode placed = assignment.getPlacedNode();
        if (placed == null || containers == null || containers.isEmpty()) {
            return false;
        }
        List<RollNode> ordered = new ArrayList<>(containers);
        ordered.sort(Comparator.comparingDouble(c -> c.getDimensions() != null ? c.getDimensions().area() : 0));

        for (RollNode container : ordered) {
            if (tryPlace(container, assignment, existing, false)) {
                return true;
            }
            if (tryPlace(container, assignment, existing, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryPlace(
            RollNode container, NestAssignment assignment, List<NestAssignment> existing, boolean rotated) {
        if (container.getDimensions() == null || assignment.getPlacedNode() == null) {
            return false;
        }
        double w = SlittingGeometryUtil.effectiveWidth(assignment.getPlacedNode(), rotated);
        double h = SlittingGeometryUtil.effectiveLength(assignment.getPlacedNode(), rotated);
        if (w > container.getDimensions().widthMm() || h > container.getDimensions().lengthMm()) {
            return false;
        }
        int maxX = (int) Math.max(0, container.getDimensions().widthMm() - w);
        int maxY = (int) Math.max(0, container.getDimensions().lengthMm() - h);
        for (int y = 0; y <= maxY; y += 10) {
            for (int x = 0; x <= maxX; x += 10) {
                assignment.setParentNode(container);
                assignment.setPositionX(x);
                assignment.setPositionY(y);
                assignment.setRotated(Boolean.valueOf(rotated));
                if (fits(container, assignment) && !violatesExisting(container, assignment, existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean violatesExisting(
            RollNode container, NestAssignment candidate, List<NestAssignment> existing) {
        if (existing == null || existing.isEmpty()) {
            return false;
        }
        double kerfW = SlittingGeometryUtil.kerfWidthMm(container);
        double kerfL = SlittingGeometryUtil.kerfLengthMm(container);
        for (NestAssignment other : existing) {
            if (other == candidate) {
                continue;
            }
            if (other.getParentNode() == null || other.getPositionX() == null) {
                continue;
            }
            if (!container.getNodeId().equals(other.getParentNode().getNodeId())) {
                continue;
            }
            if (SlittingGeometryUtil.violatesKerfClearance(other, candidate, kerfW, kerfL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(RollNode container, NestAssignment candidate) {
        double w = SlittingGeometryUtil.effectiveWidth(candidate.getPlacedNode(), candidate.isRotated());
        double h = SlittingGeometryUtil.effectiveLength(candidate.getPlacedNode(), candidate.isRotated());
        if (candidate.getPositionX() + w > container.getDimensions().widthMm()
                || candidate.getPositionY() + h > container.getDimensions().lengthMm()) {
            return false;
        }
        return true;
    }
}
