package com.plantops.solver.slitting;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.plantops.config.SolverRuntimeFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SlittingConstraintProviderTest {

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    @Test
    void boundaryOverflow_penalizesWhenChildExceedsParent() {
        RollNode parent = new RollNode("P1", RollType.INTERMEDIATE, new Dimensions(1000, 2000, 0));
        RollNode child = new RollNode("C1", RollType.CHILD, new Dimensions(400, 500, 0));
        NestAssignment assignment = new NestAssignment("A1", child);
        assignment.setParentNode(parent);
        assignment.setPositionX(700);
        assignment.setPositionY(0);
        assignment.setRotated(Boolean.FALSE);

        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setContainers(List.of(parent));
        solution.setAssignments(List.of(assignment));

        HardSoftScore score = score(solution);
        assertTrue(score.hardScore() < 0, "expected hard penalty, score=" + score);
    }

    @Test
    void feasiblePlacement_hasNoHardPenalty() {
        RollNode parent = new RollNode("P1", RollType.INTERMEDIATE, new Dimensions(1000, 2000, 0));
        RollNode child = new RollNode("C1", RollType.CHILD, new Dimensions(400, 500, 0));
        NestAssignment assignment = new NestAssignment("A1", child);
        assignment.setParentNode(parent);
        assignment.setPositionX(0);
        assignment.setPositionY(0);
        assignment.setRotated(Boolean.FALSE);

        SlittingNestSolution solution = new SlittingNestSolution();
        solution.setContainers(List.of(parent));
        solution.setAssignments(List.of(assignment));

        HardSoftScore score = score(solution);
        assertTrue(score.hardScore() >= 0, "expected no hard penalty, score=" + score);
    }

    private HardSoftScore score(SlittingNestSolution solution) {
        SolutionManager<SlittingNestSolution, HardSoftScore> manager =
                solverRuntimeFactory.createSlittingNestSolutionManager();
        return manager.update(solution);
    }
}
