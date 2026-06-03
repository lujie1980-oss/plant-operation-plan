package com.plantops.scenario;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;
import com.plantops.api.dto.demand.OrderDemandActionRequest;
import com.plantops.api.dto.demand.OrderDemandActionResult;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainNodeDto;
import com.plantops.api.dto.planning.OrderPlanningChainPreviewRequest;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.scenario.planning.OrderPlanningChainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class OrderDemandActionService {

    @Inject
    DemandService demandService;

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    OrderPlanningChainService orderPlanningChainService;

    @Inject
    OrderDemandCancelPlanService orderDemandCancelPlanService;

    public OrderDemandActionResult execute(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandAction action,
            OrderDemandActionRequest body) {
        OrderDemandActionRequest req = body != null ? body : new OrderDemandActionRequest(null, null, null, null);
        return switch (action) {
            case BUILD_UPSTREAM_CHAIN -> buildUpstreamChain(salesOrderNo, salesOrderLineNo, req);
            case PLAN_UNCONSTRAINED -> planPreview(
                    salesOrderNo, salesOrderLineNo, req, MasterPlanStrategyConfigService.UNCONSTRAINED_STRATEGY_ID);
            case PLAN_FINITE -> planPreview(
                    salesOrderNo, salesOrderLineNo, req, "finite-capacity");
            case CONFIRM_PROMISE_DATE -> confirmPromiseDate(salesOrderNo, salesOrderLineNo, req);
            case CANCEL_PLAN -> cancelPlan(salesOrderNo, salesOrderLineNo, req);
        };
    }

    private OrderDemandActionResult cancelPlan(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        OrderDemandCancelPlanService.CancelPlanSummary summary =
                orderDemandCancelPlanService.cancelForOrderLine(salesOrderNo, salesOrderLineNo);
        OrderFulfillmentChainDto chain = demandService.getFulfillmentChain(
                salesOrderNo, salesOrderLineNo, blankToNull(req.masterPlanVersionId()));
        String message = buildCancelPlanMessage(summary);
        return new OrderDemandActionResult(
                OrderDemandAction.CANCEL_PLAN.name(),
                message,
                chain,
                null,
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

    private OrderDemandActionResult buildUpstreamChain(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req) {
        WorkOrderGenerationResultDto generation = workOrderGenerationService.generateForOrderLine(
                salesOrderNo, salesOrderLineNo, true);
        OrderFulfillmentChainDto chain = demandService.getFulfillmentChain(
                salesOrderNo, salesOrderLineNo, blankToNull(req.masterPlanVersionId()));
        return new OrderDemandActionResult(
                OrderDemandAction.BUILD_UPSTREAM_CHAIN.name(),
                "已刷新 MRP 合并工单并重建满足链（全场景重算）",
                chain,
                null,
                null,
                generation);
    }

    private OrderDemandActionResult planPreview(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req,
            String strategyId) {
        OrderPlanningChainDto planning = orderPlanningChainService.preview(previewRequest(
                salesOrderNo, salesOrderLineNo, req, strategyId));
        String label = MasterPlanStrategyConfigService.UNCONSTRAINED_STRATEGY_ID.equals(strategyId)
                ? "无限能力"
                : "有限能力";
        return new OrderDemandActionResult(
                strategyId.equals(MasterPlanStrategyConfigService.UNCONSTRAINED_STRATEGY_ID)
                        ? OrderDemandAction.PLAN_UNCONSTRAINED.name()
                        : OrderDemandAction.PLAN_FINITE.name(),
                label + "推演完成",
                null,
                planning,
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
        OrderPlanningChainDto planning = null;
        if (promiseDate == null) {
            planning = orderPlanningChainService.preview(previewRequest(
                    salesOrderNo, salesOrderLineNo, req, "finite-capacity"));
            promiseDate = suggestPromiseDate(planning);
            if (promiseDate == null) {
                throw new BadRequestException("无法从有限能力推演中推算承诺交期，请检查工单与工艺数据");
            }
            if ("BLOCKED".equals(planning.overallStatus())) {
                throw new BadRequestException("订单推演状态为 BLOCKED，不宜自动确认承诺交期");
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
                null,
                planning,
                promiseDate,
                null);
    }

    static LocalDate suggestPromiseDate(OrderPlanningChainDto planning) {
        if (planning == null || planning.nodes() == null) {
            return null;
        }
        return planning.nodes().stream()
                .filter(n -> "WORK_ORDER".equals(n.nodeType()) || "SALES_ORDER".equals(n.nodeType()))
                .map(OrderPlanningChainNodeDto::windowEnd)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static OrderPlanningChainPreviewRequest previewRequest(
            String salesOrderNo,
            int salesOrderLineNo,
            OrderDemandActionRequest req,
            String strategyId) {
        return new OrderPlanningChainPreviewRequest(
                salesOrderNo,
                salesOrderLineNo,
                strategyId,
                req.useFeedbackOverlay(),
                req.feedbackCutoff(),
                blankToNull(req.masterPlanVersionId()),
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
}
