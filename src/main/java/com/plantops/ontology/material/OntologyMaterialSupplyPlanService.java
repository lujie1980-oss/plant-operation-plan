package com.plantops.ontology.material;

import com.plantops.api.dto.MaterialBalancePeriodDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanRequest;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanResultDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyPlanOrderSummaryDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.persistence.OntologyLegacyMutationCoordinator;
import com.plantops.ontology.scheduling.PispDailyClosingProjection;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MrpExplosionService;
import com.plantops.scenario.WorkOrderService;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OntologyMaterialSupplyPlanService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyMaterialSupplyRoutingService routingService;

    @Inject
    OntologyLegacyMutationCoordinator legacyMutationCoordinator;

    @Transactional
    public CreateSupplyPlanResultDto createSupplyPlan(
            String pispId,
            CreateSupplyPlanRequest request,
            String masterPlanVersionId) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            throw new BadRequestException("mode 必填（AUTO / MANUAL / OPTIMIZE）");
        }
        String mode = request.mode().trim().toUpperCase();
        if ("OPTIMIZE".equals(mode)) {
            throw new BadRequestException("OPTIMIZE 模式尚未实现（SCN-07d）");
        }
        if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
            throw new BadRequestException("不支持的 mode: " + request.mode());
        }

        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        ProductInStockingPoint pisp = OntologyMaterialSupplyRoutingService.requirePisp(graph, pispId);
        List<Period> periods = graph.periodsOrdered();
        List<ProductInStockingPointPeriod> chain =
                PispDailyClosingProjection.chainForPisp(graph, pispId, periods);
        List<ProductInStockingPointPeriod> scoped = periodsInRange(
                chain, periods, request.periodFrom(), request.periodTo());

        double shortageQty = scoped.stream()
                .mapToDouble(ProductInStockingPointPeriod::getStockShortageQuantity)
                .sum();
        double quantity = request.quantity() != null && request.quantity() > 0
                ? request.quantity()
                : shortageQty;
        if (quantity <= 0) {
            throw new BadRequestException("选定区间无需补货（stockShortageQuantity = 0）");
        }

        RoutingDto routing = routingService.selectRouting(graph, pispId, mode, request.routingId());
        LocalDate needDate = request.needDate() != null
                ? request.needDate()
                : OntologyMaterialSupplyRoutingService.resolveRangeEndDate(
                        graph, request.periodFrom(), request.periodTo());

        String workOrderNo = MrpExplosionService.allocateUniqueWorkOrderNo(
                pisp.getProductCode(), needDate, 1);
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = workOrderNo;
        wo.ensureWorkspace();
        wo.salesOrderNo = null;
        wo.salesOrderLineNo = 0;
        wo.productCode = pisp.getProductCode();
        wo.quantity = BigDecimal.valueOf(quantity).setScale(4, RoundingMode.HALF_UP);
        wo.resourceId = resolveResourceId(pisp.getProductCode());
        wo.sequenceNo = WorkOrderEntity.nextSequenceNo();
        wo.parentWorkOrderNo = null;
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        wo.needDate = needDate;
        wo.bomLevel = 0;
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.pendingScheduleEligible = Boolean.TRUE;
        wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_NONE;
        wo.persist();

        legacyMutationCoordinator.afterWorkOrdersChanged(workspaceId);
        authoritativeOntologyGraph.invalidate(workspaceId, masterPlanVersionId);

        OntologyGraph refreshed = authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId);
        ProductInStockingPointPeriod updatedPispp = findLastScopedPispp(refreshed, pispId, periods, request.periodTo());
        MaterialBalancePeriodDto summary = toPeriodDto(updatedPispp);
        LocalDateTime eat = routingService.estimateEarliestAchievableTime(
                pisp.getProductCode(), quantity, needDate);

        return new CreateSupplyPlanResultDto(
                List.of(new SupplyPlanOrderSummaryDto(
                        workOrderNo,
                        pisp.getProductCode(),
                        quantity,
                        needDate)),
                routing.id(),
                eat,
                summary);
    }

    private static List<ProductInStockingPointPeriod> periodsInRange(
            List<ProductInStockingPointPeriod> chain,
            List<Period> periodsOrdered,
            String periodFrom,
            String periodTo) {
        Period from = periodsOrdered.stream()
                .filter(p -> p.getId().equals(periodFrom))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("未知 periodFrom: " + periodFrom));
        Period to = periodsOrdered.stream()
                .filter(p -> p.getId().equals(periodTo))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("未知 periodTo: " + periodTo));
        if (from.getSequenceNr() > to.getSequenceNr()) {
            throw new BadRequestException("periodFrom 不得晚于 periodTo");
        }
        return chain.stream()
                .filter(pispp -> {
                    int seq = periodsOrdered.stream()
                            .filter(p -> p.getId().equals(pispp.getPeriodId()))
                            .map(Period::getSequenceNr)
                            .findFirst()
                            .orElse(Integer.MAX_VALUE);
                    return seq >= from.getSequenceNr() && seq <= to.getSequenceNr();
                })
                .toList();
    }

    private static ProductInStockingPointPeriod findLastScopedPispp(
            OntologyGraph graph,
            String pispId,
            List<Period> periods,
            String periodTo) {
        List<ProductInStockingPointPeriod> chain =
                PispDailyClosingProjection.chainForPisp(graph, pispId, periods);
        return chain.stream()
                .filter(p -> periodTo.equals(p.getPeriodId()))
                .findFirst()
                .orElse(chain.stream()
                        .max(Comparator.comparingInt(p -> sequenceFor(periods, p.getPeriodId())))
                        .orElse(null));
    }

    private static int sequenceFor(List<Period> periods, String periodId) {
        return periods.stream()
                .filter(p -> p.getId().equals(periodId))
                .map(Period::getSequenceNr)
                .findFirst()
                .orElse(0);
    }

    private static MaterialBalancePeriodDto toPeriodDto(ProductInStockingPointPeriod pispp) {
        if (pispp == null) {
            return null;
        }
        return new MaterialBalancePeriodDto(
                pispp.getPeriodId(),
                BigDecimal.valueOf(pispp.getOnHand()),
                BigDecimal.valueOf(pispp.getPlannedDemandQuantityTotal()),
                BigDecimal.valueOf(pispp.getPlannedSupplyTotal()),
                BigDecimal.valueOf(Math.max(0, pispp.getPlannedInventoryLevel())),
                BigDecimal.valueOf(pispp.getStockShortageQuantity()));
    }

    private static String resolveResourceId(String productCode) {
        ProductResourceEntity pr = ProductResourceEntity.findFirstByProduct(productCode);
        return pr != null ? pr.resourceId : "UNKNOWN";
    }
}
