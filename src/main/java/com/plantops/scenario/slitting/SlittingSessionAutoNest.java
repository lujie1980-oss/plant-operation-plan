package com.plantops.scenario.slitting;

import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingConstructionHeuristic;
import com.plantops.solver.slitting.SlittingNestSolution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 在会话层容器内，将尚未放置的子块填入空白区域（启发式，非 Timefold）。
 */
public final class SlittingSessionAutoNest {

    private SlittingSessionAutoNest() {
    }

    public static int fillUnplaced(SlittingNestSolution solution, List<RollNode> candidateNodes, String activeParentNodeId) {
        if (solution == null || candidateNodes == null || candidateNodes.isEmpty()) {
            return 0;
        }
        Set<String> placedChildIds = solution.getAssignments().stream()
                .map(a -> a.getPlacedNode().getNodeId())
                .collect(Collectors.toCollection(HashSet::new));

        RollType expectedChildType = activeParentNodeId == null || activeParentNodeId.isBlank()
                ? RollType.INTERMEDIATE
                : RollType.CHILD;

        List<RollNode> unplaced = candidateNodes.stream()
                .filter(n -> n.getType() == expectedChildType)
                .filter(n -> !placedChildIds.contains(n.getNodeId()))
                .filter(n -> matchesParent(n, activeParentNodeId))
                .toList();

        List<NestAssignment> assignments = new ArrayList<>(solution.getAssignments());
        for (RollNode node : unplaced) {
            assignments.add(new NestAssignment("AUTO-" + UUID.randomUUID().toString().substring(0, 8), node));
        }
        solution.setAssignments(assignments);
        int before = placedChildIds.size();
        SlittingConstructionHeuristic.seedFFD(solution);
        return (int) solution.getAssignments().stream()
                .filter(a -> a.getParentNode() != null && a.getPositionX() != null)
                .count() - before;
    }

    private static boolean matchesParent(RollNode node, String activeParentNodeId) {
        if (activeParentNodeId == null || activeParentNodeId.isBlank()) {
            return true;
        }
        return node.getParent() != null && activeParentNodeId.equals(node.getParent().getNodeId());
    }

    public static List<RollNode> collectLayerCandidates(
            Map<String, RollNode> nodeById,
            String activeParentNodeId) {
        if (activeParentNodeId == null || activeParentNodeId.isBlank()) {
            return nodeById.values().stream().filter(n -> n.getType() == RollType.INTERMEDIATE).toList();
        }
        return nodeById.values().stream()
                .filter(n -> n.getType() == RollType.CHILD)
                .filter(n -> n.getParent() != null && activeParentNodeId.equals(n.getParent().getNodeId()))
                .toList();
    }
}
