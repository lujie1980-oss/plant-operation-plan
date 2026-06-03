package com.plantops.scenario.planning;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainPreviewRequest;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.DetailScheduleService;
import com.plantops.scenario.FulfillmentPeggingService;
import com.plantops.scenario.MasterPlanService;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OrderPlanningChainService {

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    FulfillmentPeggingService fulfillmentPeggingService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    @Inject
    SampleDataLoader sampleDataLoader;

    public OrderPlanningChainDto preview(OrderPlanningChainPreviewRequest req) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(req.salesOrderNo(), req.salesOrderLineNo());
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("Sales order line not found: "
                    + req.salesOrderNo() + "-" + req.salesOrderLineNo());
        }

        sampleDataLoader.extendCalendarsToHorizon();

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                blankToNull(req.masterPlanStrategyId()));
        MasterPlanCapacityOverlay overlay = Boolean.TRUE.equals(req.useFeedbackOverlay())
                ? masterPlanService.buildFeedbackOverlay(
                        req.feedbackCutoff() != null ? req.feedbackCutoff() : LocalDate.now())
                : MasterPlanCapacityOverlay.empty();

        MaterialPlanningContext material = materialPlanningContextBuilder.build();
        MasterPlanPlanningContext mpCtx = masterPlanService.buildPlanningContext(resolved, overlay, material);

        DetailSchedulePlanningContext dsCtx = null;
        String detailMpId = blankToNull(req.detailScheduleMasterPlanVersionId());
        if (detailMpId != null) {
            dsCtx = detailScheduleService.buildPlanningContext(detailMpId, material);
        }

        String kittingStatus = resolveKittingStatus(order.salesOrderNo, order.salesOrderLineNo);
        OrderFulfillmentChainDto topology = fulfillmentPeggingService.build(order, kittingStatus, null);
        List<String> workOrderNos = extractWorkOrderNos(topology);

        String baselineId = blankToNull(req.baselineMasterPlanVersionId());
        OrderPlanningChainProjector.BaselineWindowResolver baseline = null;
        if (baselineId != null) {
            baseline = wo -> {
                var window = masterPlanService.resolveWorkOrderWindow(baselineId, wo);
                if (window == null) {
                    return null;
                }
                return new LocalDate[] {
                        window.plannedStart().toLocalDate(),
                        window.plannedEnd().toLocalDate()
                };
            };
        }

        return OrderPlanningChainProjector.project(
                topology, mpCtx, dsCtx, workOrderNos, baselineId, baseline);
    }

    private static List<String> extractWorkOrderNos(OrderFulfillmentChainDto topology) {
        List<String> workOrderNos = new ArrayList<>();
        for (var node : topology.nodes()) {
            if (!"WORK_ORDER".equals(node.nodeType())) {
                continue;
            }
            Object wo = node.attributes() != null ? node.attributes().get("workOrderNo") : null;
            if (wo != null) {
                workOrderNos.add(wo.toString());
            }
        }
        return workOrderNos;
    }

    private static String resolveKittingStatus(String salesOrderNo, int lineNo) {
        KittingResultEntity result = KittingResultEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2 order by computedTs desc",
                        salesOrderNo, lineNo)
                .firstResult();
        return result != null ? result.kittingStatus : "UNKNOWN";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
