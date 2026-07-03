package com.plantops.solver.slitting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlittingGeometryUtilTest {

    @Test
    void violatesKerfClearance_whenTouchingWithoutGap() {
        NestAssignment a = assignment("A", 0, 0, 100, 50);
        NestAssignment b = assignment("B", 100, 0, 80, 50);
        assertTrue(SlittingGeometryUtil.violatesKerfClearance(a, b, 2, 2));
    }

    @Test
    void violatesKerfClearance_falseWhenSeparatedByKerf() {
        NestAssignment a = assignment("A", 0, 0, 100, 50);
        NestAssignment b = assignment("B", 102, 0, 80, 50);
        assertFalse(SlittingGeometryUtil.violatesKerfClearance(a, b, 2, 2));
    }

    @Test
    void overlaps_detectsAreaIntersection() {
        NestAssignment a = assignment("A", 0, 0, 100, 50);
        NestAssignment b = assignment("B", 90, 0, 80, 50);
        assertTrue(SlittingGeometryUtil.overlaps(a, b));
    }

    private static NestAssignment assignment(String id, int x, int y, double w, double h) {
        RollNode parent = new RollNode("P", RollType.INTERMEDIATE, new Dimensions(500, 1000, 0));
        parent.setKerfTransverseMm(2);
        parent.setKerfLongitudinalMm(2);
        RollNode child = new RollNode("C-" + id, RollType.CHILD, new Dimensions(w, h, 0));
        NestAssignment asn = new NestAssignment(id, child);
        asn.setParentNode(parent);
        asn.setPositionX(x);
        asn.setPositionY(y);
        asn.setRotated(false);
        return asn;
    }
}
