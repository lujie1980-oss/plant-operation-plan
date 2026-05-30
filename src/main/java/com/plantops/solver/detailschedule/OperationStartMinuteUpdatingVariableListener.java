package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;

public class OperationStartMinuteUpdatingVariableListener
        implements VariableListener<DetailSchedule, OperationAssignment> {

    @Override
    public void beforeEntityAdded(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        // no-op
    }

    @Override
    public void afterEntityAdded(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        updateStartMinutes(scoreDirector);
    }

    @Override
    public void beforeVariableChanged(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        // no-op
    }

    @Override
    public void afterVariableChanged(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        updateStartMinutes(scoreDirector);
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        // no-op
    }

    @Override
    public void afterEntityRemoved(ScoreDirector<DetailSchedule> scoreDirector, OperationAssignment operation) {
        updateStartMinutes(scoreDirector);
    }

    private void updateStartMinutes(ScoreDirector<DetailSchedule> scoreDirector) {
        DetailSchedule schedule = scoreDirector.getWorkingSolution();
        for (OperationAssignment op : schedule.getOperations()) {
            scoreDirector.beforeVariableChanged(op, "startMinute");
        }
        ScheduleTimingUtil.applyLineStartTimes(schedule);
        for (OperationAssignment op : schedule.getOperations()) {
            scoreDirector.afterVariableChanged(op, "startMinute");
        }
    }
}
