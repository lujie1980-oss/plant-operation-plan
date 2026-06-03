package com.plantops.solver.masterplan;

import com.plantops.scenario.ChangeoverRuleIndex;
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.Joiners;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class MasterPlanConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                materialFeasibleOnSlot(factory),
                resourceMatch(factory),
                notBeforeEarliestFeasibleStart(factory),
                upstreamBeforeAssembly(factory),
                operationSerialPrecedence(factory),
                parallelOperationsSameSlot(factory),
                preferHigherPriorityResource(factory),
                slotCapacity(factory),
                segmentOrderAcrossDays(factory),
                lockedOrdersPreferEarlier(factory),
                minimizeLateness(factory),
                prioritizeHighPriority(factory),
                balanceAdjacentSlotLoadingBothAllocated(factory),
                balanceAdjacentSlotLoadingAgainstEmptyLater(factory),
                balanceAdjacentSlotLoadingAgainstEmptyEarlier(factory),
                minimizeActiveSlotCount(factory),
                minimizeUnusedCapacityInActiveSlots(factory),
                minimizeSlotProductChangeover(factory)
        };
    }

    /**
     * 主计划层：分配到的槽位日期上，多层 MRP 物料必须可满足（采购件看期末库存，自制件递归）。
     */
    private Constraint materialFeasibleOnSlot(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .join(MaterialFeasibilityContext.class)
                .filter((a, ctx) -> a.getTimeSlot() != null
                        && a.getWorkOrderQuantity() != null
                        && !isMaterialFeasible(a, ctx))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Material feasible on slot date");
    }

    private static boolean isMaterialFeasible(OrderAllocation allocation, MaterialFeasibilityContext ctx) {
        if (allocation.getProductCode() == null || allocation.getTimeSlot() == null) {
            return true;
        }
        return MaterialFeasibilityEvaluator.isFeasible(
                allocation.getProductCode(),
                allocation.getWorkOrderQuantity(),
                allocation.getTimeSlot().getDate(),
                ctx);
    }

    private Constraint resourceMatch(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .filter(a -> a.getTimeSlot() != null && !resourceMatches(a))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Resource must match slot");
    }

    private static boolean resourceMatches(OrderAllocation allocation) {
        String slotResourceId = allocation.getTimeSlot().getResourceId();
        List<String> allowed = allocation.getAllowedResourceIds();
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(slotResourceId);
        }
        return allocation.getResourceId().equals(slotResourceId);
    }

    private static final int RESOURCE_PRIORITY_FALLBACK_WEIGHT = 50;

    /** 同工序多资源：优先占用 allowedResourceIds 中排位靠前的（优先级更高）资源。 */
    private Constraint preferHigherPriorityResource(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .filter(a -> a.getTimeSlot() != null)
                .filter(a -> a.getAllowedResourceIds() != null && a.getAllowedResourceIds().size() > 1)
                .penalize(HardSoftScore.ONE_SOFT, a -> {
                    int idx = a.getAllowedResourceIds().indexOf(a.getTimeSlot().getResourceId());
                    return idx <= 0 ? 0 : idx * RESOURCE_PRIORITY_FALLBACK_WEIGHT;
                })
                .asConstraint("Prefer higher priority resource");
    }

    /**
     * 工单首段开工不宜早于「最早可行开始」（上游就绪）。
     * 采用高权重软约束：可行槽位存在时由值域保证满足；当时窗内无可行槽位时，
     * 不再丢弃工单，而是按“早于天数”计成本，并驱动求解器尽量排到靠后（最接近可行）的槽位。
     */
    private static final int EARLIEST_FEASIBLE_VIOLATION_DAY_WEIGHT = 100;

    private Constraint notBeforeEarliestFeasibleStart(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .join(WorkOrderTimingBoundsContext.class)
                .filter((a, bounds) -> a.getTimeSlot() != null
                        && a.getSegmentIndex() == 0
                        && bounds.violatesEarliestStart(a.getWorkOrderNo(), a.getTimeSlot()))
                .penalize(HardSoftScore.ONE_SOFT,
                        (a, bounds) -> Math.max(1, bounds.violationDays(a.getWorkOrderNo(), a.getTimeSlot()))
                                * EARLIEST_FEASIBLE_VIOLATION_DAY_WEIGHT)
                .asConstraint("Not before earliest feasible start");
    }

    /**
     * 子件工单须先于父件工单完工：基于 MRP BOM 依赖边（支持合并工单一对多）。
     */
    private Constraint upstreamBeforeAssembly(ConstraintFactory factory) {
        return factory.forEach(BomDependencyEdge.class)
                .join(OrderAllocation.class,
                        Joiners.equal(BomDependencyEdge::childWorkOrderNo, OrderAllocation::getWorkOrderNo))
                .filter((edge, child) -> child.getTimeSlot() != null && child.isLastSegment())
                .join(OrderAllocation.class,
                        Joiners.filtering((edge, child, parent) -> edge.parentWorkOrderNo().equals(parent.getWorkOrderNo())
                                && parent.getSegmentIndex() == 0
                                && parent.getTimeSlot() != null))
                .filter((edge, child, parent) -> child.getTimeSlot().getIndex() >= parent.getTimeSlot().getIndex())
                .penalize(HardSoftScore.ONE_HARD,
                        (edge, child, parent) -> child.getTimeSlot().getIndex() - parent.getTimeSlot().getIndex() + 1)
                .asConstraint("Upstream before parent work order");
    }

    /** 工序串行：后继槽位 index 不得早于前驱（前道工序末段 → 后道工序首段）。 */
    private Constraint operationSerialPrecedence(ConstraintFactory factory) {
        return factory.forEach(OperationPrecedenceEdge.class)
                .join(OrderAllocation.class,
                        Joiners.equal(OperationPrecedenceEdge::predecessorAllocationId, OrderAllocation::getId))
                .filter((edge, predecessor) -> predecessor.getTimeSlot() != null)
                .join(OrderAllocation.class,
                        Joiners.filtering((edge, predecessor, successor) ->
                                edge.successorAllocationId().equals(successor.getId())))
                .filter((edge, predecessor, successor) -> successor.getTimeSlot() != null
                        && successor.getTimeSlot().getIndex() < predecessor.getTimeSlot().getIndex())
                .penalize(HardSoftScore.ONE_HARD,
                        (edge, predecessor, successor) ->
                                predecessor.getTimeSlot().getIndex() - successor.getTimeSlot().getIndex())
                .asConstraint("Operation serial precedence");
    }

    /** 并行工序组：配对分配须在同一规划槽位（同槽开工）。 */
    private Constraint parallelOperationsSameSlot(ConstraintFactory factory) {
        return factory.forEachUniquePair(OrderAllocation.class,
                        Joiners.equal(OrderAllocation::getParallelGroupId))
                .filter((a, b) -> a.getParallelGroupId() != null && !a.getParallelGroupId().isBlank())
                .filter((a, b) -> a.getTimeSlot() != null && b.getTimeSlot() != null)
                .filter((a, b) -> a.getTimeSlot().getIndex() != b.getTimeSlot().getIndex())
                .penalize(HardSoftScore.ONE_HARD,
                        (a, b) -> Math.abs(a.getTimeSlot().getIndex() - b.getTimeSlot().getIndex()))
                .asConstraint("Parallel operations same start slot");
    }

    private Constraint slotCapacity(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .filter(a -> a.getTimeSlot() != null)
                .groupBy(OrderAllocation::getTimeSlot, ConstraintCollectors.sum(OrderAllocation::getDurationMinutes))
                .join(MasterPlanSettings.class)
                .join(MasterPlanCapacityOverlay.class)
                .filter((slot, total, settings, overlay) -> settings.isCapacityConstrained()
                        && total + overlay.fixedMinutesForSlot(slot.getId()) > slot.getCapacityMinutes())
                .penalize(HardSoftScore.ONE_HARD,
                        (slot, total, settings, overlay) ->
                                total + overlay.fixedMinutesForSlot(slot.getId()) - slot.getCapacityMinutes())
                .asConstraint("Slot capacity");
    }

    /** 同一工单拆段须按段序跨天（前段槽位 index 不得晚于后段）。 */
    private Constraint segmentOrderAcrossDays(ConstraintFactory factory) {
        return factory.forEachUniquePair(OrderAllocation.class,
                        Joiners.equal(OrderAllocation::getWorkOrderNo))
                .filter((earlier, later) -> earlier.getSegmentIndex() < later.getSegmentIndex())
                .filter((earlier, later) -> earlier.getTimeSlot() != null && later.getTimeSlot() != null)
                .filter((earlier, later) -> earlier.getTimeSlot().getIndex() > later.getTimeSlot().getIndex())
                .penalize(HardSoftScore.ONE_HARD,
                        (earlier, later) -> earlier.getTimeSlot().getIndex() - later.getTimeSlot().getIndex())
                .asConstraint("Segment order across days");
    }

    /**
     * 现阶段 locked 仅表示“更希望靠前排”，但由于缺少“原始已锁定槽位”信息，
     * 不能用 Hard 约束强制到某一天，否则会与产能 Hard 约束产生不可满足冲突。
     */
    private Constraint lockedOrdersPreferEarlier(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((a, settings) -> settings.isEnabled(MasterPlanObjectiveCatalog.LOCKED_ORDERS_PREFER_EARLIER)
                        && a.isLocked()
                        && a.getTimeSlot() != null)
                .penalize(HardSoftScore.ONE_SOFT,
                        (a, settings) -> a.getTimeSlot().getIndex() * settings.weight(
                                MasterPlanObjectiveCatalog.LOCKED_ORDERS_PREFER_EARLIER))
                .asConstraint("Locked orders prefer earlier");
    }

    private Constraint minimizeLateness(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((a, settings) -> settings.isEnabled(MasterPlanObjectiveCatalog.MINIMIZE_LATENESS)
                        && a.getTimeSlot() != null
                        && a.getDueDate() != null
                        && a.getTimeSlot().getPeriodEnd().isAfter(a.getDueDate()))
                .penalize(HardSoftScore.ONE_SOFT,
                        (a, settings) -> (int) ChronoUnit.DAYS.between(a.getDueDate(), a.getTimeSlot().getPeriodEnd())
                                * settings.weight(MasterPlanObjectiveCatalog.MINIMIZE_LATENESS))
                .asConstraint("Minimize lateness");
    }

    private Constraint prioritizeHighPriority(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((a, settings) -> settings.isEnabled(MasterPlanObjectiveCatalog.PRIORITIZE_HIGH_PRIORITY)
                        && a.getTimeSlot() != null)
                .penalize(HardSoftScore.ONE_SOFT,
                        (a, settings) -> a.getTimeSlot().getIndex() * a.getPriority() * settings.weight(
                                MasterPlanObjectiveCatalog.PRIORITIZE_HIGH_PRIORITY))
                .asConstraint("Earlier slot for high priority");
    }

    /** 相邻槽位均有分配时，惩罚两槽总负荷差。 */
    private Constraint balanceAdjacentSlotLoadingBothAllocated(ConstraintFactory factory) {
        return factory.forEach(AdjacentSlotPair.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .join(OrderAllocation.class,
                        Joiners.equal((pair, settings) -> pair.earlier(), OrderAllocation::getTimeSlot))
                .groupBy((pair, settings, alloc) -> pair,
                        ConstraintCollectors.sum((pair, settings, alloc) -> alloc.getDurationMinutes()))
                .join(MasterPlanObjectiveSettings.class)
                .join(OrderAllocation.class,
                        Joiners.equal((pair, loadEarlier, settings) -> pair.later(), OrderAllocation::getTimeSlot))
                .groupBy(
                        (pair, loadEarlier, settings, laterAlloc) -> pair,
                        (pair, loadEarlier, settings, laterAlloc) -> loadEarlier,
                        ConstraintCollectors.sum((pair, loadEarlier, settings, laterAlloc) ->
                                laterAlloc.getDurationMinutes()))
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, loadEarlier, loadLater, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .penalize(HardSoftScore.ONE_SOFT,
                        (pair, loadEarlier, loadLater, settings) -> Math.abs(loadLater - loadEarlier)
                                * settings.weight(MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .asConstraint("Balance adjacent slot loading");
    }

    /** 后一槽位无分配（负荷视为 0）时，惩罚前一槽位的负荷。 */
    private Constraint balanceAdjacentSlotLoadingAgainstEmptyLater(ConstraintFactory factory) {
        return factory.forEach(AdjacentSlotPair.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .ifNotExists(OrderAllocation.class,
                        Joiners.equal((pair, settings) -> pair.later(), OrderAllocation::getTimeSlot))
                .join(OrderAllocation.class,
                        Joiners.equal((pair, settings) -> pair.earlier(), OrderAllocation::getTimeSlot))
                .groupBy((pair, settings, alloc) -> pair,
                        ConstraintCollectors.sum((pair, settings, alloc) -> alloc.getDurationMinutes()))
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, loadEarlier, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .penalize(HardSoftScore.ONE_SOFT,
                        (pair, loadEarlier, settings) -> loadEarlier
                                * settings.weight(MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .asConstraint("Balance adjacent slot loading empty later");
    }

    /** 前一槽位无分配（负荷视为 0）时，惩罚后一槽位的负荷。 */
    private Constraint balanceAdjacentSlotLoadingAgainstEmptyEarlier(ConstraintFactory factory) {
        return factory.forEach(AdjacentSlotPair.class)
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .ifNotExists(OrderAllocation.class,
                        Joiners.equal((pair, settings) -> pair.earlier(), OrderAllocation::getTimeSlot))
                .join(OrderAllocation.class,
                        Joiners.equal((pair, settings) -> pair.later(), OrderAllocation::getTimeSlot))
                .groupBy((pair, settings, alloc) -> pair,
                        ConstraintCollectors.sum((pair, settings, alloc) -> alloc.getDurationMinutes()))
                .join(MasterPlanObjectiveSettings.class)
                .filter((pair, loadLater, settings) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .penalize(HardSoftScore.ONE_SOFT,
                        (pair, loadLater, settings) -> loadLater
                                * settings.weight(MasterPlanObjectiveCatalog.BALANCE_ADJACENT_SLOT_LOADING))
                .asConstraint("Balance adjacent slot loading empty earlier");
    }

    /**
     * 产能集中（1/2）：同一资源每多一个「有占用」的时间槽计一次开线成本。
     */
    private Constraint minimizeActiveSlotCount(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .filter(a -> a.getTimeSlot() != null)
                .groupBy(OrderAllocation::getTimeSlot, ConstraintCollectors.sum(OrderAllocation::getDurationMinutes))
                .join(MasterPlanObjectiveSettings.class)
                .filter((slot, total, settings) -> settings.isEnabled(MasterPlanObjectiveCatalog.CONCENTRATE_CAPACITY)
                        && total > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (slot, total, settings) -> MasterPlanCapacityConcentration.activeSlotPenalty(
                                slot.getCapacityMinutes(),
                                settings.weight(MasterPlanObjectiveCatalog.CONCENTRATE_CAPACITY)))
                .asConstraint("Minimize active slot count");
    }

    /**
     * 产能集中（2/2）：已占用槽位内剩余产能（分钟）惩罚，促使连续用足单槽产能。
     */
    private Constraint minimizeUnusedCapacityInActiveSlots(ConstraintFactory factory) {
        return factory.forEach(OrderAllocation.class)
                .filter(a -> a.getTimeSlot() != null)
                .groupBy(OrderAllocation::getTimeSlot, ConstraintCollectors.sum(OrderAllocation::getDurationMinutes))
                .join(MasterPlanObjectiveSettings.class)
                .join(MasterPlanCapacityOverlay.class)
                .filter((slot, total, settings, overlay) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.CONCENTRATE_CAPACITY)
                        && total > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        (slot, total, settings, overlay) -> MasterPlanCapacityConcentration.unusedCapacityPenalty(
                                slot.getCapacityMinutes(),
                                overlay.fixedMinutesForSlot(slot.getId()),
                                Math.toIntExact(total),
                                settings.weight(MasterPlanObjectiveCatalog.CONCENTRATE_CAPACITY)))
                .asConstraint("Minimize unused capacity in active slots");
    }

    /**
     * 减少槽内换型：同一时间槽内不同产品对的切换成本（换型矩阵 + 名义 fallback）。
     */
    private Constraint minimizeSlotProductChangeover(ConstraintFactory factory) {
        return factory.forEachUniquePair(OrderAllocation.class, Joiners.equal(OrderAllocation::getTimeSlot))
                .filter((a, b) -> a.getTimeSlot() != null
                        && a.getProductCode() != null
                        && b.getProductCode() != null
                        && !a.getProductCode().equals(b.getProductCode()))
                .join(MasterPlanObjectiveSettings.class)
                .join(ChangeoverRuleIndex.class)
                .filter((a, b, settings, changeoverRules) -> settings.isEnabled(
                        MasterPlanObjectiveCatalog.MINIMIZE_SLOT_CHANGEOVER))
                .penalize(HardSoftScore.ONE_SOFT,
                        (a, b, settings, changeoverRules) -> MasterPlanSlotChangeover.switchPenaltyMinutes(
                                a, b, changeoverRules)
                                * settings.weight(MasterPlanObjectiveCatalog.MINIMIZE_SLOT_CHANGEOVER))
                .asConstraint("Minimize slot product changeover");
    }

}
