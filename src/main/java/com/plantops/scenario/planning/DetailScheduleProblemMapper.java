package com.plantops.scenario.planning;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleLineInitializer;
import com.plantops.solver.detailschedule.DetailScheduleProblemFacts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 将 {@link DetailSchedulePlanningContext} 投影为 Timefold {@link DetailSchedule}（只读视图 + 待优化变量）。
 */
@ApplicationScoped
public class DetailScheduleProblemMapper {

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    public DetailSchedule toSchedule(DetailSchedulePlanningContext context) {
        if (context == null) {
            return DetailSchedule.empty();
        }
        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(context.lines());
        schedule.setOperations(context.operations());
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                context.contractSettings(),
                context.planningAnchor(),
                businessRuleScopeService.loadChangeoverIndex(),
                businessRuleScopeService.loadDetailScheduleTransferTimeIndex()));
        DetailScheduleLineInitializer.seedInitialQueues(schedule);
        return schedule;
    }
}
