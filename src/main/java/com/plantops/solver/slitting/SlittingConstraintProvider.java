package com.plantops.solver.slitting;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

public class SlittingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                boundaryOverflow(factory),
                noOverlap(factory),
                childParentType(factory),
                intermediateParentType(factory),
                wasteAreaByDepth(factory),
                nonStandardIntermediatePenalty(factory)
        };
    }

    private Constraint boundaryOverflow(ConstraintFactory factory) {
        return factory.forEach(NestAssignment.class)
                .filter(a -> a.getParentNode() != null && a.getPlacedNode() != null)
                .filter(a -> {
                    double w = SlittingGeometryUtil.effectiveWidth(a.getPlacedNode(), a.isRotated());
                    double h = SlittingGeometryUtil.effectiveLength(a.getPlacedNode(), a.isRotated());
                    int px = a.getPositionX() != null ? a.getPositionX() : 0;
                    int py = a.getPositionY() != null ? a.getPositionY() : 0;
                    return px + w > a.getParentNode().getDimensions().widthMm()
                            || py + h > a.getParentNode().getDimensions().lengthMm();
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Boundary overflow");
    }

    private Constraint noOverlap(ConstraintFactory factory) {
        return factory.forEachUniquePair(NestAssignment.class, Joiners.equal(NestAssignment::getParentNode))
                .filter((a, b) -> {
                    if (a.getParentNode() == null) {
                        return false;
                    }
                    double kerfW = SlittingGeometryUtil.kerfWidthMm(a.getParentNode());
                    double kerfL = SlittingGeometryUtil.kerfLengthMm(a.getParentNode());
                    return SlittingGeometryUtil.violatesKerfClearance(a, b, kerfW, kerfL);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No overlap");
    }

    private Constraint childParentType(ConstraintFactory factory) {
        return factory.forEach(NestAssignment.class)
                .filter(a -> a.getPlacedNode() != null && a.getPlacedNode().getType() == RollType.CHILD)
                .filter(a -> a.getParentNode() == null || a.getParentNode().getType() != RollType.INTERMEDIATE)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Child must hang on intermediate");
    }

    private Constraint intermediateParentType(ConstraintFactory factory) {
        return factory.forEach(NestAssignment.class)
                .filter(a -> a.getPlacedNode() != null && a.getPlacedNode().getType() == RollType.INTERMEDIATE)
                .filter(a -> a.getParentNode() == null || a.getParentNode().getType() != RollType.MASTER)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Intermediate must hang on master");
    }

    private Constraint wasteAreaByDepth(ConstraintFactory factory) {
        return factory.forEach(NestAssignment.class)
                .filter(a -> a.getParentNode() != null)
                .penalize(HardSoftScore.ONE_SOFT, a -> {
                    int depthWeight = a.getParentNode().getType() == RollType.MASTER ? 10 : 1;
                    int px = a.getPositionX() != null ? a.getPositionX() : 0;
                    int py = a.getPositionY() != null ? a.getPositionY() : 0;
                    return depthWeight * (px + py);
                })
                .asConstraint("Compact placement by depth");
    }

    private Constraint nonStandardIntermediatePenalty(ConstraintFactory factory) {
        return factory.forEach(NestAssignment.class)
                .filter(a -> a.getPlacedNode() != null && a.getPlacedNode().getType() == RollType.INTERMEDIATE)
                .filter(a -> a.getPlacedNode().getSourceSpecCode() == null)
                .penalize(HardSoftScore.ONE_SOFT, a -> 100)
                .asConstraint("Non-standard intermediate");
    }
}
