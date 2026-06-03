package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;

import java.time.LocalDate;

/** 排程求解问题事实：契约权重 + 时间锚点 + 换型/工序流转规则。 */
public class DetailScheduleProblemFacts {

    private final ScheduleContractSettings contractSettings;
    private final LocalDate planningAnchorDate;
    private final ChangeoverRuleIndex changeoverRules;
    private final OperationTransferTimeIndex transferRules;

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate) {
        this(contractSettings, planningAnchorDate, ChangeoverRuleIndex.fromWorkspace(), null);
    }

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            ChangeoverRuleIndex changeoverRules) {
        this(contractSettings, planningAnchorDate, changeoverRules, null);
    }

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules) {
        this.contractSettings = contractSettings != null ? contractSettings : ScheduleContractSettings.defaults();
        this.planningAnchorDate = planningAnchorDate != null ? planningAnchorDate : LocalDate.now();
        this.changeoverRules = changeoverRules != null ? changeoverRules : ChangeoverRuleIndex.fromWorkspace();
        this.transferRules = transferRules != null ? transferRules : OperationTransferTimeIndex.fromWorkspace();
    }

    public ScheduleContractSettings contractSettings() {
        return contractSettings;
    }

    public LocalDate planningAnchorDate() {
        return planningAnchorDate;
    }

    public ChangeoverRuleIndex changeoverRules() {
        return changeoverRules;
    }

    public OperationTransferTimeIndex transferRules() {
        return transferRules;
    }
}
