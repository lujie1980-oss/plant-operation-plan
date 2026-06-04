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
        for (NestAssignment assignment : sorted) {
            placeFirstFit(solution.getContainers(), assignment);
            assignment.setSequence(seq++);
        }
    }

    private static void placeFirstFit(List<RollNode> containers, NestAssignment assignment) {
        RollNode placed = assignment.getPlacedNode();
        if (placed == null || containers == null || containers.isEmpty()) {
            return;
        }
        List<RollNode> ordered = new ArrayList<>(containers);
        ordered.sort(Comparator.comparingDouble(c -> c.getDimensions() != null ? c.getDimensions().area() : 0));

        for (RollNode container : ordered) {
            if (tryPlace(container, assignment, false)) {
                return;
            }
            if (tryPlace(container, assignment, true)) {
                return;
            }
        }
        assignment.setParentNode(ordered.get(0));
        assignment.setPositionX(0);
        assignment.setPositionY(0);
        assignment.setRotated(Boolean.FALSE);
    }

    private static boolean tryPlace(RollNode container, NestAssignment assignment, boolean rotated) {
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
                if (fits(container, assignment)) {
                    return true;
                }
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
