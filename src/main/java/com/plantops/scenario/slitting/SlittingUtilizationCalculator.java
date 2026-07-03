package com.plantops.scenario.slitting;

import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.RollType;
import com.plantops.solver.slitting.SlittingGeometryUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class SlittingUtilizationCalculator {

    private SlittingUtilizationCalculator() {
    }

    public static BigDecimal computeUtilizationPct(List<RollNode> nodes, List<NestAssignment> assignments) {
        double masterArea = nodes.stream()
                .filter(n -> n.getType() == RollType.MASTER)
                .mapToDouble(n -> n.getDimensions().area())
                .sum();
        if (masterArea <= 0) {
            return BigDecimal.ZERO;
        }
        double childArea = assignments.stream()
                .filter(a -> a.getPlacedNode() != null && a.getPlacedNode().getType() == RollType.CHILD)
                .mapToDouble(a -> SlittingGeometryUtil.effectiveWidth(a.getPlacedNode(), a.isRotated())
                        * SlittingGeometryUtil.effectiveLength(a.getPlacedNode(), a.isRotated()))
                .sum();
        double pct = (childArea / masterArea) * 100.0;
        return BigDecimal.valueOf(pct).setScale(4, RoundingMode.HALF_UP);
    }
}
