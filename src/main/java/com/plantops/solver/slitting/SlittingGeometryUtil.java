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
        return violatesKerfClearance(a, b, 0, 0);
    }

    /** API 坐标：posX=宽度轴，posY=长度轴；切边余量分别沿宽/长方向。 */
    public static boolean violatesKerfClearance(
            NestAssignment a, NestAssignment b, double kerfWidthMm, double kerfLengthMm) {
        if (a == null || b == null || a.getParentNode() == null || b.getParentNode() == null) {
            return false;
        }
        if (!a.getParentNode().getNodeId().equals(b.getParentNode().getNodeId())) {
            return false;
        }
        if (a.getAssignmentId() != null && a.getAssignmentId().equals(b.getAssignmentId())) {
            return false;
        }
        double ax1 = a.getPositionX() != null ? a.getPositionX() : 0;
        double ay1 = a.getPositionY() != null ? a.getPositionY() : 0;
        double aw = effectiveWidth(a.getPlacedNode(), a.isRotated());
        double ah = effectiveLength(a.getPlacedNode(), a.isRotated());
        double bx1 = b.getPositionX() != null ? b.getPositionX() : 0;
        double by1 = b.getPositionY() != null ? b.getPositionY() : 0;
        double bw = effectiveWidth(b.getPlacedNode(), b.isRotated());
        double bh = effectiveLength(b.getPlacedNode(), b.isRotated());
        boolean separated =
                ax1 + aw + kerfWidthMm <= bx1
                        || bx1 + bw + kerfWidthMm <= ax1
                        || ay1 + ah + kerfLengthMm <= by1
                        || by1 + bh + kerfLengthMm <= by1;
        return !separated;
    }

    public static double kerfWidthMm(RollNode parent) {
        if (parent == null) {
            return 2;
        }
        if (parent.getKerfTransverseMm() > 0) {
            return parent.getKerfTransverseMm();
        }
        if (parent.getKerfMm() > 0) {
            return parent.getKerfMm();
        }
        return 2;
    }

    public static double kerfLengthMm(RollNode parent) {
        if (parent == null) {
            return 2;
        }
        if (parent.getKerfLongitudinalMm() > 0) {
            return parent.getKerfLongitudinalMm();
        }
        if (parent.getKerfMm() > 0) {
            return parent.getKerfMm();
        }
        return 2;
    }

    /** 子块是否可放入容器（允许旋转 90°）。 */
    public static boolean dimensionsFitContainer(double itemWidthMm, double itemLengthMm, RollNode container) {
        if (container == null || container.getDimensions() == null) {
            return false;
        }
        double cw = container.getDimensions().widthMm();
        double cl = container.getDimensions().lengthMm();
        return (itemWidthMm <= cw && itemLengthMm <= cl) || (itemLengthMm <= cw && itemWidthMm <= cl);
    }
}
