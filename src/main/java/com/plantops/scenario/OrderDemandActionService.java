package com.plantops.scenario;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.demand.OrderDemandActionRequest;
import com.plantops.api.dto.demand.OrderDemandActionResult;
import com.plantops.api.dto.demand.PromiseDatePreviewDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxService;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;

@ApplicationScoped
public class OrderDemandActionService {

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    DeliveryPlanningSandboxService deliveryPlanningSandboxService;

    @Inject
    OrderDemandCancelPlanService orderDemandCancelPlanService;

    @Inject
    OrderDemandCancelPromiseService orderDemandCancelPromiseService;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    public OrderDemandActionResult execute(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandAction action,
            OrderDemandActionRequest body) {
        OrderDemandActionRequest req = body != null ? body : new OrderDemandActionRequest(null, null, null, null);
        return switch (action) {
            case INFINITE_PLAN_JIT, BUILD_UPSTREAM_CHAIN -> infinitePlanJit(salesOrderNo, salesOrderLineNo, req);
            case FINITE_PLAN, PLAN_FINITE -> finitePlanForDelivery(salesOrderNo, salesOrderLineNo, req);
            case PLAN_UNCONSTRAINED -> unconstrainedPlanPreview(salesOrderNo, salesOrderLineNo, req);
            case CONFIRM_PROMISE_DATE -> confirmPromiseDate(salesOrderNo, salesOrderLineNo, req);
            case CANCEL_PLAN -> cancelPlan(salesOrderNo, salesOrderLineNo, req);
            case CANCEL_PROMISE -> cancelPromise(salesOrderNo, salesOrderLineNo, req);
        };
    }

    public PromiseDatePreviewDto previewPromiseDate(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        OrderDemandActionRequest effective = req != null ? req : new OrderDemandActionRequest(null, null, null, null);
        OrderFulfillmentChainDto chain = deliveryPlanningSandboxService.optimizeForDelivery(
                salesOrderNo,
                salesOrderLineNo,
                "finite-capacity",
                blankToNull(effective.masterPlanVersionId()),
                effective.useFeedbackOverlay(),
                effective.feedbackCutoff());
        LocalDate suggested = FulfillmentChainPromiseDate.suggest(chain);
        return new PromiseDatePreviewDto(chain, suggested, chain.overallStatus());
    }

    private OrderDemandActionResult cancelPlan(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        OrderDemandCancelPlanService.CancelPlanSummary summary =
                orderDemandCancelPlanService.cancelForOrderLine(salesOrderNo, salesOrderLineNo);
        String deliveryId = ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo);
        deliveryPlanningSandboxService.invalidateForDelivery(deliveryId);
        authoritativeOntologyGraph.invalidate(
                WorkspaceResolver.currentWorkspaceId(), blankToNull(req.masterPlanVersionId()));
        OrderFulfillmentChainDto chain = ontologyFulfillmentService.fulfillmentChainFromDeliveryScoped(
                deliveryId, blankToNull(req.masterPlanVersionId()));
        String message = buildCancelPlanMessage(summary);
        return new OrderDemandActionResult(
                OrderDemandAction.CANCEL_PLAN.name(),
                message,
                chain,
                null,
                null);
    }

    private static String buildCancelPlanMessage(OrderDemandCancelPlanService.CancelPlanSummary summary) {
        if (summary.peggingRemoved() == 0 && summary.workOrdersDeleted() == 0) {
            return "当前订单行无计划工单，无需取消";
        }
        StringBuilder sb = new StringBuilder("已取消计划");
        if (summary.workOrdersDeleted() > 0) {
            sb.append("，删除 ").append(summary.workOrdersDeleted()).append(" 个专属工单");
        }
        if (summary.workOrdersRetained() > 0) {
            sb.append("，保留 ").append(summary.workOrdersRetained()).append(" 个共享/已下发工单（仅解除 pegging）");
        }
        return sb.toString();
    }

    private OrderDemandActionResult cancelPromise(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        OrderDemandCancelPromiseService.CancelPromiseResult summary =
                orderDemandCancelPromiseService.cancelForOrderLine(salesOrderNo, salesOrderLineNo);
        String deliveryId = ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo);
        authoritativeOntologyGraph.invalidate(
                WorkspaceResolver.currentWorkspaceId(), blankToNull(req.masterPlanVersionId()));
        OrderFulfillmentChainDto chain = ontologyFulfillmentService.fulfillmentChainFromDeliveryScoped(
                deliveryId, blankToNull(req.masterPlanVersionId()));
        return new OrderDemandActionResult(
                OrderDemandAction.CANCEL_PROMISE.name(),
                summary.message(),
                chain,
                null,
                null);
    }

    private OrderDemandActionResult infinitePlanJit(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        String deliveryId = ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo);
        deliveryPlanningSandboxService.invalidateForDelivery(deliveryId);
        OrderFulfillmentChainDto chain = ontologyFulfillmentService.buildUpstreamChain(
                deliveryId, blankToNull(req.masterPlanVersionId()));
        long workOrderCount = chain.nodes().stream()
                .filter(n -> "SUPPLY_ORDER".equals(n.nodeType()))
                .count();
        return new OrderDemandActionResult(
                OrderDemandAction.INFINITE_PLAN_JIT.name(),
                "无限能力计划（JIT）完成：已按交期倒排并创建/挂接 " + workOrderCount + " 个上游 SupplyOrder",
                chain,
                null,
                null);
    }

    private OrderDemandActionResult finitePlanForDelivery(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        deliveryPlanningSandboxService.invalidateForDelivery(
                ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo));
        OrderFulfillmentChainDto chain = deliveryPlanningSandboxService.optimizeForDelivery(
                salesOrderNo,
                salesOrderLineNo,
                "finite-capacity",
                blankToNull(req.masterPlanVersionId()),
                req.useFeedbackOverlay(),
                req.feedbackCutoff());
        return new OrderDemandActionResult(
                OrderDemandAction.FINITE_PLAN.name(),
                "有限能力计划完成（单交付优化，结果已写入满足链预览）",
                chain,
                null,
                null);
    }

    private OrderDemandActionResult unconstrainedPlanPreview(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        deliveryPlanningSandboxService.invalidateForDelivery(
                ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo));
        OrderFulfillmentChainDto chain = deliveryPlanningSandboxService.optimizeForDelivery(
                salesOrderNo,
                salesOrderLineNo,
                MasterPlanStrategyConfigService.UNCONSTRAINED_STRATEGY_ID,
                blankToNull(req.masterPlanVersionId()),
                req.useFeedbackOverlay(),
                req.feedbackCutoff());
        return new OrderDemandActionResult(
                OrderDemandAction.PLAN_UNCONSTRAINED.name(),
                "无限能力推演完成",
                chain,
                null,
                null);
    }

    @Transactional
    OrderDemandActionResult confirmPromiseDate(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        SalesOrderLineEntity order = requireOrder(salesOrderNo, salesOrderLineNo);
        LocalDate promiseDate = req.promiseDateOverride();
        OrderFulfillmentChainDto chain = null;
        if (promiseDate == null) {
            chain = deliveryPlanningSandboxService.optimizeForDelivery(
                    salesOrderNo,
                    salesOrderLineNo,
                    "finite-capacity",
                    blankToNull(req.masterPlanVersionId()),
                    req.useFeedbackOverlay(),
                    req.feedbackCutoff());
            promiseDate = FulfillmentChainPromiseDate.suggest(chain);
            if (promiseDate == null) {
                throw new BadRequestException("无法从满足链推算承诺交期，请先执行有限能力计划或检查工单与工艺数据");
            }
            if ("BLOCKED".equals(chain.overallStatus())) {
                throw new BadRequestException("满足链状态为 BLOCKED，不宜自动确认承诺交期");
            }
        }
        order.promiseDate = promiseDate;
        String message = "承诺交期已更新为 " + promiseDate;
        if (order.dueDate != null && promiseDate.isAfter(order.dueDate)) {
            message += "（晚于客户交期 " + order.dueDate + "）";
        }
        return new OrderDemandActionResult(
                OrderDemandAction.CONFIRM_PROMISE_DATE.name(),
                message,
                chain,
                promiseDate,
                null);
    }

    private static SalesOrderLineEntity requireOrder(String salesOrderNo, int salesOrderLineNo) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, salesOrderLineNo);
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("销售订单行不存在: " + salesOrderNo + "-" + salesOrderLineNo);
        }
        return order;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private OrderFulfillmentChainDto ontologyFulfillmentChain(
            String salesOrderNo, int salesOrderLineNo, OrderDemandActionRequest req) {
        return ontologyFulfillmentService.fulfillmentChainForOrderLine(
                salesOrderNo, salesOrderLineNo, blankToNull(req.masterPlanVersionId()));
    }
}
