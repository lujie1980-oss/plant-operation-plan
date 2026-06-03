package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import java.time.LocalDate;
import java.util.Objects;

import ai.timefold.solver.core.api.score.stream.Joiners;

public class DetailScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                lineMustBeOpened(factory),
                resourceMatch(factory),
                parallelOperationSameLine(factory),
                parallelOperationSameStartEnd(factory),
                continuousProductionNoInterleaving(factory),
                minimizeDueDateLateness(factory),
                preferMasterPlanContractResource(factory),
                preferHigherPriorityResource(factory),
                minimizeMasterPlanTargetDeviation(factory),
                minimizeProductChangeover(factory),
                routingPrecedence(factory)
        };
    }

    private Constraint lineMustBeOpened(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(op -> op.getLine() != null && !op.getLine().isOpened())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Line must be opened");
    }

    private Constraint resourceMatch(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(op -> op.getLine() != null && !op.acceptsLine(op.getLine()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Resource must match line");
    }

    /** 并行工序：配对的两道工序须落在同一产线。 */
    private Constraint parallelOperationSameLine(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        OperationAssignment.class,
                        Joiners.equal(OperationAssignment::getPairGroupId))
                .filter((left, right) -> left.isParallelPaired()
                        && left.getPairGroupId() != null
                        && left.getLine() != null
                        && right.getLine() != null
                        && !Objects.equals(left.getLine(), right.getLine()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Parallel operation same line");
    }

    /** 并行工序：配对工序同起同止。 */
    private Constraint parallelOperationSameStartEnd(ConstraintFactory factory) {
        return factory.forEachUniquePair(
                        OperationAssignment.class,
                        Joiners.equal(OperationAssignment::getPairGroupId))
                .filter((left, right) -> left.isParallelPaired()
                        && left.getPairGroupId() != null
                        && left.getStartMinute() != null
                        && right.getStartMinute() != null
                        && (!left.getStartMinute().equals(right.getStartMinute())
                                || !Objects.equals(left.getEndMinute(), right.getEndMinute())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Parallel operation same start end");
    }

    /** 连续生产：同组工序在同产线上不得被其它料号隔开。 */
    private Constraint continuousProductionNoInterleaving(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(a -> a.getContinuousGroupId() != null
                        && a.getLine() != null
                        && a.getStartMinute() != null)
                .join(OperationAssignment.class,
                        Joiners.equal(OperationAssignment::getLine),
                        Joiners.equal(OperationAssignment::getContinuousGroupId),
                        Joiners.filtering((earlier, later) -> earlier.getStartMinute() != null
                                && later.getStartMinute() != null
                                && earlier.getStartMinute() < later.getStartMinute()
                                && !earlier.getOperationId().equals(later.getOperationId())))
                .join(OperationAssignment.class,
                        Joiners.equal((earlier, later) -> earlier.getLine(), OperationAssignment::getLine))
                .filter((earlier, later, middle) -> middle.getStartMinute() != null
                        && !middle.getOperationId().equals(earlier.getOperationId())
                        && !middle.getOperationId().equals(later.getOperationId())
                        && middle.getStartMinute() > earlier.getStartMinute()
                        && middle.getStartMinute() < later.getStartMinute()
                        && (middle.getContinuousGroupId() == null
                                || !middle.getContinuousGroupId().equals(earlier.getContinuousGroupId())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Continuous production no interleaving");
    }

    /**
     * L1：末道工序完成日晚于交期（Soft，仅罚延期）。
     */
    private Constraint minimizeDueDateLateness(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .join(DetailScheduleProblemFacts.class)
                .filter((op, facts) -> op.isLastOperationForDueDate()
                        && op.getLine() != null
                        && op.getDueDate() != null
                        && op.getStartMinute() != null)
                .penalize(HardSoftScore.ONE_SOFT, (op, facts) -> {
                    LocalDate actualEnd = ScheduleTimingUtil.completionDate(
                            facts.planningAnchorDate(),
                            op.getStartMinute(),
                            op.getDurationMinutes());
                    return facts.contractSettings().dueDatePenalty(op.getDueDate(), actualEnd);
                })
                .asConstraint("Minimize due date lateness");
    }

    /**
     * L2：相对主计划目标完成日偏差（Soft，早/晚不同公式与权重）。
     */
    private Constraint minimizeMasterPlanTargetDeviation(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .join(DetailScheduleProblemFacts.class)
                .filter((op, facts) -> facts.contractSettings().masterPlanTargetSoftConstraintEnabled())
                .filter((op, facts) -> (op.getMpContractStartDate() != null || op.getMpContractEndDate() != null
                        || op.getMpTargetEndDate() != null)
                        && op.getLine() != null
                        && op.getStartMinute() != null)
                .penalize(HardSoftScore.ONE_SOFT, (op, facts) -> {
                    LocalDate actualStart = ScheduleTimingUtil.startDate(
                            facts.planningAnchorDate(),
                            op.getStartMinute());
                    LocalDate actualEnd = ScheduleTimingUtil.completionDate(
                            facts.planningAnchorDate(),
                            op.getStartMinute(),
                            op.getDurationMinutes());
                    int penalty = 0;
                    if (op.getMpContractStartDate() != null && actualStart != null) {
                        penalty += facts.contractSettings().masterPlanTargetPenalty(
                                op.getMpContractStartDate(), actualStart);
                    }
                    if (op.getMpContractEndDate() != null) {
                        penalty += facts.contractSettings().masterPlanTargetPenalty(
                                op.getMpContractEndDate(), actualEnd);
                    } else if (op.getMpTargetEndDate() != null) {
                        penalty += facts.contractSettings().masterPlanTargetPenalty(
                                op.getMpTargetEndDate(), actualEnd);
                    }
                    return penalty;
                })
                .asConstraint("Minimize master plan target deviation");
    }

    /** 契约模式：优先落在主计划契约资源上（Soft，可违约）。 */
    private Constraint preferMasterPlanContractResource(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .join(DetailScheduleProblemFacts.class)
                .filter((op, facts) -> op.getLine() != null
                        && op.getMpContractResourceId() != null
                        && !op.getMpContractResourceId().isBlank()
                        && !op.getMpContractResourceId().equals(op.getLine().getResourceId()))
                .penalize(HardSoftScore.ONE_SOFT, (op, facts) ->
                        Math.max(1, facts.contractSettings().weightMasterPlanLate()))
                .asConstraint("Prefer master plan contract resource");
    }

    private static final int RESOURCE_PRIORITY_FALLBACK_WEIGHT = 50;

    /** 同工序多资源：优先占用 allowedResourceIds 中排位靠前的资源对应产线。 */
    private Constraint preferHigherPriorityResource(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(op -> op.getLine() != null
                        && op.getAllowedResourceIds() != null
                        && op.getAllowedResourceIds().size() > 1)
                .penalize(HardSoftScore.ONE_SOFT, op -> {
                    int idx = op.getAllowedResourceIds().indexOf(op.getLine().getResourceId());
                    return idx <= 0 ? 0 : idx * RESOURCE_PRIORITY_FALLBACK_WEIGHT;
                })
                .asConstraint("Prefer higher priority resource");
    }

    /**
     * 同产线链上相邻任务的产品换型成本（Soft）。
     */
    private Constraint minimizeProductChangeover(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(op -> op.getLine() != null && op.getPreviousOnLine() != null)
                .join(DetailScheduleProblemFacts.class)
                .filter((later, facts) -> later.getProductCode() != null
                        && later.getPreviousOnLine().getProductCode() != null
                        && later.getOperationName() != null)
                .penalize(HardSoftScore.ONE_SOFT, (later, facts) ->
                        facts.changeoverRules().computeMinutes(
                                later.getOperationName(),
                                later.getResourceId(),
                                later.getOperationSeq(),
                                later.getPreviousOnLine().getProductCode(),
                                later.getProductCode()))
                .asConstraint("Minimize product changeover");
    }

    /**
     * 工序衔接（Hard）：沿 {@link OperationAssignment#getRoutingPredecessor()} 工艺链生效；
     * 间隔与模式由生产规则 · 工序流转时间配置。
     */
    private Constraint routingPrecedence(ConstraintFactory factory) {
        return factory.forEach(OperationAssignment.class)
                .filter(succ -> succ.getLine() != null
                        && succ.getRoutingPredecessor() != null
                        && succ.getStartMinute() != null)
                .join(DetailScheduleProblemFacts.class)
                .penalize(HardSoftScore.ONE_HARD, (succ, facts) ->
                        LineChainTimingUtil.routingPrecedenceViolationMinutes(
                                succ, facts.transferRules()))
                .asConstraint("Routing precedence");
    }
}
