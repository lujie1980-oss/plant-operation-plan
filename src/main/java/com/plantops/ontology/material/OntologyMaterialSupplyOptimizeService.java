package com.plantops.ontology.material;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.master.MasterPlanRoutingProjector;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** SCN-07d：在多条 ENT-RT 间按 EAT/延期/工时选优（scoped 子问题 · RULE-MRP-03）。 */
@ApplicationScoped
public class OntologyMaterialSupplyOptimizeService {

    @Inject
    MasterPlanRoutingProjector routingProjector;

    @Inject
    OntologyMaterialSupplyRoutingService routingService;

    public OptimizeSelection selectOptimalRouting(
            OntologyGraph graph,
            String pispId,
            double quantity,
            LocalDate needDate,
            String periodFrom,
            String periodTo) {
        ProductInStockingPoint pisp = OntologyMaterialSupplyRoutingService.requirePisp(graph, pispId);
        LocalDate anchorDate = OntologyMaterialSupplyRoutingService.resolveRangeEndDate(graph, periodFrom, periodTo);
        LocalDate effectiveNeedDate = needDate != null ? needDate : anchorDate;
        double effectiveQty = quantity > 0 ? quantity : 1.0;

        List<RoutingDto> routings = routingProjector.listRoutingsForPisp(pispId, pisp.getProductCode()).stream()
                .sorted(Comparator.comparingInt(RoutingDto::pathPriority))
                .toList();
        if (routings.isEmpty()) {
            throw new BadRequestException("PISP 无可用工艺路径，请先维护主数据（SCN-T04）");
        }

        List<ScoredRouting> scored = new ArrayList<>();
        for (RoutingDto routing : routings) {
            if (routingProjector
                    .projectRoutingSteps(pispId, pisp.getProductCode(), routing.pathPriority())
                    .isEmpty()) {
                continue;
            }
            int durationMinutes = ProductRoutingSteps.totalDurationMinutes(
                    pisp.getProductCode(), BigDecimal.valueOf(effectiveQty), routing.pathPriority());
            LocalDateTime eat = routingService.estimateEarliestAchievableTime(
                    pisp.getProductCode(), effectiveQty, anchorDate, routing.pathPriority());
            long latenessMinutes = latenessMinutes(eat, effectiveNeedDate);
            scored.add(new ScoredRouting(routing, eat, latenessMinutes, durationMinutes));
        }

        if (scored.isEmpty()) {
            throw new BadRequestException("所有路径均不可行：工艺步骤缺失（SCN-07d-E1）");
        }

        ScoredRouting best = scored.stream()
                .min(Comparator
                        .comparingLong(ScoredRouting::latenessMinutes)
                        .thenComparingInt(ScoredRouting::durationMinutes)
                        .thenComparingInt(s -> s.routing().pathPriority()))
                .orElseThrow();

        String summary = "routingId="
                + best.routing().id()
                + ", latenessMin="
                + best.latenessMinutes()
                + ", durationMin="
                + best.durationMinutes()
                + ", eat="
                + best.eat();
        return new OptimizeSelection(best.routing(), best.eat(), summary);
    }

    private static long latenessMinutes(LocalDateTime eat, LocalDate needDate) {
        if (eat == null || needDate == null) {
            return Long.MAX_VALUE;
        }
        LocalDateTime deadline = needDate.atTime(OntologyMaterialSupplyRoutingService.DEFAULT_SHIFT_END);
        if (!eat.isAfter(deadline)) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(deadline, eat);
    }

    record ScoredRouting(RoutingDto routing, LocalDateTime eat, long latenessMinutes, int durationMinutes) {}

    public record OptimizeSelection(RoutingDto routing, LocalDateTime eat, String scoreSummary) {}
}
