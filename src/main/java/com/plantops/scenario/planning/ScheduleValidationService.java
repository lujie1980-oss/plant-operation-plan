package com.plantops.scenario.planning;



import com.plantops.api.dto.planning.ScheduleConstraintViolationDto;

import com.plantops.scenario.planning.simulation.SimulationMode;

import com.plantops.scenario.planning.simulation.SimulationRuleContextFactory;

import com.plantops.scenario.planning.simulation.ValidationPipeline;

import com.plantops.solver.detailschedule.DetailSchedule;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;



import java.util.List;

import java.util.Set;



@ApplicationScoped

public class ScheduleValidationService {



    @Inject

    ValidationPipeline validationPipeline;



    public List<ScheduleConstraintViolation> validate(DetailSchedule schedule) {

        var ctx = SimulationRuleContextFactory.from(schedule, SimulationMode.FULL, Set.of());

        return validationPipeline.validate(ctx);

    }



    public List<ScheduleConstraintViolationDto> toDtos(List<ScheduleConstraintViolation> violations) {

        if (violations == null || violations.isEmpty()) {

            return List.of();

        }

        return violations.stream().map(this::toDto).toList();

    }



    public ScheduleConstraintViolationDto toDto(ScheduleConstraintViolation violation) {

        return new ScheduleConstraintViolationDto(

                violation.level().name(),

                violation.ruleCode(),

                violation.operationId(),

                violation.lineId(),

                violation.message());

    }

}


