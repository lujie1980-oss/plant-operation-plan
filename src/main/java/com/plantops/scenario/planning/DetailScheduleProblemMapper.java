package com.plantops.scenario.planning;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.scenario.FeedbackFreezeIndex;
import com.plantops.scenario.ResourceWorkingCalendarIndex;
import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleLineInitializer;
import com.plantops.solver.detailschedule.DetailScheduleProblemFacts;

import java.time.LocalDate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 将 {@link DetailSchedulePlanningContext} 投影为 Timefold {@link DetailSchedule}（只读视图 + 待优化变量）。
 */
@ApplicationScoped
public class DetailScheduleProblemMapper {

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    public DetailSchedule toSchedule(DetailSchedulePlanningContext context) {
        if (context == null) {
            return DetailSchedule.empty();
        }
        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(context.lines());
        schedule.setOperations(context.operations());
        LocalDate anchor = context.planningAnchor();
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                context.contractSettings(),
                anchor,
                businessRuleScopeService.loadChangeoverIndex(),
                businessRuleScopeService.loadDetailScheduleTransferTimeIndex(),
                ResourceWorkingCalendarIndex.fromWorkspace(anchor, timeslotHorizonService.totalCalendarDays()),
                FeedbackFreezeIndex.empty()));
        DetailScheduleLineInitializer.seedInitialQueues(schedule);
        return schedule;
    }
}
