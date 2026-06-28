package com.plantops.solver.slitting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlittingConstructionHeuristicTest {

    @Test
    void seedFFD_placesMultipleStripsWithoutOverlap() {
        RollNode region = new RollNode("REG-1", RollType.INTERMEDIATE, new Dimensions(1600, 600000, 0));
        region.setKerfTransverseMm(2);
        region.setKerfLongitudinalMm(2);

        List<NestAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            RollNode child = new RollNode("CHILD-" + i, RollType.CHILD, new Dimensions(730, 3000, 0));
            assignments.add(new NestAssignment("A-" + i, child));
        }

        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setContainers(List.of(region));
        solution.setAssignments(assignments);

        SlittingConstructionHeuristic.seedFFD(solution);

        List<NestAssignment> placed = solution.getAssignments().stream()
                .filter(a -> a.getParentNode() != null && a.getPositionX() != null)
                .toList();
        assertTrue(placed.size() >= 2, "expected at least two strips placed");

        double kerfW = SlittingGeometryUtil.kerfWidthMm(region);
        double kerfL = SlittingGeometryUtil.kerfLengthMm(region);
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(
                        SlittingGeometryUtil.violatesKerfClearance(placed.get(i), placed.get(j), kerfW, kerfL),
                        "assignments must not overlap");
            }
        }
    }
}
