package com.plantops.solver.slitting;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
public class SlittingNestSolution {

    @ProblemFactProperty
    private SlittingProblemFacts problemFacts = new SlittingProblemFacts();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "containerRange")
    private List<RollNode> containers = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<NestAssignment> assignments = new ArrayList<>();

    @PlanningScore
    private HardSoftScore score;

    @ValueRangeProvider(id = "rotatedRange")
    public List<Boolean> getRotatedRange() {
        return List.of(Boolean.FALSE, Boolean.TRUE);
    }

    @ValueRangeProvider(id = "positionRange")
    public List<Integer> getPositionRange() {
        int max = problemFacts != null ? problemFacts.getMaxPositionMm() : 10000;
        List<Integer> range = new ArrayList<>(max / 10 + 1);
        for (int i = 0; i <= max; i += 10) {
            range.add(i);
        }
        return range;
    }

    public SlittingProblemFacts getProblemFacts() {
        return problemFacts;
    }

    public void setProblemFacts(SlittingProblemFacts problemFacts) {
        this.problemFacts = problemFacts;
    }

    public List<RollNode> getContainers() {
        return containers;
    }

    public void setContainers(List<RollNode> containers) {
        this.containers = containers;
    }

    public List<NestAssignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<NestAssignment> assignments) {
        this.assignments = assignments;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
