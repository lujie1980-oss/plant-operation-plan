package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;

import java.time.LocalDate;

/** 排程求解问题事实：契约权重 + 时间锚点（用于完成日推算）。 */
public class DetailScheduleProblemFacts {

    private final ScheduleContractSettings contractSettings;
    private final LocalDate planningAnchorDate;
    private final int shiftCapacityMinutes;
    private final ChangeoverRuleIndex changeoverRules;

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes) {
        this(contractSettings, planningAnchorDate, shiftCapacityMinutes, ChangeoverRuleIndex.fromWorkspace());
    }

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes,
            ChangeoverRuleIndex changeoverRules) {
        this.contractSettings = contractSettings != null ? contractSettings : ScheduleContractSettings.defaults();
        this.planningAnchorDate = planningAnchorDate != null ? planningAnchorDate : LocalDate.now();
        this.shiftCapacityMinutes = Math.max(1, shiftCapacityMinutes);
        this.changeoverRules = changeoverRules != null ? changeoverRules : ChangeoverRuleIndex.fromWorkspace();
    }

    public ScheduleContractSettings contractSettings() {
        return contractSettings;
    }

    public LocalDate planningAnchorDate() {
        return planningAnchorDate;
    }

    public int shiftCapacityMinutes() {
        return shiftCapacityMinutes;
    }

    public ChangeoverRuleIndex changeoverRules() {
        return changeoverRules;
    }
}
