package com.plantops.solver.detailschedule;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
public class DetailSchedule {

    /** 产线规划实体（每条产线持有有序工序 list）。 */
    @PlanningEntityCollectionProperty
    private List<ScheduleLine> lines;

    @ValueRangeProvider(id = "operationRange")
    @PlanningEntityCollectionProperty
    private List<OperationAssignment> operations;

    @ProblemFactProperty
    private DetailScheduleProblemFacts problemFacts;

    @PlanningScore
    private HardSoftScore score;

    public DetailSchedule() {
    }

    public List<ScheduleLine> getLines() {
        return lines;
    }

    public void setLines(List<ScheduleLine> lines) {
        this.lines = lines;
    }

    /** @deprecated 使用 {@link #getLines()} */
    @Deprecated
    public List<ScheduleLine> getLineRange() {
        return lines;
    }

    /** @deprecated 使用 {@link #setLines(List)} */
    @Deprecated
    public void setLineRange(List<ScheduleLine> lineRange) {
        this.lines = lineRange;
    }

    public List<OperationAssignment> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationAssignment> operations) {
        this.operations = operations;
    }

    public DetailScheduleProblemFacts getProblemFacts() {
        return problemFacts;
    }

    public void setProblemFacts(DetailScheduleProblemFacts problemFacts) {
        this.problemFacts = problemFacts;
    }

    public HardSoftScore score() {
        return score;
    }

    /** Timefold 2.0 要求 {@link ai.timefold.solver.core.api.domain.solution.PlanningScore} 具备 public getter。 */
    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public static DetailSchedule empty() {
        DetailSchedule s = new DetailSchedule();
        s.lines = new ArrayList<>();
        s.operations = new ArrayList<>();
        return s;
    }
}
