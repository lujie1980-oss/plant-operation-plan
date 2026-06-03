package com.plantops.scenario.planning.simulation.timing;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.OperationLinkMode;
import com.plantops.scenario.OperationTransferTimeIndex;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.TimingRule;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RoutingChainTimingRule implements TimingRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.OPERATION_TRANSFER_TIME;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public int earliestFloorMinute(SimulationRuleContext ctx, OperationAssignment op) {
        OperationTransferTimeIndex transferRules = transferRules(ctx);
        Integer required = minimumStartRespectingRoutingChain(op, transferRules);
        return required != null ? required : 0;
    }

    public static OperationTransferTimeIndex transferRules(SimulationRuleContext ctx) {
        return ctx.facts() != null
                ? ctx.facts().transferRules()
                : new OperationTransferTimeIndex(List.of());
    }

    public static void clampAssignedStartsToRoutingChain(
            DetailSchedule schedule,
            OperationTransferTimeIndex transferRules) {
        if (schedule == null || transferRules == null) {
            return;
        }
        for (OperationAssignment op : assignedOperations(schedule)) {
            Integer floor = minimumStartRespectingRoutingChain(op, transferRules);
            if (floor == null || op.getStartMinute() == null) {
                continue;
            }
            if (op.getStartMinute() < floor) {
                op.setStartMinute(floor);
            }
        }
    }

    public static boolean bumpEarliestFromRoutingPredecessors(
            DetailSchedule schedule,
            OperationTransferTimeIndex transferRules) {
        if (schedule == null || schedule.getOperations() == null) {
            return false;
        }
        if (transferRules == null) {
            transferRules = new OperationTransferTimeIndex(List.of());
        }
        boolean changed = false;
        for (OperationAssignment succ : schedule.getOperations()) {
            Integer requiredStart = minimumStartRespectingRoutingChain(succ, transferRules);
            if (requiredStart == null) {
                continue;
            }
            if (requiredStart > succ.getEarliestStartMinute()) {
                succ.setEarliestStartMinute(requiredStart);
                changed = true;
            }
        }
        return changed;
    }

    public static int routingPrecedenceViolationMinutes(
            OperationAssignment succ,
            OperationTransferTimeIndex transferRules) {
        if (succ == null || succ.getStartMinute() == null || transferRules == null) {
            return 0;
        }
        Integer requiredStart = minimumStartRespectingRoutingChain(succ, transferRules);
        if (requiredStart == null) {
            return 0;
        }
        return Math.max(0, requiredStart - succ.getStartMinute());
    }

    public static Integer minimumStartRespectingRoutingChain(
            OperationAssignment succ,
            OperationTransferTimeIndex transferRules) {
        if (succ == null || transferRules == null) {
            return null;
        }
        List<OperationAssignment> chain = routingChainRootFirst(succ);
        if (chain.size() < 2) {
            return null;
        }
        Map<OperationAssignment, Integer> simulatedStart = new LinkedHashMap<>();
        OperationAssignment root = chain.get(0);
        simulatedStart.put(root, root.getStartMinute() != null
                ? root.getStartMinute()
                : root.getEarliestStartMinute());

        for (int i = 0; i < chain.size() - 1; i++) {
            OperationAssignment pred = chain.get(i);
            OperationAssignment next = chain.get(i + 1);
            if (pred.getOperationName() == null || next.getOperationName() == null) {
                continue;
            }
            OperationTransferTimeIndex.ResolvedRule rule = transferRules.resolve(
                    pred.getProductCode(), pred.getOperationName(), next.getOperationName());
            int predStart = pred.getStartMinute() != null
                    ? pred.getStartMinute()
                    : simulatedStart.getOrDefault(pred, pred.getEarliestStartMinute());
            Integer nextRequired = requiredStartGivenPredStart(pred, predStart, next, rule);
            if (nextRequired == null) {
                continue;
            }
            simulatedStart.put(next, Math.max(next.getEarliestStartMinute(), nextRequired));
        }
        return simulatedStart.get(succ);
    }

    public static Integer requiredStartGivenPredStart(
            OperationAssignment pred,
            int predStart,
            OperationAssignment succ,
            OperationTransferTimeIndex.ResolvedRule rule) {
        OperationLinkMode mode = rule != null ? rule.linkMode() : OperationLinkMode.STANDARD;
        int minGap = rule != null ? rule.minTransferMinutes() : 0;
        int delayStart = rule != null ? rule.delayStartMinutes() : 0;
        int predEnd = predStart + pred.getDurationMinutes();

        return switch (mode) {
            case SIMULTANEOUS_START -> predStart;
            case DELAYED_START -> predStart + Math.max(0, delayStart);
            case SIMULTANEOUS_END -> predEnd - succ.getDurationMinutes();
            case STANDARD -> predEnd + Math.max(0, minGap);
        };
    }

    public static List<OperationAssignment> routingChainRootFirst(OperationAssignment succ) {
        List<OperationAssignment> reversed = new ArrayList<>();
        OperationAssignment current = succ;
        while (current != null) {
            reversed.add(current);
            current = current.getRoutingPredecessor();
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static java.util.Set<OperationAssignment> assignedOperations(DetailSchedule schedule) {
        java.util.Set<OperationAssignment> assigned = new java.util.HashSet<>();
        if (schedule.getLines() != null) {
            for (var line : schedule.getLines()) {
                if (line.getAssignedOperations() != null) {
                    assigned.addAll(line.getAssignedOperations());
                }
            }
        }
        return assigned;
    }
}
