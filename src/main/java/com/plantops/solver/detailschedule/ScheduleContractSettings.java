package com.plantops.solver.detailschedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 排程契约软约束权重与早/晚惩罚公式（L1 交期、L2 主计划目标）。
 */
public class ScheduleContractSettings {

    private final int weightDue;
    private final int weightMasterPlanLate;
    private final int weightMasterPlanEarly;
    private final ScheduleContractPenaltyMode masterPlanLateMode;
    private final ScheduleContractPenaltyMode masterPlanEarlyMode;
    private final int masterPlanEarlyCapDays;
    private final boolean masterPlanTargetSoftConstraintEnabled;
    /** 为 true 时工序不得早于主计划契约开始日（mpContractStartDate）开工。 */
    private final boolean masterPlanContractStartWaitEnabled;

    public ScheduleContractSettings(
            int weightDue,
            int weightMasterPlanLate,
            int weightMasterPlanEarly,
            ScheduleContractPenaltyMode masterPlanLateMode,
            ScheduleContractPenaltyMode masterPlanEarlyMode,
            int masterPlanEarlyCapDays,
            boolean masterPlanTargetSoftConstraintEnabled,
            boolean masterPlanContractStartWaitEnabled) {
        this.weightDue = Math.max(0, weightDue);
        this.weightMasterPlanLate = Math.max(0, weightMasterPlanLate);
        this.weightMasterPlanEarly = Math.max(0, weightMasterPlanEarly);
        this.masterPlanLateMode = masterPlanLateMode != null ? masterPlanLateMode : ScheduleContractPenaltyMode.LINEAR;
        this.masterPlanEarlyMode = masterPlanEarlyMode != null ? masterPlanEarlyMode : ScheduleContractPenaltyMode.LINEAR;
        this.masterPlanEarlyCapDays = Math.max(0, masterPlanEarlyCapDays);
        this.masterPlanTargetSoftConstraintEnabled = masterPlanTargetSoftConstraintEnabled;
        this.masterPlanContractStartWaitEnabled = masterPlanContractStartWaitEnabled;
    }

    public static ScheduleContractSettings defaults() {
        return new ScheduleContractSettings(
                100,
                20,
                60,
                ScheduleContractPenaltyMode.LINEAR,
                ScheduleContractPenaltyMode.QUADRATIC,
                0,
                false,
                true);
    }

    public boolean masterPlanTargetSoftConstraintEnabled() {
        return masterPlanTargetSoftConstraintEnabled;
    }

    public boolean masterPlanContractStartWaitEnabled() {
        return masterPlanContractStartWaitEnabled;
    }

    /**
     * 主计划契约开始日对应的最早开工分钟（相对排程锚点）；关闭等待时返回 0。
     */
    public int contractStartMinuteFloor(OperationAssignment op, LocalDate planningAnchorDate) {
        if (!masterPlanContractStartWaitEnabled
                || op == null
                || op.getMpContractStartDate() == null
                || planningAnchorDate == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(planningAnchorDate, op.getMpContractStartDate());
        if (days <= 0) {
            return 0;
        }
        return Math.toIntExact(Math.min((long) Integer.MAX_VALUE, days * ScheduleTimingUtil.MINUTES_PER_DAY));
    }

    public int weightDue() {
        return weightDue;
    }

    public int weightMasterPlanLate() {
        return weightMasterPlanLate;
    }

    public int weightMasterPlanEarly() {
        return weightMasterPlanEarly;
    }

    /**
     * L1：完成日晚于交期时惩罚（按天，线性）。
     */
    public int dueDatePenalty(LocalDate dueDate, LocalDate actualEndDate) {
        if (dueDate == null || actualEndDate == null || weightDue == 0) {
            return 0;
        }
        long daysLate = ChronoUnit.DAYS.between(dueDate, actualEndDate);
        if (daysLate <= 0) {
            return 0;
        }
        return (int) (weightDue * daysLate);
    }

    /**
     * L2：相对主计划目标完成日的偏差惩罚（早/晚公式与权重不同）。
     */
    public int masterPlanTargetPenalty(LocalDate mpTargetEndDate, LocalDate actualEndDate) {
        if (mpTargetEndDate == null || actualEndDate == null) {
            return 0;
        }
        long deltaDays = ChronoUnit.DAYS.between(mpTargetEndDate, actualEndDate);
        if (deltaDays > 0) {
            return latePenalty((int) deltaDays);
        }
        if (deltaDays < 0) {
            return earlyPenalty((int) -deltaDays);
        }
        return 0;
    }

    private int latePenalty(int daysLate) {
        if (weightMasterPlanLate == 0 || daysLate <= 0) {
            return 0;
        }
        return switch (masterPlanLateMode) {
            case QUADRATIC -> weightMasterPlanLate * daysLate * daysLate;
            case LINEAR, CAPPED -> weightMasterPlanLate * daysLate;
        };
    }

    private int earlyPenalty(int daysEarly) {
        if (weightMasterPlanEarly == 0 || daysEarly <= 0) {
            return 0;
        }
        int effectiveDays = daysEarly;
        if (masterPlanEarlyMode == ScheduleContractPenaltyMode.CAPPED && masterPlanEarlyCapDays > 0) {
            effectiveDays = Math.min(daysEarly, masterPlanEarlyCapDays);
        }
        return switch (masterPlanEarlyMode) {
            case QUADRATIC -> weightMasterPlanEarly * effectiveDays * effectiveDays;
            case LINEAR, CAPPED -> weightMasterPlanEarly * effectiveDays;
        };
    }
}
