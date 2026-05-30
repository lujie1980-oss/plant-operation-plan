package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.ArrayList;
import java.util.List;
@PlanningSolution
public class DetailSchedule {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "lineRange")
    private List<ScheduleLine> lineRange;

    @PlanningEntityCollectionProperty
    private List<OperationAssignment> operations;

    private int shiftCapacityMinutes = 480;

    @ProblemFactProperty
    private DetailScheduleProblemFacts problemFacts;

    @PlanningScore
    private HardSoftScore score;

    public DetailSchedule() {
    }

    public List<ScheduleLine> getLineRange() {
        return lineRange;
    }

    public void setLineRange(List<ScheduleLine> lineRange) {
        this.lineRange = lineRange;
    }

    public List<OperationAssignment> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationAssignment> operations) {
        this.operations = operations;
    }

    public int getShiftCapacityMinutes() {
        return shiftCapacityMinutes;
    }

    public void setShiftCapacityMinutes(int shiftCapacityMinutes) {
        this.shiftCapacityMinutes = shiftCapacityMinutes;
    }

    public DetailScheduleProblemFacts getProblemFacts() {
        return problemFacts;
    }

    public void setProblemFacts(DetailScheduleProblemFacts problemFacts) {
        this.problemFacts = problemFacts;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public static DetailSchedule empty() {
        DetailSchedule s = new DetailSchedule();
        s.lineRange = new ArrayList<>();
        s.operations = new ArrayList<>();
        return s;
    }
}
