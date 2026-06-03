package com.plantops.scenario.planning;



import com.plantops.scenario.planning.simulation.SimulationClosureExpander;

import com.plantops.scenario.planning.simulation.SimulationPipeline;
import io.quarkus.arc.Arc;

import com.plantops.scenario.planning.simulation.SimulationRuleContextFactory;

import com.plantops.solver.detailschedule.DetailSchedule;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;



import java.util.Collection;

import java.util.List;

import java.util.Set;



@ApplicationScoped

public class DetailScheduleSimulationEngine {



    /** @deprecated 使用 {@link SimulationMode} */

    public enum SimulationMode {

        FULL,

        INCREMENTAL

    }



    public record SimulationResult(

            SimulationMode mode,

            long durationMs,

            List<String> recalculatedOperationIds,

            List<ScheduleConstraintViolation> violations) {

    }



    @Inject

    SimulationPipeline pipeline;



    @Inject

    ScheduleValidationService validationService;



    public SimulationResult fullSimulate(DetailSchedule schedule) {

        SimulationPipeline.SimulationResult result = pipeline.fullSimulate(schedule);

        return toEngineResult(result);

    }



    public SimulationResult incrementalSimulate(DetailSchedule schedule, Collection<String> seedOperationIds) {

        SimulationPipeline.SimulationResult result = pipeline.incrementalSimulate(schedule, seedOperationIds);

        return toEngineResult(result);

    }



    public List<ScheduleConstraintViolation> validate(DetailSchedule schedule) {

        return validationService.validate(schedule);

    }



    static Set<String> expandAffectedClosure(DetailSchedule schedule, Collection<String> seedOperationIds) {

        var ctx = SimulationRuleContextFactory.from(

                schedule, com.plantops.scenario.planning.simulation.SimulationMode.INCREMENTAL, Set.of());

        return Arc.container().instance(SimulationClosureExpander.class).get()

                .expand(ctx, seedOperationIds);

    }



    private static SimulationResult toEngineResult(SimulationPipeline.SimulationResult result) {

        SimulationMode mode = result.mode() == com.plantops.scenario.planning.simulation.SimulationMode.FULL

                ? SimulationMode.FULL

                : SimulationMode.INCREMENTAL;

        return new SimulationResult(

                mode,

                result.durationMs(),

                result.recalculatedOperationIds(),

                result.violations());

    }

}


