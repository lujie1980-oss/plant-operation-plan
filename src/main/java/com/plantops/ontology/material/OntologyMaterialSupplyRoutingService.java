package com.plantops.ontology.material;

import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyRoutingCandidateDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyRoutingStepSummaryDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepDetailDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.master.MasterPlanRoutingProjector;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OntologyMaterialSupplyRoutingService {

    private static final LocalTime DEFAULT_SHIFT_END = LocalTime.of(17, 0);

    @Inject
    MasterPlanRoutingProjector routingProjector;

    public List<SupplyRoutingCandidateDto> listRoutingCandidates(
            OntologyGraph graph,
            String pispId,
            String periodFrom,
            String periodTo,
            double quantity) {
        ProductInStockingPoint pisp = requirePisp(graph, pispId);
        LocalDate anchorDate = resolveRangeEndDate(graph, periodFrom, periodTo);
        double effectiveQty = quantity > 0 ? quantity : 1.0;

        List<RoutingDto> routings = routingProjector.listRoutingsForPisp(pispId, pisp.getProductCode()).stream()
                .sorted(Comparator.comparingInt(RoutingDto::pathPriority))
                .toList();
        if (routings.isEmpty()) {
            return List.of();
        }

        List<SupplyRoutingCandidateDto> candidates = new ArrayList<>(routings.size());
        for (RoutingDto routing : routings) {
            List<RoutingStepDetailDto> steps =
                    routingProjector.projectRoutingSteps(pispId, pisp.getProductCode());
            if (steps.isEmpty()) {
                continue;
            }
            List<SupplyRoutingStepSummaryDto> stepSummaries = steps.stream()
                    .map(step -> new SupplyRoutingStepSummaryDto(
                            step.sequenceNo(),
                            step.operationName(),
                            step.standardResources().isEmpty()
                                    ? null
                                    : step.standardResources().get(0).standardResourceId()))
                    .toList();
            LocalDateTime eat = estimateEarliestAchievableTime(
                    pisp.getProductCode(), effectiveQty, anchorDate);
            candidates.add(new SupplyRoutingCandidateDto(
                    routing.id(),
                    routing.pathPriority(),
                    routing.routingName(),
                    routing.stepCount(),
                    stepSummaries,
                    eat));
        }
        return candidates;
    }

    public RoutingDto selectRouting(
            OntologyGraph graph,
            String pispId,
            String mode,
            String routingId) {
        ProductInStockingPoint pisp = requirePisp(graph, pispId);
        List<RoutingDto> routings = routingProjector.listRoutingsForPisp(pispId, pisp.getProductCode()).stream()
                .filter(r -> !routingProjector.projectRoutingSteps(pispId, pisp.getProductCode()).isEmpty())
                .sorted(Comparator.comparingInt(RoutingDto::pathPriority))
                .toList();
        if (routings.isEmpty()) {
            throw new BadRequestException("PISP 无可用工艺路径，请先维护主数据（SCN-T04）");
        }
        if ("MANUAL".equalsIgnoreCase(mode)) {
            if (routingId == null || routingId.isBlank()) {
                throw new BadRequestException("MANUAL 模式必须指定 routingId");
            }
            return routings.stream()
                    .filter(r -> routingId.equals(r.id()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("routingId 无效: " + routingId));
        }
        return routings.get(0);
    }

    public LocalDateTime estimateEarliestAchievableTime(
            String productCode, double quantity, LocalDate anchorDate) {
        int minutes = ProductRoutingSteps.totalDurationMinutes(productCode, BigDecimal.valueOf(quantity));
        LocalDateTime finishAnchor = anchorDate.atTime(DEFAULT_SHIFT_END);
        return finishAnchor.minusMinutes(Math.max(minutes, 0));
    }

    static ProductInStockingPoint requirePisp(OntologyGraph graph, String pispId) {
        ProductInStockingPoint pisp = graph.pisp(pispId);
        if (pisp == null) {
            throw new NotFoundException("PISP not found: " + pispId);
        }
        return pisp;
    }

    static LocalDate resolveRangeEndDate(OntologyGraph graph, String periodFrom, String periodTo) {
        Period from = requirePeriod(graph, periodFrom, "periodFrom");
        Period to = requirePeriod(graph, periodTo, "periodTo");
        if (from.getSequenceNr() > to.getSequenceNr()) {
            throw new BadRequestException("periodFrom 不得晚于 periodTo");
        }
        if (to.getEndDate() != null) {
            return to.getEndDate();
        }
        if (from.getEndDate() != null) {
            return from.getEndDate();
        }
        throw new BadRequestException("期间缺少 endDate");
    }

    static Period requirePeriod(OntologyGraph graph, String periodId, String paramName) {
        if (periodId == null || periodId.isBlank()) {
            throw new BadRequestException(paramName + " 必填");
        }
        Period period = graph.periodsOrdered().stream()
                .filter(p -> periodId.equals(p.getId()))
                .findFirst()
                .orElse(null);
        if (period == null) {
            throw new BadRequestException("未知 periodId: " + periodId);
        }
        return period;
    }
}
