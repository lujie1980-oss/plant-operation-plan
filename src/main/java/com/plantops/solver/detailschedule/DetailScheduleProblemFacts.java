package com.plantops.solver.detailschedule;

import com.plantops.scenario.FeedbackFreezeIndex;
import com.plantops.scenario.ResourceWorkingCalendarIndex;
import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;

import java.time.LocalDate;
import java.util.Set;

/** 排程求解问题事实：契约权重 + 时间锚点 + 换型/工序流转规则 + Phase 3 日历/冻结快照。 */
public class DetailScheduleProblemFacts {

    private final ScheduleContractSettings contractSettings;
    private final LocalDate planningAnchorDate;
    private final ChangeoverRuleIndex changeoverRules;
    private final OperationTransferTimeIndex transferRules;
    private final ResourceWorkingCalendarIndex workingCalendar;
    private final FeedbackFreezeIndex feedbackFreeze;
    /**
     * 请求线程预加载的详细排程启用规则项快照；求解线程（Timefold 影子变量）据此判断规则启用，
     * 避免在 SolverManager 工作线程上触发 JPA（无 CDI 请求上下文/事务）。{@code null} 表示未预加载，
     * 回退到 {@code BusinessRuleScopeService} 实时查询（仅请求线程安全）。
     */
    private final Set<String> detailScheduleEnabledRuleTypes;

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
        this(contractSettings, planningAnchorDate, changeoverRules, transferRules, null, null);
    }

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules,
            ResourceWorkingCalendarIndex workingCalendar,
            FeedbackFreezeIndex feedbackFreeze) {
        this(contractSettings, planningAnchorDate, changeoverRules, transferRules, workingCalendar, feedbackFreeze, null);
    }

    public DetailScheduleProblemFacts(
            ScheduleContractSettings contractSettings,
            LocalDate planningAnchorDate,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules,
            ResourceWorkingCalendarIndex workingCalendar,
            FeedbackFreezeIndex feedbackFreeze,
            Set<String> detailScheduleEnabledRuleTypes) {
        this.contractSettings = contractSettings != null ? contractSettings : ScheduleContractSettings.defaults();
        this.planningAnchorDate = planningAnchorDate != null ? planningAnchorDate : LocalDate.now();
        this.changeoverRules = changeoverRules != null ? changeoverRules : ChangeoverRuleIndex.fromWorkspace();
        this.transferRules = transferRules != null ? transferRules : OperationTransferTimeIndex.fromWorkspace();
        this.workingCalendar = workingCalendar != null ? workingCalendar : ResourceWorkingCalendarIndex.empty();
        this.feedbackFreeze = feedbackFreeze != null ? feedbackFreeze : FeedbackFreezeIndex.empty();
        this.detailScheduleEnabledRuleTypes = detailScheduleEnabledRuleTypes != null
                ? Set.copyOf(detailScheduleEnabledRuleTypes)
                : null;
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

    public ResourceWorkingCalendarIndex workingCalendar() {
        return workingCalendar;
    }

    public FeedbackFreezeIndex feedbackFreeze() {
        return feedbackFreeze;
    }

    /**
     * @return 预加载的详细排程启用规则项；{@code null} 表示未预加载（回退实时查询）。
     */
    public Set<String> detailScheduleEnabledRuleTypes() {
        return detailScheduleEnabledRuleTypes;
    }
}
