package com.plantops.scenario.planning.simulation.timing;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.ResourceWorkingCalendarIndex;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.TimingRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FactoryCalendarTimingRule implements TimingRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.FACTORY_CALENDAR;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public int snapStartMinute(
            SimulationRuleContext ctx,
            OperationAssignment op,
            ScheduleLine line,
            int tentativeStart) {
        ResourceWorkingCalendarIndex calendar = calendar(ctx);
        if (calendar == null) {
            return tentativeStart;
        }
        String resourceId = resolveResourceId(line, op);
        if (!calendar.hasCalendar(resourceId)) {
            return tentativeStart;
        }
        return calendar.snapForward(resourceId, tentativeStart);
    }

    static ResourceWorkingCalendarIndex calendar(SimulationRuleContext ctx) {
        return ctx.facts() != null ? ctx.facts().workingCalendar() : null;
    }

    static String resolveResourceId(ScheduleLine line, OperationAssignment op) {
        if (line != null && line.getResourceId() != null && !line.getResourceId().isBlank()) {
            return line.getResourceId();
        }
        if (line != null && line.getLineId() != null) {
            return line.getLineId();
        }
        return op != null ? op.getResourceId() : null;
    }
}
