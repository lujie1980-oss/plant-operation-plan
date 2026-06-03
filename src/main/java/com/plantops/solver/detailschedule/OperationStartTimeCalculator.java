package com.plantops.solver.detailschedule;

import com.plantops.scenario.planning.simulation.DetailScheduleTimingKernel;
import io.quarkus.arc.Arc;

/** Timefold shadow supplier — 委托 {@link DetailScheduleTimingKernel#computeShadowStartMinute}。 */
final class OperationStartTimeCalculator {

    private OperationStartTimeCalculator() {
    }

    static Integer compute(OperationAssignment op, DetailSchedule schedule) {
        return timingKernel().computeShadowStartMinute(op, schedule);
    }

    private static DetailScheduleTimingKernel timingKernel() {
        return Arc.container().instance(DetailScheduleTimingKernel.class).get();
    }
}
