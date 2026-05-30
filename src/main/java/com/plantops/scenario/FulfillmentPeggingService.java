package com.plantops.scenario;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentOperationDto;
import com.plantops.api.dto.FulfillmentPegEdgeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.UtilizationBucketDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ???????? / ?? / ????????????
 */
@ApplicationScoped
public class FulfillmentPeggingService {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);
    private static final int MIN_BAR_MINUTES = 120;

    @Inject
    MasterPlanService masterPlanService;

    public OrderFulfillmentChainDto build(SalesOrderLineEntity order, String kittingStatus) {
        return build(order, kittingStatus, null);
    }

    public OrderFulfillmentChainDto build(
            SalesOrderLineEntity order, String kittingStatus, String masterPlanVersionId) {
        PeggingGraph graph = new PeggingGraph(order);
        String soNodeId = graph.addSalesOrder(order);
        graph.markRoot(soNodeId);

        pegDemand(
                graph,
                soNodeId,
                order.productCode,
                order.orderQty,
                1,
                order.salesOrderNo,
                order.salesOrderLineNo,
                null);

        graph.applyPlannedSchedule(order, masterPlanVersionId, masterPlanService);

        String overall = graph.hasShortage() ? "AT_RISK" : (graph.hasWorkOrderPeg() ? "PLANNED" : "ON_TRACK");
        return new OrderFulfillmentChainDto(
                order.salesOrderNo,
                order.salesOrderLineNo,
                order.productCode,
                order.dueDate,
                order.promiseDate,
                overall,
                kittingStatus,
                graph.nodes,
                graph.edges,
                graph.buildUtilizationBuckets());
    }

    /**
     * ??????????????????????depth 0??????BOM ??????
     * ???? / ?? / ?? ?????????????????????????????     * ??????????????= ?????????/??????????????     */
    public OrderFulfillmentChainDto buildForWorkOrder(
            WorkOrderEntity wo, SalesOrderLineEntity orderLine, String kittingStatus) {
        return buildForWorkOrder(wo, orderLine, kittingStatus, null);
    }

    public OrderFulfillmentChainDto buildForWorkOrder(
            WorkOrderEntity wo,
            SalesOrderLineEntity orderLine,
            String kittingStatus,
            String masterPlanVersionId) {
        PeggingGraph graph = new PeggingGraph(orderLine);
        String rootId = graph.addWorkOrder(wo, 0);
        graph.markRoot(rootId);

        expandWorkOrderNeeds(graph, wo, 1);

        graph.applyPlannedSchedule(orderLine, masterPlanVersionId, masterPlanService);

        String overall = graph.hasShortage() ? "AT_RISK" : "PLANNED";
        return new OrderFulfillmentChainDto(
                wo.salesOrderNo,
                wo.salesOrderLineNo,
                wo.productCode,
                orderLine.dueDate,
                orderLine.promiseDate,
                overall,
                kittingStatus,
                graph.nodes,
                graph.edges,
                graph.buildUtilizationBuckets());
    }

    /**
     * 工单下游满足链：从当前工单向上追溯至父工单及最终销售订单需求。
     */
    public OrderFulfillmentChainDto buildDownstreamForWorkOrder(
            WorkOrderEntity wo,
            SalesOrderLineEntity orderLine,
            String kittingStatus,
            String masterPlanVersionId) {
        PeggingGraph graph = new PeggingGraph(orderLine);
        String rootId = graph.addWorkOrder(wo, 0);
        graph.markRoot(rootId);

        expandWorkOrderConsumers(graph, wo, 1);

        graph.applyPlannedSchedule(orderLine, masterPlanVersionId, masterPlanService);

        String overall = graph.hasWorkOrderPeg() ? "PLANNED" : "ON_TRACK";
        return new OrderFulfillmentChainDto(
                wo.salesOrderNo,
                wo.salesOrderLineNo,
                wo.productCode,
                orderLine.dueDate,
                orderLine.promiseDate,
                overall,
                kittingStatus,
                graph.nodes,
                graph.edges,
                graph.buildUtilizationBuckets());
    }

    private void expandWorkOrderConsumers(PeggingGraph graph, WorkOrderEntity wo, int depth) {
        String woId = graph.nodeIdForWorkOrder(wo.workOrderNo);
        List<com.plantops.persistence.entity.WorkOrderBomDependencyEntity> parents =
                com.plantops.persistence.entity.WorkOrderBomDependencyEntity.findByChild(wo.workOrderNo);
        for (com.plantops.persistence.entity.WorkOrderBomDependencyEntity dep : parents) {
            WorkOrderEntity parent = WorkOrderEntity.findByNo(dep.parentWorkOrderNo);
            if (parent == null) {
                continue;
            }
            String parentId = graph.addWorkOrder(parent, depth);
            graph.addEdge(woId, parentId, "WORK_ORDER_PEG", wo.quantity);
            expandWorkOrderConsumers(graph, parent, depth + 1);
        }
        if (wo.bomLevel == 0) {
            for (com.plantops.persistence.entity.WorkOrderPeggingEntity peg :
                    com.plantops.persistence.entity.WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo)) {
                SalesOrderLineEntity order =
                        SalesOrderLineEntity.findByKey(peg.salesOrderNo, peg.salesOrderLineNo);
                if (order == null || "CANCELLED".equals(order.status)) {
                    continue;
                }
                String soId = graph.addSalesOrder(order);
                BigDecimal qty = peg.peggedQty != null ? peg.peggedQty : wo.quantity;
                graph.addEdge(woId, soId, "DEMAND_PEG", qty);
            }
        }
    }

    private void pegDemand(
            PeggingGraph graph,
            String demanderId,
            String productCode,
            BigDecimal qty,
            int depth,
            String salesOrderNo,
            int salesOrderLineNo,
            String parentWorkOrderNo) {

        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal remaining = qty;
        BigDecimal available = graph.availableInventory(productCode);
        if (available.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pegQty = available.min(remaining);
            String invId = graph.addInventory(productCode, pegQty, depth);
            graph.addEdge(invId, demanderId, "INVENTORY_PEG", pegQty);
            graph.consumeInventory(productCode, pegQty);
            remaining = remaining.subtract(pegQty);
        }

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        WorkOrderEntity wo = resolveWorkOrder(
                productCode, salesOrderNo, salesOrderLineNo, parentWorkOrderNo, demanderId);
        if (wo != null) {
            String woId = graph.addWorkOrder(wo, depth);
            graph.addEdge(woId, demanderId, "WORK_ORDER_PEG", remaining);
            expandWorkOrderNeeds(graph, wo, depth + 1);
            return;
        }

        String shortageId = graph.addShortage(productCode, remaining, depth);
        graph.addEdge(shortageId, demanderId, "SHORTAGE_PEG", remaining);
    }

    private WorkOrderEntity resolveWorkOrder(
            String productCode,
            String salesOrderNo,
            int salesOrderLineNo,
            String parentWorkOrderNo,
            String demanderId) {

        if (parentWorkOrderNo != null) {
            WorkOrderEntity byDep = WorkOrderEntity.findChildByDependency(parentWorkOrderNo, productCode);
            if (byDep != null) {
                return byDep;
            }
            for (WorkOrderEntity child : WorkOrderEntity.findChildren(parentWorkOrderNo)) {
                if (productCode.equals(child.productCode)) {
                    return child;
                }
            }
            return null;
        }

        if (demanderId.startsWith("so-")) {
            List<WorkOrderEntity> pegged = WorkOrderEntity.findByPeggingOrderLine(
                    salesOrderNo, salesOrderLineNo, productCode);
            if (!pegged.isEmpty()) {
                return pegged.get(0);
            }
            return WorkOrderEntity.findRootForOrderLine(salesOrderNo, salesOrderLineNo, productCode);
        }
        return null;
    }

    private void expandWorkOrderNeeds(PeggingGraph graph, WorkOrderEntity wo, int depth) {
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        String salesOrderNo = wo.salesOrderNo;
        int salesOrderLineNo = wo.salesOrderLineNo;
        if (salesOrderNo == null || salesOrderNo.isBlank()) {
            var pegs = com.plantops.persistence.entity.WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo);
            if (!pegs.isEmpty()) {
                salesOrderNo = pegs.get(0).salesOrderNo;
                salesOrderLineNo = pegs.get(0).salesOrderLineNo;
            }
        }
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity);
            pegDemand(
                    graph,
                    graph.nodeIdForWorkOrder(wo.workOrderNo),
                    bom.componentProductCode,
                    need,
                    depth,
                    salesOrderNo,
                    salesOrderLineNo,
                    wo.workOrderNo);
        }
    }

    private static String pegLabel(String pegType) {
        return switch (pegType) {
            case "INVENTORY_PEG" -> "库存满足";
            case "WORK_ORDER_PEG" -> "工单满足";
            case "DEMAND_PEG" -> "需求追溯";
            default -> "追溯";
        };
    }

    private static int leadMinutes(FulfillmentChainNodeDto node) {
        return switch (node.nodeType()) {
            case "INVENTORY" -> 60;
            case "SHORTAGE" -> (int) Duration.ofDays(3).toMinutes();
            case "WORK_ORDER" -> workOrderLeadMinutes(node);
            default -> MIN_BAR_MINUTES;
        };
    }

    private static int workOrderLeadMinutes(FulfillmentChainNodeDto node) {
        Object woNo = node.attributes().get("workOrderNo");
        if (woNo == null) {
            return MIN_BAR_MINUTES;
        }
        WorkOrderEntity wo = WorkOrderEntity.findByNo(woNo.toString());
        if (wo == null) {
            return MIN_BAR_MINUTES;
        }
        return Math.max(MIN_BAR_MINUTES, ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity));
    }

    private static List<FulfillmentOperationDto> buildOperations(
            WorkOrderEntity wo, LocalDateTime woStart, LocalDateTime woEnd) {
        List<com.plantops.persistence.entity.ProductResourceEntity> routingRows =
                com.plantops.persistence.entity.ProductResourceEntity.findByProductOrdered(wo.productCode);

        long totalMinutes = Math.max(MIN_BAR_MINUTES, Duration.between(woStart, woEnd).toMinutes());
        List<FulfillmentOperationDto> ops = new ArrayList<>();
        LocalDateTime cursor = woStart;

        if (!routingRows.isEmpty()) {
            long totalProcessSeconds = 0;
            for (com.plantops.persistence.entity.ProductResourceEntity r : routingRows) {
                totalProcessSeconds += r.processTimeSeconds != null ? r.processTimeSeconds.longValue() : 0;
            }
            for (int i = 0; i < routingRows.size(); i++) {
                com.plantops.persistence.entity.ProductResourceEntity row = routingRows.get(i);
                int seq = row.sequenceNo != null ? row.sequenceNo : (i + 1);
                String opName = row.operationName != null && !row.operationName.isBlank()
                        ? row.operationName : "工序 " + seq;
                LocalDateTime opEnd;
                if (i == routingRows.size() - 1) {
                    opEnd = woEnd;
                } else if (totalProcessSeconds > 0 && row.processTimeSeconds != null) {
                    long share = row.processTimeSeconds.longValue();
                    long minutes = Math.max(30, Math.round((double) totalMinutes * share / totalProcessSeconds));
                    opEnd = cursor.plusMinutes(minutes);
                    if (opEnd.isAfter(woEnd)) {
                        opEnd = woEnd;
                    }
                } else {
                    long perOp = Math.max(30, totalMinutes / routingRows.size());
                    opEnd = cursor.plusMinutes(perOp);
                    if (opEnd.isAfter(woEnd)) {
                        opEnd = woEnd;
                    }
                }
                int duration = (int) Duration.between(cursor, opEnd).toMinutes();
                int util = estimateUtilizationPct(row.resourceId, cursor.toLocalDate(), duration);
                ops.add(new FulfillmentOperationDto(
                        wo.workOrderNo + "-OP" + (seq * 10),
                        opName,
                        seq * 10,
                        row.resourceId,
                        cursor,
                        opEnd,
                        duration,
                        util));
                cursor = opEnd;
            }
            return ops;
        }

        List<ProductRoutingCatalog.RoutingStep> steps = ProductRoutingCatalog.stepsFor(wo.productCode);
        int perOp = (int) Math.max(30, totalMinutes / steps.size());
        for (int i = 0; i < steps.size(); i++) {
            ProductRoutingCatalog.RoutingStep step = steps.get(i);
            LocalDateTime opEnd = (i == steps.size() - 1) ? woEnd : cursor.plusMinutes(perOp);
            int duration = (int) Duration.between(cursor, opEnd).toMinutes();
            int util = estimateUtilizationPct(step.resourceId(), cursor.toLocalDate(), duration);
            ops.add(new FulfillmentOperationDto(
                    wo.workOrderNo + "-OP" + (10 + i * 10),
                    step.operationName(),
                    10 + i * 10,
                    step.resourceId(),
                    cursor,
                    opEnd,
                    duration,
                    util));
            cursor = opEnd;
        }
        return ops;
    }

    private static int estimateUtilizationPct(String resourceId, LocalDate date, int extraDemandMinutes) {
        int demand = extraDemandMinutes;
        int available = 480;
        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if (!order.dueDate.equals(date) && !order.dueDate.isBefore(date.plusDays(3))) {
                continue;
            }
            boolean canProduce = com.plantops.persistence.entity.ProductResourceEntity.listInWorkspace()
                    .stream()
                    .anyMatch(pr -> pr.productCode.equals(order.productCode) && pr.resourceId.equals(resourceId));
            if (!canProduce) {
                continue;
            }
            int stepMinutes = ProductRoutingSteps.durationMinutesForResource(
                    order.productCode, resourceId, order.orderQty);
            if (stepMinutes <= 0) {
                continue;
            }
            demand += stepMinutes;
        }
        for (ResourceCalendarEntity cal : ResourceCalendarEntity.findForResource(resourceId)) {
            if (cal.calendarDate.equals(date)) {
                available = cal.availableCapacityMinutes;
                break;
            }
        }
        if (available <= 0) {
            return demand > 0 ? 100 : 0;
        }
        return Math.min(200, (int) (demand * 100L / available));
    }

    private static final class PeggingGraph {
        final List<FulfillmentChainNodeDto> nodes = new ArrayList<>();
        final List<FulfillmentPegEdgeDto> edges = new ArrayList<>();
        final Map<String, BigDecimal> inventory = new HashMap<>();
        final LocalDate dueDate;
        final Set<String> nodeIds = new HashSet<>();
        final Set<String> resourcesInChain = new HashSet<>();
        LocalDate horizonStart;
        LocalDate horizonEnd;
        boolean shortage;
        boolean workOrderPeg;
        String rootNodeId;

        PeggingGraph(SalesOrderLineEntity orderLine) {
            this.dueDate = orderLine.dueDate;
            this.horizonStart = orderLine.dueDate.minusDays(30);
            this.horizonEnd = orderLine.dueDate;
            for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
                inventory.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
            }
        }

        void trackHorizon(LocalDateTime start, LocalDateTime end) {
            if (start.toLocalDate().isBefore(horizonStart)) {
                horizonStart = start.toLocalDate();
            }
            if (end.toLocalDate().isAfter(horizonEnd)) {
                horizonEnd = end.toLocalDate();
            }
        }

        BigDecimal availableInventory(String product) {
            return inventory.getOrDefault(product, BigDecimal.ZERO);
        }

        void consumeInventory(String product, BigDecimal qty) {
            inventory.merge(product, qty.negate(), BigDecimal::add);
        }

        void markRoot(String nodeId) {
            this.rootNodeId = nodeId;
        }

        String addSalesOrder(SalesOrderLineEntity order) {
            String id = "so-" + order.salesOrderNo + "-" + order.salesOrderLineNo;
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            nodes.add(node(
                    id,
                    "SALES_ORDER",
                    "销售订单 " + order.salesOrderNo + "-" + order.salesOrderLineNo,
                    "DEMAND",
                    0,
                    order.productCode,
                    order.orderQty,
                    placeholder,
                    placeholder,
                    Map.of("dueDate", order.dueDate.toString(), "priority", order.priority),
                    List.of()));
            nodeIds.add(id);
            return id;
        }

        String addInventory(String product, BigDecimal qty, int depth) {
            String id = "inv-" + product + "-" + depth + "-" + nodes.size();
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            addSupplierNode(id, "INVENTORY", depth, "库存 · " + product, "OK", product, qty,
                    Map.of("pegType", "INVENTORY_PEG", "stockingPoint", "WH-01"), List.of(),
                    placeholder, placeholder);
            return id;
        }

        String addWorkOrder(WorkOrderEntity wo, int depth) {
            String id = nodeIdForWorkOrder(wo.workOrderNo);
            if (nodeIds.contains(id)) {
                return id;
            }
            workOrderPeg = true;
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("pegType", "WORK_ORDER_PEG");
            attrs.put("workOrderNo", wo.workOrderNo);
            attrs.put("resourceId", wo.resourceId);
            if (wo.parentWorkOrderNo != null) {
                attrs.put("parentWorkOrderNo", wo.parentWorkOrderNo);
            }
            addSupplierNode(id, "WORK_ORDER", depth, "工单 · " + wo.workOrderNo, "PLANNED",
                    wo.productCode, wo.quantity, attrs, List.of(), placeholder, placeholder);
            return id;
        }

        String nodeIdForWorkOrder(String workOrderNo) {
            return "wo-" + workOrderNo;
        }

        String addShortage(String product, BigDecimal qty, int depth) {
            shortage = true;
            String id = "short-" + product + "-" + nodes.size();
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            addSupplierNode(id, "SHORTAGE", depth, "缺料 · " + product, "SHORTAGE",
                    product, qty, Map.of("pegType", "SHORTAGE_PEG"), List.of(),
                    placeholder, placeholder);
            return id;
        }

        void addSupplierNode(
                String id,
                String nodeType,
                int depth,
                String label,
                String status,
                String product,
                BigDecimal qty,
                Map<String, Object> attrs,
                List<FulfillmentOperationDto> operations,
                LocalDateTime start,
                LocalDateTime end) {
            nodes.add(node(id, nodeType, label, status, depth, product, qty, start, end, attrs, operations));
            nodeIds.add(id);
        }

        void addEdge(String from, String to, String pegType, BigDecimal qty) {
            edges.add(new FulfillmentPegEdgeDto(from, to, pegType, pegLabel(pegType)));
        }

        /**
         * ???????????????= ???????planEnd???? = ????????planStart???         * ????planEnd = ??????????????????????????????????         */
        void applyPlannedSchedule(SalesOrderLineEntity orderLine) {
            applyPlannedSchedule(orderLine, null, null);
        }

        void applyPlannedSchedule(
                SalesOrderLineEntity orderLine,
                String masterPlanVersionId,
                MasterPlanService planService) {
            Map<String, List<FulfillmentPegEdgeDto>> outgoing = new HashMap<>();
            for (FulfillmentPegEdgeDto edge : edges) {
                outgoing.computeIfAbsent(edge.fromNodeId(), k -> new ArrayList<>()).add(edge);
            }

            Map<String, FulfillmentChainNodeDto> nodeById = new HashMap<>();
            for (FulfillmentChainNodeDto n : nodes) {
                nodeById.put(n.nodeId(), n);
            }

            List<FulfillmentChainNodeDto> sorted = new ArrayList<>(nodes);
            sorted.sort(Comparator.comparingInt(FulfillmentChainNodeDto::depth));

            Map<String, LocalDateTime> planStart = new HashMap<>();
            Map<String, LocalDateTime> planEnd = new HashMap<>();

            LocalDateTime rootFulfill = orderLine.dueDate.atTime(WORKDAY_END);
            if (orderLine.promiseDate != null && !orderLine.promiseDate.isAfter(orderLine.dueDate)) {
                rootFulfill = orderLine.promiseDate.atTime(WORKDAY_END);
            }

            for (FulfillmentChainNodeDto node : sorted) {
                boolean isRoot = rootNodeId != null && rootNodeId.equals(node.nodeId());
                if (isRoot) {
                    LocalDateTime planFulfill = rootFulfill;
                    LocalDateTime start = planFulfill.minusDays(7).with(WORKDAY_START);
                    if (!start.isBefore(planFulfill)) {
                        start = planFulfill.minusMinutes(Math.max(MIN_BAR_MINUTES, leadMinutes(node)));
                    }
                    planEnd.put(node.nodeId(), planFulfill);
                    planStart.put(node.nodeId(), start);
                    continue;
                }

                List<FulfillmentPegEdgeDto> outs = outgoing.getOrDefault(node.nodeId(), List.of());
                LocalDateTime fulfill;
                if (outs.isEmpty()) {
                    fulfill = dueDate.minusDays((long) node.depth() * 2L).atTime(WORKDAY_END);
                } else {
                    fulfill = outs.stream()
                            .map(e -> demandNeedTime(nodeById.get(e.toNodeId()), planStart, planEnd))
                            .filter(java.util.Objects::nonNull)
                            .min(LocalDateTime::compareTo)
                            .orElse(dueDate.atTime(WORKDAY_END));
                }
                int lead = leadMinutes(node);
                LocalDateTime start = fulfill.minusMinutes(lead);
                if (!start.isBefore(fulfill)) {
                    start = fulfill.minusMinutes(MIN_BAR_MINUTES);
                }
                planEnd.put(node.nodeId(), fulfill);
                planStart.put(node.nodeId(), start);
            }

            // ??????? = ????? planStart
            if (rootNodeId != null) {
                LocalDateTime rootEnd = planEnd.get(rootNodeId);
                if (rootEnd != null) {
                    LocalDateTime earliest = nodes.stream()
                            .filter(n -> !n.nodeId().equals(rootNodeId))
                            .map(n -> planStart.get(n.nodeId()))
                            .filter(java.util.Objects::nonNull)
                            .min(LocalDateTime::compareTo)
                            .orElse(planStart.get(rootNodeId));
                    if (earliest != null) {
                        if (!earliest.isBefore(rootEnd)) {
                            earliest = rootEnd.minusMinutes(MIN_BAR_MINUTES);
                        }
                        planStart.put(rootNodeId, earliest);
                    }
                }
            }

            if (masterPlanVersionId != null && !masterPlanVersionId.isBlank() && planService != null) {
                for (FulfillmentChainNodeDto node : nodes) {
                    if (!"WORK_ORDER".equals(node.nodeType())) {
                        continue;
                    }
                    Object woNo = node.attributes().get("workOrderNo");
                    if (woNo == null) {
                        continue;
                    }
                    MasterPlanService.WorkOrderPlannedWindow window =
                            planService.resolveWorkOrderWindow(masterPlanVersionId, woNo.toString());
                    if (window == null) {
                        continue;
                    }
                    planStart.put(node.nodeId(), window.plannedStart());
                    planEnd.put(node.nodeId(), window.plannedEnd());
                }
            }

            for (int i = 0; i < nodes.size(); i++) {
                FulfillmentChainNodeDto node = nodes.get(i);
                LocalDateTime start = planStart.get(node.nodeId());
                LocalDateTime fulfill = planEnd.get(node.nodeId());
                if (start == null || fulfill == null) {
                    continue;
                }
                trackHorizon(start, fulfill);

                List<FulfillmentOperationDto> ops = node.operations();
                if ("WORK_ORDER".equals(node.nodeType())) {
                    Object woNo = node.attributes().get("workOrderNo");
                    if (woNo != null) {
                        WorkOrderEntity wo = WorkOrderEntity.findByNo(woNo.toString());
                        if (wo != null) {
                            ops = buildOperations(wo, start, fulfill);
                            for (FulfillmentOperationDto op : ops) {
                                resourcesInChain.add(op.resourceId());
                            }
                        }
                    }
                }

                Map<String, Object> attrs = new HashMap<>(node.attributes());
                attrs.put("plannedStartTs", start.toString());
                attrs.put("plannedEndTs", fulfill.toString());
                nodes.set(i, node(
                        node.nodeId(),
                        node.nodeType(),
                        node.label(),
                        node.status(),
                        node.depth(),
                        node.productCode(),
                        node.quantity(),
                        start,
                        fulfill,
                        attrs,
                        ops));
            }
        }

        private static LocalDateTime demandNeedTime(
                FulfillmentChainNodeDto demander,
                Map<String, LocalDateTime> planStart,
                Map<String, LocalDateTime> planEnd) {
            if (demander == null) {
                return null;
            }
            if ("SALES_ORDER".equals(demander.nodeType())) {
                return planEnd.get(demander.nodeId());
            }
            return planStart.get(demander.nodeId());
        }

        FulfillmentChainNodeDto node(
                String id,
                String nodeType,
                String label,
                String status,
                int depth,
                String product,
                BigDecimal qty,
                LocalDateTime start,
                LocalDateTime end,
                Map<String, Object> attrs,
                List<FulfillmentOperationDto> operations) {
            return new FulfillmentChainNodeDto(
                    id, nodeType, nodeType, label, status, depth, product, qty, start, end, attrs, operations);
        }

        List<UtilizationBucketDto> buildUtilizationBuckets() {
            List<UtilizationBucketDto> buckets = new ArrayList<>();
            for (String resourceId : resourcesInChain) {
                LocalDate d = horizonStart;
                while (!d.isAfter(horizonEnd)) {
                    LocalDateTime bucketStart = d.atTime(8, 0);
                    LocalDateTime bucketEnd = d.atTime(17, 0);
                    int demand = 0;
                    for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
                        if (!order.dueDate.equals(d) && !order.dueDate.isBefore(d.plusDays(3))) {
                            continue;
                        }
                        boolean canProduce = com.plantops.persistence.entity.ProductResourceEntity.listInWorkspace()
                                .stream()
                                .anyMatch(pr -> pr.productCode.equals(order.productCode)
                                        && pr.resourceId.equals(resourceId));
                        if (!canProduce) {
                            continue;
                        }
                        int stepMinutes = ProductRoutingSteps.durationMinutesForResource(
                                order.productCode, resourceId, order.orderQty);
                        if (stepMinutes <= 0) {
                            continue;
                        }
                        demand += stepMinutes;
                    }
                    int available = 480;
                    for (ResourceCalendarEntity cal : ResourceCalendarEntity.findForResource(resourceId)) {
                        if (cal.calendarDate.equals(d)) {
                            available = cal.availableCapacityMinutes;
                            break;
                        }
                    }
                    int util = available <= 0 ? (demand > 0 ? 100 : 0) : Math.min(200, (int) (demand * 100L / available));
                    buckets.add(new UtilizationBucketDto(
                            resourceId, bucketStart, bucketEnd, demand, available, util));
                    d = d.plusDays(1);
                }
            }
            return buckets;
        }

        boolean hasShortage() {
            return shortage;
        }

        boolean hasWorkOrderPeg() {
            return workOrderPeg;
        }
    }
}
