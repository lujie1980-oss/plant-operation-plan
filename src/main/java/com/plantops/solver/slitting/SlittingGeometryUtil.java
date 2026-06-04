package com.plantops.solver.slitting;

public final class SlittingGeometryUtil {

    private SlittingGeometryUtil() {
    }

    public static double effectiveWidth(RollNode node, boolean rotated) {
        if (node == null || node.getDimensions() == null) {
            return 0;
        }
        return rotated ? node.getDimensions().lengthMm() : node.getDimensions().widthMm();
    }

    public static double effectiveLength(RollNode node, boolean rotated) {
        if (node == null || node.getDimensions() == null) {
            return 0;
        }
        return rotated ? node.getDimensions().widthMm() : node.getDimensions().lengthMm();
    }

    public static boolean overlaps(NestAssignment a, NestAssignment b) {
        if (a == null || b == null || a.getParentNode() == null || b.getParentNode() == null) {
            return false;
        }
        if (!a.getParentNode().getNodeId().equals(b.getParentNode().getNodeId())) {
            return false;
        }
        if (a.getAssignmentId().equals(b.getAssignmentId())) {
            return false;
        }
        double ax1 = a.getPositionX() != null ? a.getPositionX() : 0;
        double ay1 = a.getPositionY() != null ? a.getPositionY() : 0;
        double ax2 = ax1 + effectiveWidth(a.getPlacedNode(), a.isRotated());
        double ay2 = ay1 + effectiveLength(a.getPlacedNode(), a.isRotated());
        double bx1 = b.getPositionX() != null ? b.getPositionX() : 0;
        double by1 = b.getPositionY() != null ? b.getPositionY() : 0;
        double bx2 = bx1 + effectiveWidth(b.getPlacedNode(), b.isRotated());
        double by2 = by1 + effectiveLength(b.getPlacedNode(), b.isRotated());
        return ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1;
    }
}
