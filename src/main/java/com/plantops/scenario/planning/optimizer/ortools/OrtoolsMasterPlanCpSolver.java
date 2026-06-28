package com.plantops.scenario.planning.optimizer.ortools;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主计划 CP-SAT 模型：为每个 {@link OrderAllocation} 选择 eligible 槽位，满足产能硬约束。
 * soft 目标与 Timefold 不同；对等性测试仅要求 hard 可行 + assignment 键一致率。
 */
public final class OrtoolsMasterPlanCpSolver {

    private static volatile boolean nativeLoaded;

    public record SolveOutcome(
            List<OrderAllocation> assignedAllocations,
            String scoreSummary,
            boolean feasible) {
    }

    private OrtoolsMasterPlanCpSolver() {
    }

    public static void ensureNativeLoaded() {
        if (!nativeLoaded) {
            synchronized (OrtoolsMasterPlanCpSolver.class) {
                if (!nativeLoaded) {
                    Loader.loadNativeLibraries();
                    nativeLoaded = true;
                }
            }
        }
    }

    public static SolveOutcome solve(MasterPlanSchedule schedule, Set<String> scopedSupplyOrderIds) {
        ensureNativeLoaded();
        if (schedule == null || schedule.getOrderAllocations() == null) {
            return new SolveOutcome(List.of(), "0hard/0soft", true);
        }

        List<OrderAllocation> working = new ArrayList<>();
        for (OrderAllocation source : schedule.getOrderAllocations()) {
            if (!inScope(source, scopedSupplyOrderIds)) {
                continue;
            }
            OrderAllocation copy = cloneAllocation(source);
            if (copy.isLocked() && copy.getTimeSlot() != null) {
                working.add(copy);
                continue;
            }
            working.add(copy);
        }
        if (working.isEmpty()) {
            return new SolveOutcome(List.of(), "0hard/0soft", true);
        }

        List<OrderAllocation> toAssign = working.stream()
                .filter(a -> !a.isLocked() || a.getTimeSlot() == null)
                .filter(a -> !a.getEligibleTimeSlots().isEmpty())
                .toList();
        if (toAssign.isEmpty()) {
            return finalizeOutcome(working);
        }

        MasterPlanCapacityOverlay overlay = schedule.getCapacityOverlay() != null
                ? schedule.getCapacityOverlay()
                : MasterPlanCapacityOverlay.empty();

        CpModel model = new CpModel();
        Map<OrderAllocation, Map<TimeSlot, Literal>> assignedLiteral = new LinkedHashMap<>();
        Map<TimeSlot, List<Literal>> literalsBySlot = new HashMap<>();

        for (OrderAllocation allocation : toAssign) {
            Map<TimeSlot, Literal> slotLiterals = new LinkedHashMap<>();
            List<Literal> choiceLiterals = new ArrayList<>();
            for (TimeSlot slot : allocation.getEligibleTimeSlots()) {
                if (slot == null) {
                    continue;
                }
                if (!overlay.isSlotEligibleForReplan(slot)) {
                    continue;
                }
                Literal onSlot = model.newBoolVar("a_" + allocation.getId() + "_s_" + slot.getId());
                slotLiterals.put(slot, onSlot);
                choiceLiterals.add(onSlot);
                literalsBySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(onSlot);
            }
            if (choiceLiterals.isEmpty()) {
                return infeasibleOutcome(working);
            }
            model.addExactlyOne(choiceLiterals.toArray(Literal[]::new));
            assignedLiteral.put(allocation, slotLiterals);
        }

        for (Map.Entry<TimeSlot, List<Literal>> entry : literalsBySlot.entrySet()) {
            TimeSlot slot = entry.getKey();
            int fixed = overlay.fixedMinutesForSlot(slot.getId());
            int capacity = Math.max(0, slot.getCapacityMinutes() - fixed);
            LinearExprBuilder loadBuilder = LinearExpr.newBuilder();
            for (OrderAllocation allocation : toAssign) {
                Map<TimeSlot, Literal> slotLiterals = assignedLiteral.get(allocation);
                if (slotLiterals == null) {
                    continue;
                }
                Literal literal = slotLiterals.get(slot);
                if (literal != null) {
                    loadBuilder.addTerm(literal, allocation.getDurationMinutes());
                }
            }
            model.addLessOrEqual(loadBuilder.build(), capacity);
        }

        applyPrecedence(model, schedule, assignedLiteral, toAssign);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(30);
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return infeasibleOutcome(working);
        }

        for (OrderAllocation allocation : toAssign) {
            Map<TimeSlot, Literal> slotLiterals = assignedLiteral.get(allocation);
            TimeSlot chosen = null;
            for (Map.Entry<TimeSlot, Literal> entry : slotLiterals.entrySet()) {
                if (solver.booleanValue(entry.getValue())) {
                    chosen = entry.getKey();
                    break;
                }
            }
            if (chosen == null) {
                return infeasibleOutcome(working);
            }
            allocation.setTimeSlot(chosen);
        }

        return finalizeOutcome(working);
    }

    private static void applyPrecedence(
            CpModel model,
            MasterPlanSchedule schedule,
            Map<OrderAllocation, Map<TimeSlot, Literal>> assignedLiteral,
            List<OrderAllocation> toAssign) {
        if (schedule.getOperationPrecedenceEdges() == null || schedule.getOperationPrecedenceEdges().isEmpty()) {
            return;
        }
        Map<String, OrderAllocation> byId = new HashMap<>();
        for (OrderAllocation allocation : toAssign) {
            byId.put(allocation.getId(), allocation);
        }
        for (OperationPrecedenceEdge edge : schedule.getOperationPrecedenceEdges()) {
            OrderAllocation predecessor = byId.get(edge.predecessorAllocationId());
            OrderAllocation successor = byId.get(edge.successorAllocationId());
            if (predecessor == null || successor == null) {
                continue;
            }
            IntVar predIndex = slotIndexVar(model, assignedLiteral.get(predecessor), predecessor, "pred_" + predecessor.getId());
            IntVar succIndex = slotIndexVar(model, assignedLiteral.get(successor), successor, "succ_" + successor.getId());
            if (predIndex != null && succIndex != null) {
                model.addLessOrEqual(predIndex, succIndex);
            }
        }
    }

    private static IntVar slotIndexVar(
            CpModel model,
            Map<TimeSlot, Literal> slotLiterals,
            OrderAllocation allocation,
            String name) {
        if (slotLiterals == null || slotLiterals.isEmpty()) {
            return null;
        }
        List<TimeSlot> slots = new ArrayList<>(slotLiterals.keySet());
        slots.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        int maxIndex = slots.stream().mapToInt(TimeSlot::getIndex).max().orElse(0);
        IntVar indexVar = model.newIntVar(0, maxIndex, name);
        for (TimeSlot slot : slots) {
            Literal literal = slotLiterals.get(slot);
            if (literal != null) {
                model.addEquality(indexVar, slot.getIndex()).onlyEnforceIf(literal);
            }
        }
        return indexVar;
    }

    private static SolveOutcome finalizeOutcome(List<OrderAllocation> working) {
        boolean allAssigned = working.stream().allMatch(a -> a.getTimeSlot() != null);
        if (!allAssigned) {
            return infeasibleOutcome(working);
        }
        return new SolveOutcome(working, "0hard/0soft", true);
    }

    private static SolveOutcome infeasibleOutcome(List<OrderAllocation> working) {
        return new SolveOutcome(working, "1hard/0soft", false);
    }

    private static boolean inScope(OrderAllocation allocation, Set<String> scopedSupplyOrderIds) {
        if (scopedSupplyOrderIds == null || scopedSupplyOrderIds.isEmpty()) {
            return true;
        }
        return scopedSupplyOrderIds.contains(allocation.getWorkOrderNo());
    }

    private static OrderAllocation cloneAllocation(OrderAllocation source) {
        OrderAllocation copy = new OrderAllocation();
        copy.setId(source.getId());
        copy.setWorkOrderNo(source.getWorkOrderNo());
        copy.setParentWorkOrderNo(source.getParentWorkOrderNo());
        copy.setSalesOrderNo(source.getSalesOrderNo());
        copy.setSalesOrderLineNo(source.getSalesOrderLineNo());
        copy.setProductCode(source.getProductCode());
        copy.setResourceId(source.getResourceId());
        copy.setOperationName(source.getOperationName());
        copy.setOperationSeq(source.getOperationSeq());
        copy.setDueDate(source.getDueDate());
        copy.setPriority(source.getPriority());
        copy.setDurationMinutes(source.getDurationMinutes());
        copy.setSegmentIndex(source.getSegmentIndex());
        copy.setLastSegment(source.isLastSegment());
        copy.setWorkOrderQuantity(source.getWorkOrderQuantity());
        copy.setLocked(source.isLocked());
        copy.setParallelGroupId(source.getParallelGroupId());
        copy.setParallelOrphan(source.isParallelOrphan());
        copy.setDesignatedLineId(source.getDesignatedLineId());
        copy.setAllowedResourceIds(source.getAllowedResourceIds());
        copy.setEligibleTimeSlots(source.getEligibleTimeSlots());
        copy.setTimeSlot(source.getTimeSlot());
        return copy;
    }
}
