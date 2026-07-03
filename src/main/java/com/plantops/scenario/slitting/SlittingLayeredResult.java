package com.plantops.scenario.slitting;

import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.SlittingNestSolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SlittingLayeredResult(
        SlittingNestSolution phase1Solution,
        SlittingNestSolution phase2Solution,
        List<RollNode> allNodes,
        List<NestAssignment> allAssignments,
        String score) {

    public static SlittingLayeredResult merge(SlittingNestSolution phase1, SlittingNestSolution phase2) {
        Map<String, RollNode> nodeById = new LinkedHashMap<>();
        for (RollNode n : phase2.getContainers()) {
            nodeById.put(n.getNodeId(), n);
        }
        for (RollNode n : phase1.getContainers()) {
            nodeById.putIfAbsent(n.getNodeId(), n);
        }
        for (NestAssignment a : phase1.getAssignments()) {
            if (a.getPlacedNode() != null) {
                nodeById.putIfAbsent(a.getPlacedNode().getNodeId(), a.getPlacedNode());
            }
        }

        List<NestAssignment> assignments = new ArrayList<>(phase1.getAssignments());
        assignments.addAll(phase2.getAssignments());

        for (NestAssignment a : phase1.getAssignments()) {
            linkParent(nodeById, a);
        }
        for (NestAssignment a : phase2.getAssignments()) {
            linkParent(nodeById, a);
        }

        String score = phase2.getScore() != null ? phase2.getScore().toString() : null;
        return new SlittingLayeredResult(phase1, phase2, new ArrayList<>(nodeById.values()), assignments, score);
    }

    private static void linkParent(Map<String, RollNode> nodeById, NestAssignment a) {
        if (a.getParentNode() == null || a.getPlacedNode() == null) {
            return;
        }
        RollNode parent = nodeById.get(a.getParentNode().getNodeId());
        if (parent != null) {
            a.getPlacedNode().setParent(parent);
            if (parent.getChildren().stream().noneMatch(c -> c.getNodeId().equals(a.getPlacedNode().getNodeId()))) {
                parent.getChildren().add(a.getPlacedNode());
            }
        }
    }
}
