package com.plantops.scenario.slitting;

import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.IntermediateRollCatalogEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.solver.slitting.CuttingMethod;
import com.plantops.solver.slitting.Dimensions;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingNestSolution;
import com.plantops.solver.slitting.SlittingProblemFacts;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SlittingProblemMapper {

    public SlittingNestSolution toPhase1Solution(SlittingPlanningContext ctx) {
        SlittingProblemFacts facts = buildFacts(ctx);
        List<RollNode> containers = ctx.catalog().stream()
                .map(this::toIntermediateContainer)
                .toList();
        List<NestAssignment> assignments = expandChildAssignments(ctx.childOrders());
        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setProblemFacts(facts);
        solution.setContainers(containers);
        solution.setAssignments(assignments);
        return solution;
    }

    public SlittingNestSolution toPhase2Solution(List<MasterRollEntity> masterRolls, List<RollNode> intermediates) {
        SlittingProblemFacts facts = new SlittingProblemFacts();
        facts.setMaxPositionMm(10000);
        List<RollNode> containers = masterRolls.stream().map(this::toMasterContainer).toList();
        List<NestAssignment> assignments = new ArrayList<>();
        int i = 0;
        for (RollNode intermediate : intermediates) {
            assignments.add(new NestAssignment("P2-A-" + (i++), intermediate));
        }
        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setProblemFacts(facts);
        solution.setContainers(containers);
        solution.setAssignments(assignments);
        return solution;
    }

    public List<RollNode> materializeIntermediates(SlittingNestSolution phase1) {
        Map<String, RollNode> used = new LinkedHashMap<>();
        for (NestAssignment a : phase1.getAssignments()) {
            if (a.getParentNode() == null) {
                continue;
            }
            String key = a.getParentNode().getNodeId();
            used.putIfAbsent(key, a.getParentNode());
        }
        return new ArrayList<>(used.values());
    }

    private SlittingProblemFacts buildFacts(SlittingPlanningContext ctx) {
        SlittingProblemFacts facts = new SlittingProblemFacts();
        int max = ctx.masterRolls().stream()
                .map(r -> Math.max(toDouble(r.widthMm), toDouble(r.lengthMm)))
                .max(Double::compare)
                .orElse(5000.0)
                .intValue();
        facts.setMaxPositionMm(Math.max(1000, max));
        facts.setStandardIntermediateSizes(ctx.catalog().stream()
                .map(c -> new Dimensions(toDouble(c.widthMm), toDouble(c.lengthMm), 0))
                .toList());
        return facts;
    }

    private List<NestAssignment> expandChildAssignments(List<ChildSlittingOrderEntity> orders) {
        List<NestAssignment> assignments = new ArrayList<>();
        for (ChildSlittingOrderEntity order : orders) {
            for (int q = 0; q < order.quantity; q++) {
                String nodeId = "CHILD-" + order.orderCode + "-" + (q + 1);
                RollNode child = new RollNode(nodeId, RollType.CHILD,
                        new Dimensions(toDouble(order.widthMm), toDouble(order.lengthMm), toDouble(order.thicknessMm)));
                child.setSourceChildOrderId(order.id);
                assignments.add(new NestAssignment("P1-A-" + nodeId, child));
            }
        }
        return assignments;
    }

    private RollNode toIntermediateContainer(IntermediateRollCatalogEntity catalog) {
        RollNode node = new RollNode("INT-" + catalog.specCode, RollType.INTERMEDIATE,
                new Dimensions(toDouble(catalog.widthMm), toDouble(catalog.lengthMm), 0));
        node.setSourceSpecCode(catalog.specCode);
        node.setCuttingMethod(CuttingMethod.fromString(catalog.cuttingMethod));
        node.setKerfMm(toDouble(catalog.kerfMm));
        return node;
    }

    private RollNode toMasterContainer(MasterRollEntity roll) {
        RollNode node = new RollNode("MASTER-" + roll.rollCode, RollType.MASTER,
                new Dimensions(toDouble(roll.widthMm), toDouble(roll.lengthMm), toDouble(roll.thicknessMm)));
        node.setSourceMasterRollId(roll.id);
        node.setKerfMm(toDouble(roll.kerfLongitudinalMm));
        return node;
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
