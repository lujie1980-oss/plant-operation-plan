package com.plantops.solver.detailschedule;



import com.plantops.scenario.OperationTransferTimeIndex;

import com.plantops.scenario.planning.simulation.DetailScheduleTimingKernel;

import com.plantops.scenario.planning.simulation.timing.RoutingChainTimingRule;

import io.quarkus.arc.Arc;



import java.util.Map;



/**

 * 产线 list 顺序 + 工艺链衔接规则 → 影子开工时间（Timefold list-variable 链式时间模型）。

 * Phase 1：赋时委托 {@link DetailScheduleTimingKernel}；工艺链静态方法委托 {@link RoutingChainTimingRule}。

 */

public final class LineChainTimingUtil {



    public static final int MINUTES_PER_DAY = ScheduleTimingUtil.MINUTES_PER_DAY;



    private LineChainTimingUtil() {

    }



    public static void applyAllStartTimes(DetailSchedule schedule) {

        timingKernel().applyAllStartTimes(schedule);

    }



    public static Map<String, Integer> changeoverMinutesBeforeByOperationId(DetailSchedule schedule) {

        return timingKernel().changeoverMinutesBeforeByOperationId(schedule);

    }



    static void clampAssignedStartsToRoutingChain(

            DetailSchedule schedule,

            OperationTransferTimeIndex transferRules) {

        RoutingChainTimingRule.clampAssignedStartsToRoutingChain(schedule, transferRules);

    }



    static boolean bumpEarliestFromRoutingPredecessors(

            DetailSchedule schedule,

            OperationTransferTimeIndex transferRules) {

        return RoutingChainTimingRule.bumpEarliestFromRoutingPredecessors(schedule, transferRules);

    }



    /** @deprecated 使用 {@link #bumpEarliestFromRoutingPredecessors} */

    @Deprecated

    static boolean bumpEarliestFromRoutingChain(

            DetailSchedule schedule,

            OperationTransferTimeIndex transferRules,

            Map<String, OperationAssignment> byOperationId) {

        return bumpEarliestFromRoutingPredecessors(schedule, transferRules);

    }



    public static int routingPrecedenceViolationMinutes(

            OperationAssignment succ,

            OperationTransferTimeIndex transferRules) {

        return RoutingChainTimingRule.routingPrecedenceViolationMinutes(succ, transferRules);

    }



    public static Integer minimumStartRespectingRoutingChain(

            OperationAssignment succ,

            OperationTransferTimeIndex transferRules) {

        return RoutingChainTimingRule.minimumStartRespectingRoutingChain(succ, transferRules);

    }



    private static DetailScheduleTimingKernel timingKernel() {

        return Arc.container().instance(DetailScheduleTimingKernel.class).get();

    }

}


