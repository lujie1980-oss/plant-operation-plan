package com.plantops.scenario.planning.optimizer.ortools;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import com.plantops.scenario.planning.ResourceCapacityOverloadCalculator;
import com.plantops.scenario.planning.ResourceCapacitySeedValidator;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCalendarMoments;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.MaterialFeasibilityEvaluator;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多机台拆分 CP-SAT：每 {@link ResourceCapacityAssignment} 选择 eligible 槽位与 assignedMinutes，
 * 同工序 Σ assignedMinutes = operationTotalMinutes。
 */
public final class OrtoolsResourceCapacityCpSolver {

    private static volatile boolean nativeLoaded;

    public record SolveOutcome(
            List<ResourceCapacityAssignment> assigned,
            String scoreSummary,
            boolean feasible,
            int capacityOverloadMinutes) {

        public SolveOutcome(List<ResourceCapacityAssignment> assigned, String scoreSummary, boolean feasible) {
            this(assigned, scoreSummary, feasible, 0);
        }
    }

    private OrtoolsResourceCapacityCpSolver() {
    }

    public static void ensureNativeLoaded() {
        if (!nativeLoaded) {
            synchronized (OrtoolsResourceCapacityCpSolver.class) {
                if (!nativeLoaded) {
                    Loader.loadNativeLibraries();
                    nativeLoaded = true;
                }
            }
        }
    }

    public static SolveOutcome solve(MasterPlanSchedule schedule, Set<String> scopedWorkOrderNos) {
        ensureNativeLoaded();
        if (schedule == null || !schedule.hasResourceCapacityAssignments()) {
            return new SolveOutcome(List.of(), "0hard/0soft", true);
        }

        List<ResourceCapacityAssignment> working = new ArrayList<>();
        for (ResourceCapacityAssignment source : schedule.getResourceCapacityAssignments()) {
            if (!inScope(source, scopedWorkOrderNos)) {
                continue;
            }
            ResourceCapacityAssignment copy = cloneAssignment(source);
            if (copy.isLocked() && copy.getTimeSlot() != null && copy.getAssignedMinutes() > 0) {
                working.add(copy);
                continue;
            }
            working.add(copy);
        }
        if (working.isEmpty()) {
            return new SolveOutcome(List.of(), "0hard/0soft", true);
        }

        List<ResourceCapacityAssignment> toAssign = working.stream()
                .filter(a -> !a.isLocked() || a.getTimeSlot() == null || a.getAssignedMinutes() <= 0)
                .filter(a -> !a.getEligibleTimeSlots().isEmpty())
                .toList();
        if (toAssign.isEmpty()) {
            boolean capacityConstrained = schedule.getPlanningSettings() != null
                    && schedule.getPlanningSettings().isCapacityConstrained();
            return finalizeOutcome(working, schedule, capacityConstrained);
        }

        SolveOutcome seeded = tryFinalizeSeededSolution(working, toAssign, schedule);
        if (seeded != null) {
            return seeded;
        }
        clearSeedHints(toAssign);

        MasterPlanCapacityOverlay overlay = schedule.getCapacityOverlay() != null
                ? schedule.getCapacityOverlay()
                : MasterPlanCapacityOverlay.empty();
        boolean materialEnabled = schedule.getPlanningSettings() != null
                && schedule.getPlanningSettings().isMaterialConstraintEnabled();
        boolean capacityConstrained = schedule.getPlanningSettings() != null
                && schedule.getPlanningSettings().isCapacityConstrained();
        MaterialFeasibilityContext materialContext = schedule.getMaterialFeasibility();

        int minDayOrdinal = computeMinDayOrdinal(schedule.getTimeSlotRange());
        int maxDayOrdinal = computeMaxDayOrdinal(schedule.getTimeSlotRange());
        List<CpConstraintProfile> ladder = CpConstraintProfile.relaxationLadder(materialEnabled);
        List<String> attemptFailures = new ArrayList<>();

        for (int attempt = 0; attempt < ladder.size(); attempt++) {
            CpConstraintProfile profile = ladder.get(attempt);
            resetUnlockedAssignments(toAssign);

            CpSolveAttempt result = runCpSolve(
                    schedule,
                    working,
                    toAssign,
                    overlay,
                    materialContext,
                    capacityConstrained,
                    profile,
                    minDayOrdinal,
                    maxDayOrdinal);
            if (result.success()) {
                applyCpSolution(toAssign, result);
                SolveOutcome outcome = finalizeOutcome(working, schedule, capacityConstrained);
                if (attempt > 0) {
                    return withRelaxationNote(outcome, profile.label());
                }
                return outcome;
            }
            attemptFailures.add(result.failureReason());
        }

        resetUnlockedAssignments(toAssign);
        if (GreedyResourceCapacitySolver.tryAssign(toAssign, working, schedule)) {
            SolveOutcome outcome = finalizeOutcome(working, schedule, capacityConstrained);
            return withRelaxationNote(outcome, "greedy capacity fallback");
        }

        String hint = ResourceCapacityCpDiagnostics.summarize(schedule, toAssign);
        String probe = ResourceCapacityCpDiagnostics.probeRelaxationHint(schedule, toAssign, materialEnabled);
        String attempts = String.join(" → ", attemptFailures);
        return infeasibleOutcome(
                working,
                attempts + ": " + hint + "; " + probe);
    }

    private record CpConstraintProfile(
            boolean bomPrecedence,
            boolean operationPrecedence,
            boolean materialConstraints,
            boolean sameResourceDayOrder,
            String label) {

        static List<CpConstraintProfile> relaxationLadder(boolean materialEnabled) {
            List<CpConstraintProfile> ladder = new ArrayList<>();
            ladder.add(new CpConstraintProfile(true, true, materialEnabled, true, "full"));
            ladder.add(new CpConstraintProfile(false, true, materialEnabled, true, "no BOM"));
            ladder.add(new CpConstraintProfile(false, false, materialEnabled, true, "no BOM/operation precedence"));
            ladder.add(new CpConstraintProfile(false, false, materialEnabled, false, "capacity only"));
            if (materialEnabled) {
                ladder.add(new CpConstraintProfile(
                        false, false, false, false, "capacity only (material relaxed)"));
            }
            return ladder;
        }
    }

    private static SolveOutcome withRelaxationNote(SolveOutcome outcome, String label) {
        return new SolveOutcome(
                outcome.assigned(),
                scoreSummary(outcome.capacityOverloadMinutes()) + " (relaxed: " + label + ")",
                true,
                outcome.capacityOverloadMinutes());
    }

    private static String scoreSummary(int overloadMinutes) {
        return overloadMinutes > 0
                ? "0hard/-" + overloadMinutes + "soft"
                : "0hard/0soft";
    }

    private record CpSolveAttempt(
            boolean success,
            Map<ResourceCapacityAssignment, Map<TimeSlot, Literal>> slotLiterals,
            Map<ResourceCapacityAssignment, IntVar> loadVars,
            CpSolver solver,
            String failureReason) {
    }

    private static void resetUnlockedAssignments(List<ResourceCapacityAssignment> toAssign) {
        for (ResourceCapacityAssignment assignment : toAssign) {
            if (!assignment.isLocked()) {
                assignment.setTimeSlot(null);
                assignment.setAssignedMinutes(0);
            }
        }
    }

    private static CpSolveAttempt runCpSolve(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> working,
            List<ResourceCapacityAssignment> toAssign,
            MasterPlanCapacityOverlay overlay,
            MaterialFeasibilityContext materialContext,
            boolean capacityConstrained,
            CpConstraintProfile profile,
            int minDayOrdinal,
            int maxDayOrdinal) {
        CpModel model = new CpModel();
        Map<ResourceCapacityAssignment, Map<TimeSlot, Literal>> slotLiterals = new LinkedHashMap<>();
        Map<ResourceCapacityAssignment, IntVar> loadVars = new LinkedHashMap<>();
        Map<ResourceCapacityAssignment, IntVar> slotIndexVars = new LinkedHashMap<>();
        Map<ResourceCapacityAssignment, IntVar> dayOrdinalVars = new LinkedHashMap<>();
        Map<String, List<ResourceCapacityAssignment>> byOperationKey = new LinkedHashMap<>();

        for (ResourceCapacityAssignment assignment : toAssign) {
            byOperationKey.computeIfAbsent(assignment.getOperationKey(), ignored -> new ArrayList<>()).add(assignment);
            Map<TimeSlot, Literal> lits = new LinkedHashMap<>();
            List<Literal> choices = new ArrayList<>();
            for (TimeSlot slot : assignment.getEligibleTimeSlots()) {
                if (slot == null || !overlay.isSlotEligibleForReplan(slot)) {
                    continue;
                }
                Literal lit = model.newBoolVar("rca_" + assignment.getId() + "_s_" + slot.getId());
                lits.put(slot, lit);
                choices.add(lit);
            }
            if (choices.isEmpty()) {
                return new CpSolveAttempt(
                        false, null, null, null,
                        "no eligible replan slot for " + assignment.getId());
            }
            model.addExactlyOne(choices.toArray(Literal[]::new));
            slotLiterals.put(assignment, lits);

            int maxLoad = assignment.getOperationTotalMinutes();
            IntVar load = model.newIntVar(0, Math.max(1, maxLoad), "load_" + assignment.getId());
            loadVars.put(assignment, load);

            int maxSlotIndex = schedule.getTimeSlotRange().stream()
                    .mapToInt(TimeSlot::getIndex)
                    .max()
                    .orElse(0);
            IntVar slotIdx = model.newIntVar(0, maxSlotIndex, "slotIdx_" + assignment.getId());
            slotIndexVars.put(assignment, slotIdx);
            for (Map.Entry<TimeSlot, Literal> entry : lits.entrySet()) {
                model.addEquality(slotIdx, entry.getKey().getIndex()).onlyEnforceIf(entry.getValue());
            }

            IntVar dayOrdinal = model.newIntVar(minDayOrdinal, maxDayOrdinal, "dayOrd_" + assignment.getId());
            dayOrdinalVars.put(assignment, dayOrdinal);
            for (Map.Entry<TimeSlot, Literal> entry : lits.entrySet()) {
                model.addEquality(dayOrdinal, MasterPlanCalendarMoments.slotDayOrdinal(entry.getKey()))
                        .onlyEnforceIf(entry.getValue());
            }

            if (profile.materialConstraints() && materialContext != null) {
                applyMaterialSlotRestriction(model, assignment, load, lits, materialContext);
            }
        }

        for (Map.Entry<String, List<ResourceCapacityAssignment>> entry : byOperationKey.entrySet()) {
            List<ResourceCapacityAssignment> group = entry.getValue();
            int total = group.get(0).getOperationTotalMinutes();
            int lockedMinutes = lockedMinutesForOperation(entry.getKey(), working);
            int remaining = total - lockedMinutes;
            if (remaining < 0) {
                return new CpSolveAttempt(
                        false, null, null, null,
                        "locked minutes exceed operation total for " + entry.getKey() + " [" + profile.label() + "]");
            }
            LinearExprBuilder sumBuilder = LinearExpr.newBuilder();
            for (ResourceCapacityAssignment assignment : group) {
                sumBuilder.add(loadVars.get(assignment));
            }
            model.addEquality(sumBuilder.build(), remaining);
        }

        Map<TimeSlot, LinearExprBuilder> slotLoadBuilders = new HashMap<>();
        for (ResourceCapacityAssignment assignment : toAssign) {
            IntVar load = loadVars.get(assignment);
            for (Map.Entry<TimeSlot, Literal> entry : slotLiterals.get(assignment).entrySet()) {
                TimeSlot slot = entry.getKey();
                Literal lit = entry.getValue();
                LinearExprBuilder builder = slotLoadBuilders.computeIfAbsent(slot, ignored -> LinearExpr.newBuilder());
                IntVar contribution = model.newIntVar(0, assignment.getOperationTotalMinutes(),
                        "contrib_" + assignment.getId() + "_" + slot.getId());
                model.addEquality(contribution, load).onlyEnforceIf(lit);
                model.addEquality(contribution, 0).onlyEnforceIf(lit.not());
                builder.add(contribution);
            }
        }

        List<IntVar> overloadVars = new ArrayList<>();
        int maxSlotOverload = toAssign.stream()
                .mapToInt(ResourceCapacityAssignment::getOperationTotalMinutes)
                .sum();
        for (Map.Entry<TimeSlot, LinearExprBuilder> entry : slotLoadBuilders.entrySet()) {
            TimeSlot slot = entry.getKey();
            int fixed = overlay.fixedMinutesForSlot(slot.getId());
            int locked = lockedMinutesOnSlot(working, slot.getId());
            int capacity = Math.max(0, slot.getCapacityMinutes() - fixed - locked);
            if (capacityConstrained) {
                IntVar overload = model.newIntVar(0, maxSlotOverload, "overload_" + slot.getId());
                model.addGreaterOrEqual(
                        overload,
                        LinearExpr.newBuilder().add(entry.getValue().build()).add(-capacity).build());
                overloadVars.add(overload);
            }
        }
        if (!overloadVars.isEmpty()) {
            LinearExprBuilder objective = LinearExpr.newBuilder();
            for (IntVar overload : overloadVars) {
                objective.add(overload);
            }
            model.minimize(objective.build());
        }

        if (profile.sameResourceDayOrder()) {
            applySameResourceDayOrder(model, toAssign, loadVars, dayOrdinalVars);
        }
        if (profile.operationPrecedence() || profile.bomPrecedence()) {
            Map<String, IntVar> opLatestDayByKey = buildOperationDayVars(
                    model, toAssign, loadVars, dayOrdinalVars, minDayOrdinal, maxDayOrdinal, true);
            Map<String, IntVar> opEarliestDayByKey = buildOperationDayVars(
                    model, toAssign, loadVars, dayOrdinalVars, minDayOrdinal, maxDayOrdinal, false);
            if (profile.operationPrecedence()) {
                applyOperationPrecedence(model, schedule, opLatestDayByKey, opEarliestDayByKey);
            }
            if (profile.bomPrecedence()) {
                applyBomPrecedence(model, schedule, toAssign, opLatestDayByKey, opEarliestDayByKey);
            }
        }
        applySeedHints(model, toAssign, slotLiterals, loadVars, slotIndexVars);

        CpSolver solver = new CpSolver();
        int timeLimit = Math.min(120, Math.max(30, toAssign.size() / 4));
        solver.getParameters().setMaxTimeInSeconds(timeLimit);
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return new CpSolveAttempt(
                    false, null, null, null,
                    "CP-SAT " + status + " [" + profile.label() + "]");
        }
        return new CpSolveAttempt(true, slotLiterals, loadVars, solver, null);
    }

    private static void applyCpSolution(
            List<ResourceCapacityAssignment> toAssign,
            CpSolveAttempt result) {
        CpSolver solver = result.solver();
        Map<ResourceCapacityAssignment, Map<TimeSlot, Literal>> slotLiterals = result.slotLiterals();
        Map<ResourceCapacityAssignment, IntVar> loadVars = result.loadVars();
        for (ResourceCapacityAssignment assignment : toAssign) {
            Map<TimeSlot, Literal> lits = slotLiterals.get(assignment);
            TimeSlot chosen = null;
            for (Map.Entry<TimeSlot, Literal> entry : lits.entrySet()) {
                if (solver.booleanValue(entry.getValue())) {
                    chosen = entry.getKey();
                    break;
                }
            }
            if (chosen == null) {
                throw new IllegalStateException("missing slot choice for " + assignment.getId());
            }
            assignment.setTimeSlot(chosen);
            assignment.setAssignedMinutes((int) solver.value(loadVars.get(assignment)));
        }
    }

    private static int computeMinDayOrdinal(List<TimeSlot> slots) {
        return slots.stream().mapToInt(MasterPlanCalendarMoments::slotDayOrdinal).min().orElse(0);
    }

    private static int computeMaxDayOrdinal(List<TimeSlot> slots) {
        return slots.stream().mapToInt(MasterPlanCalendarMoments::slotDayOrdinal).max().orElse(0);
    }

    private static int lockedMinutesForOperation(
            String operationKey,
            List<ResourceCapacityAssignment> assignments) {
        int locked = 0;
        for (ResourceCapacityAssignment assignment : assignments) {
            if (!assignment.isLocked() || assignment.getTimeSlot() == null) {
                continue;
            }
            if (operationKey.equals(assignment.getOperationKey())) {
                locked += Math.max(0, assignment.getAssignedMinutes());
            }
        }
        return locked;
    }

    private static int lockedMinutesOnSlot(
            List<ResourceCapacityAssignment> assignments,
            String slotId) {
        int locked = 0;
        for (ResourceCapacityAssignment assignment : assignments) {
            if (!assignment.isLocked() || assignment.getTimeSlot() == null) {
                continue;
            }
            if (slotId.equals(assignment.getTimeSlot().getId())) {
                locked += Math.max(0, assignment.getAssignedMinutes());
            }
        }
        return locked;
    }

    private static void clearSeedHints(List<ResourceCapacityAssignment> toAssign) {
        for (ResourceCapacityAssignment assignment : toAssign) {
            if (assignment.isLocked()) {
                continue;
            }
            assignment.setTimeSlot(null);
            assignment.setAssignedMinutes(0);
        }
    }

    private static void applyMaterialSlotRestriction(
            CpModel model,
            ResourceCapacityAssignment assignment,
            IntVar load,
            Map<TimeSlot, Literal> lits,
            MaterialFeasibilityContext materialContext) {
        for (Map.Entry<TimeSlot, Literal> entry : lits.entrySet()) {
            TimeSlot slot = entry.getKey();
            if (MaterialFeasibilityEvaluator.isFeasible(
                    assignment.getProductCode(),
                    assignment.getWorkOrderQuantity(),
                    slot.getDate(),
                    materialContext)) {
                continue;
            }
            model.addEquality(load, 0).onlyEnforceIf(entry.getValue());
        }
    }

    /**
     * 仅当前段有正负载时，才强制日段槽位日期单调（避免 load=0 占位实体把日序卡死）。
     */
    private static void applySameResourceDayOrder(
            CpModel model,
            List<ResourceCapacityAssignment> assignments,
            Map<ResourceCapacityAssignment, IntVar> loadVars,
            Map<ResourceCapacityAssignment, IntVar> dayOrdinalVars) {
        Map<String, List<ResourceCapacityAssignment>> grouped = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            String key = assignment.getOperationKey() + "@" + assignment.getResourceId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(assignment);
        }
        for (List<ResourceCapacityAssignment> group : grouped.values()) {
            group.sort(Comparator.comparingInt(ResourceCapacityAssignment::getDaySegmentIndex));
            for (int i = 0; i < group.size() - 1; i++) {
                ResourceCapacityAssignment earlier = group.get(i);
                ResourceCapacityAssignment later = group.get(i + 1);
                IntVar earlierLoad = loadVars.get(earlier);
                IntVar earlierDay = dayOrdinalVars.get(earlier);
                IntVar laterDay = dayOrdinalVars.get(later);
                if (earlierLoad == null || earlierDay == null || laterDay == null) {
                    continue;
                }
                Literal earlierActive = model.newBoolVar("segActive_" + earlier.getId());
                model.addGreaterOrEqual(earlierLoad, 1).onlyEnforceIf(earlierActive);
                model.addEquality(earlierLoad, 0).onlyEnforceIf(earlierActive.not());
                model.addLessOrEqual(earlierDay, laterDay).onlyEnforceIf(earlierActive);
            }
        }
    }

    private static void applyOperationPrecedence(
            CpModel model,
            MasterPlanSchedule schedule,
            Map<String, IntVar> opLatestDayByKey,
            Map<String, IntVar> opEarliestDayByKey) {
        if (schedule.getOperationPrecedenceFacts() == null || schedule.getOperationPrecedenceFacts().isEmpty()) {
            return;
        }
        for (OperationPrecedenceFact fact : schedule.getOperationPrecedenceFacts()) {
            String predKey = ResourceCapacityAssignment.operationKey(
                    fact.workOrderNo(), fact.predecessorOperationSeq());
            String succKey = ResourceCapacityAssignment.operationKey(
                    fact.workOrderNo(), fact.successorOperationSeq());
            IntVar predLatestDay = opLatestDayByKey.get(predKey);
            IntVar succEarliestDay = opEarliestDayByKey.get(succKey);
            if (predLatestDay != null && succEarliestDay != null) {
                model.addLessOrEqual(predLatestDay, succEarliestDay);
            }
        }
    }

    private static void applyBomPrecedence(
            CpModel model,
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            Map<String, IntVar> childLatestDayByKey,
            Map<String, IntVar> parentEarliestDayByKey) {
        if (schedule.getBomDependencyEdges() == null || schedule.getBomDependencyEdges().isEmpty()) {
            return;
        }
        Map<String, Integer> firstOpSeq = new HashMap<>();
        Map<String, Integer> lastOpSeq = new HashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            firstOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::min);
            lastOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::max);
        }
        for (BomDependencyEdge edge : schedule.getBomDependencyEdges()) {
            Integer childLast = lastOpSeq.get(edge.childWorkOrderNo());
            Integer parentFirst = firstOpSeq.get(edge.parentWorkOrderNo());
            if (childLast == null || parentFirst == null) {
                continue;
            }
            String childKey = ResourceCapacityAssignment.operationKey(edge.childWorkOrderNo(), childLast);
            String parentKey = ResourceCapacityAssignment.operationKey(edge.parentWorkOrderNo(), parentFirst);
            IntVar childLatestDay = childLatestDayByKey.get(childKey);
            IntVar parentEarliestDay = parentEarliestDayByKey.get(parentKey);
            if (childLatestDay != null && parentEarliestDay != null) {
                model.addLessOrEqual(childLatestDay, parentEarliestDay);
            }
        }
    }

    private static Map<String, IntVar> buildOperationDayVars(
            CpModel model,
            List<ResourceCapacityAssignment> assignments,
            Map<ResourceCapacityAssignment, IntVar> loadVars,
            Map<ResourceCapacityAssignment, IntVar> dayOrdinalVars,
            int minDayOrdinal,
            int maxDayOrdinal,
            boolean latestDay) {
        Map<String, List<ResourceCapacityAssignment>> byOpKey = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            byOpKey.computeIfAbsent(assignment.getOperationKey(), ignored -> new ArrayList<>()).add(assignment);
        }
        Map<String, IntVar> result = new LinkedHashMap<>();
        int inactiveSentinel = maxDayOrdinal + 1;
        for (Map.Entry<String, List<ResourceCapacityAssignment>> entry : byOpKey.entrySet()) {
            List<IntVar> dayVars = new ArrayList<>();
            for (ResourceCapacityAssignment assignment : entry.getValue()) {
                IntVar dayOrdinal = dayOrdinalVars.get(assignment);
                IntVar load = loadVars.get(assignment);
                Literal active = model.newBoolVar("active_" + assignment.getId() + (latestDay ? "_late" : "_early"));
                model.addGreaterOrEqual(load, 1).onlyEnforceIf(active);
                model.addEquality(load, 0).onlyEnforceIf(active.not());

                IntVar effectiveDay = model.newIntVar(
                        0,
                        inactiveSentinel,
                        (latestDay ? "opDayLate_" : "opDayEarly_") + assignment.getId());
                model.addEquality(effectiveDay, dayOrdinal).onlyEnforceIf(active);
                model.addEquality(
                        effectiveDay,
                        latestDay ? minDayOrdinal : inactiveSentinel).onlyEnforceIf(active.not());
                dayVars.add(effectiveDay);
            }
            IntVar aggregate = model.newIntVar(
                    0,
                    inactiveSentinel,
                    (latestDay ? "opLatestDay_" : "opEarliestDay_") + entry.getKey());
            if (latestDay) {
                model.addMaxEquality(aggregate, dayVars.toArray(IntVar[]::new));
            } else {
                model.addMinEquality(aggregate, dayVars.toArray(IntVar[]::new));
            }
            result.put(entry.getKey(), aggregate);
        }
        return result;
    }

    private static SolveOutcome tryFinalizeSeededSolution(
            List<ResourceCapacityAssignment> working,
            List<ResourceCapacityAssignment> toAssign,
            MasterPlanSchedule schedule) {
        boolean anySeeded = toAssign.stream().anyMatch(a -> a.getTimeSlot() != null);
        if (!anySeeded) {
            return null;
        }
        if (!ResourceCapacitySeedValidator.isFeasible(toAssign, schedule)) {
            return null;
        }
        boolean capacityConstrained = schedule.getPlanningSettings() != null
                && schedule.getPlanningSettings().isCapacityConstrained();
        return finalizeOutcome(working, schedule, capacityConstrained);
    }

    private static void applySeedHints(
            CpModel model,
            List<ResourceCapacityAssignment> toAssign,
            Map<ResourceCapacityAssignment, Map<TimeSlot, Literal>> slotLiterals,
            Map<ResourceCapacityAssignment, IntVar> loadVars,
            Map<ResourceCapacityAssignment, IntVar> slotIndexVars) {
        for (ResourceCapacityAssignment assignment : toAssign) {
            if (assignment.getTimeSlot() == null) {
                continue;
            }
            IntVar load = loadVars.get(assignment);
            IntVar slotIdx = slotIndexVars.get(assignment);
            if (load != null) {
                model.addHint(load, assignment.getAssignedMinutes());
            }
            if (slotIdx != null) {
                model.addHint(slotIdx, assignment.getTimeSlot().getIndex());
            }
            Map<TimeSlot, Literal> lits = slotLiterals.get(assignment);
            if (lits != null) {
                for (Map.Entry<TimeSlot, Literal> entry : lits.entrySet()) {
                    if (entry.getValue() instanceof IntVar boolVar) {
                        model.addHint(boolVar, entry.getKey().equals(assignment.getTimeSlot()) ? 1 : 0);
                    }
                }
            }
        }
    }

    private static SolveOutcome finalizeOutcome(
            List<ResourceCapacityAssignment> working,
            MasterPlanSchedule schedule,
            boolean capacityConstrained) {
        boolean allAssigned = working.stream()
                .allMatch(a -> a.getTimeSlot() != null && a.getAssignedMinutes() >= 0);
        if (!allAssigned) {
            return infeasibleOutcome(working, "unassigned entities remain after solve");
        }
        List<ResourceCapacityAssignment> positive = working.stream()
                .filter(a -> a.getAssignedMinutes() > 0)
                .toList();
        int overload = capacityConstrained && schedule != null
                ? ResourceCapacityOverloadCalculator.totalOverloadMinutes(positive, schedule)
                : 0;
        return new SolveOutcome(positive, scoreSummary(overload), true, overload);
    }

    private static SolveOutcome infeasibleOutcome(List<ResourceCapacityAssignment> working, String reason) {
        String summary = reason != null && !reason.isBlank()
                ? "1hard/0soft (" + reason + ")"
                : "1hard/0soft";
        return new SolveOutcome(working, summary, false);
    }

    private static SolveOutcome infeasibleOutcome(List<ResourceCapacityAssignment> working) {
        return infeasibleOutcome(working, null);
    }

    private static boolean inScope(ResourceCapacityAssignment assignment, Set<String> scopedWorkOrderNos) {
        if (scopedWorkOrderNos == null || scopedWorkOrderNos.isEmpty()) {
            return true;
        }
        return scopedWorkOrderNos.contains(assignment.getWorkOrderNo());
    }

    private static ResourceCapacityAssignment cloneAssignment(ResourceCapacityAssignment source) {
        ResourceCapacityAssignment copy = new ResourceCapacityAssignment();
        copy.setId(source.getId());
        copy.setWorkOrderNo(source.getWorkOrderNo());
        copy.setOperationId(source.getOperationId());
        copy.setOperationSeq(source.getOperationSeq());
        copy.setOperationKey(source.getOperationKey());
        copy.setDaySegmentIndex(source.getDaySegmentIndex());
        copy.setResourceId(source.getResourceId());
        copy.setResourcePriority(source.getResourcePriority());
        copy.setProductCode(source.getProductCode());
        copy.setOperationName(source.getOperationName());
        copy.setOperationTotalMinutes(source.getOperationTotalMinutes());
        copy.setSlotCapacityMinutes(source.getSlotCapacityMinutes());
        copy.setParentWorkOrderNo(source.getParentWorkOrderNo());
        copy.setSalesOrderNo(source.getSalesOrderNo());
        copy.setSalesOrderLineNo(source.getSalesOrderLineNo());
        copy.setDueDate(source.getDueDate());
        copy.setPriority(source.getPriority());
        copy.setWorkOrderQuantity(source.getWorkOrderQuantity());
        copy.setLocked(source.isLocked());
        copy.setParallelGroupId(source.getParallelGroupId());
        copy.setOperationLatestDesiredEnd(source.getOperationLatestDesiredEnd());
        copy.setOperationLatestDesiredStart(source.getOperationLatestDesiredStart());
        copy.setEligibleTimeSlots(source.getEligibleTimeSlots());
        copy.setTimeSlot(source.getTimeSlot());
        copy.setAssignedMinutes(source.getAssignedMinutes());
        return copy;
    }
}
