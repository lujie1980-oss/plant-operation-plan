package com.plantops.scenario.planning.simulation.validation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.solver.detailschedule.OperationAssignment;

final class ValidationSupport {

    private ValidationSupport() {
    }

    static ScheduleConstraintViolation violation(
            ScheduleConstraintViolation.ViolationLevel level,
            String ruleCode,
            OperationAssignment op,
            String message) {
        return new ScheduleConstraintViolation(
                level,
                ruleCode,
                op.getOperationId(),
                op.getLine() != null ? op.getLine().getLineId() : null,
                message);
    }
}
