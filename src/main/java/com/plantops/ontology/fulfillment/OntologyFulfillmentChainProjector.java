package com.plantops.ontology.fulfillment;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentOperationDto;
import com.plantops.api.dto.FulfillmentPegEdgeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.UtilizationBucketDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.PlanningDiagnostic;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 {@link OntologyGraph} 投影客户交付需求的 SupplyOrder 满足链，供需求满足页甘特复用。
 */
@ApplicationScoped
public class OntologyFulfillmentChainProjector {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);
    private static final int MIN_BAR_MINUTES = 60;

    public OrderFulfillmentChainDto project(OntologyGraph graph, String deliveryId) {
        return project(graph, deliveryId, null);
    }

    public OrderFulfillmentChainDto project(
            OntologyGraph graph,
            String deliveryId,
            OptimizerResult optimizerResult) {
        return project(graph, deliveryId, optimizerResult, optimizerResult != null ? 1 : 0);
    }

    public OrderFulfillmentChainDto project(
            OntologyGraph graph,
            String deliveryId,
            OptimizerResult optimizerResult,
            int trialRevision) {
        CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(deliveryId);
        if (delivery == null) {
            return null;
        }
        CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
        if (line == null) {
            return null;
        }
        String demandId = OntologyIds.demandFromCustomerDeliveryId(deliveryId);
        Demand rootDemand = graph.demand(demandId);
        if (rootDemand == null) {
            return null;
        }

        SalesOrderLineEntity orderLine = SalesOrderLineEntity.findByKey(line.getSalesOrderNo(), line.getSalesOrderLineNo());
        LocalDate dueDate = delivery.getLatestDesiredDate() != null
                ? delivery.getLatestDesiredDate()
                : rootDemand.getNeedDate();
        LocalDate promiseDate = orderLine != null ? orderLine.promiseDate : delivery.getRequestedDate();

        ProjectionState state = new ProjectionState(dueDate);
        String rootNodeId = state.addSalesOrderRoot(line, delivery, orderLine);
        state.markRoot(rootNodeId);
        expandDemandFulfillments(graph, demandId, rootNodeId, 1, state);

        state.applyScheduleTimes(graph, line, promiseDate);
        state.applyPlannedSupplyOrderRollup(graph);
        if (optimizerResult != null) {
            state.applyOptimizerMetadata(optimizerResult, trialRevision);
        }
        String overall = state.hasShortage ? "AT_RISK" : (state.hasSupplyOrderPeg ? "PLANNED" : "ON_TRACK");
        String kitting = state.hasShortage ? "SHORTAGE" : "KITTING_OK";

        return new OrderFulfillmentChainDto(
                line.getSalesOrderNo(),
                line.getSalesOrderLineNo(),
                line.getProductCode(),
                dueDate,
                promiseDate,
                overall,
                kitting,
                List.copyOf(state.nodes),
                List.copyOf(state.edges),
                List.of(),
                deliveryId);
    }

    /**
     * 工单上游满足链：以 SupplyOrder 为根，展开子件 / 库存 / 缺料供应方。
     */
    public OrderFulfillmentChainDto projectUpstreamForSupplyOrder(OntologyGraph graph, String supplyOrderId) {
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        if (supplyOrder == null) {
            return null;
        }
        WorkOrderChainContext ctx = resolveWorkOrderChainContext(graph, supplyOrderId, supplyOrder);
        ProjectionState state = new ProjectionState(ctx.dueDate());
        String rootNodeId = state.ensureRootSupplyOrderNode(graph, supplyOrderId, supplyOrder);
        if (rootNodeId == null) {
            return null;
        }
        state.markRoot(rootNodeId);
        expandSupplyOrderMaterialNeeds(graph, supplyOrderId, rootNodeId, 1, state);
        state.applyScheduleTimes(graph, ctx.customerOrderLine(), ctx.promiseDate());
        state.applyPlannedSupplyOrderRollup(graph);
        return buildSupplyOrderChainDto(ctx, state, null);
    }

    /**
     * 工单下游满足链：以 SupplyOrder 为根，追溯父工单及客户交付需求。
     */
    public OrderFulfillmentChainDto projectDownstreamForSupplyOrder(OntologyGraph graph, String supplyOrderId) {
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        if (supplyOrder == null) {
            return null;
        }
        WorkOrderChainContext ctx = resolveWorkOrderChainContext(graph, supplyOrderId, supplyOrder);
        ProjectionState state = new ProjectionState(ctx.dueDate());
        String rootNodeId = state.ensureRootSupplyOrderNode(graph, supplyOrderId, supplyOrder);
        if (rootNodeId == null) {
            return null;
        }
        state.markRoot(rootNodeId);
        expandSupplyOrderConsumers(graph, supplyOrderId, rootNodeId, 1, state);
        state.applyScheduleTimes(graph, ctx.customerOrderLine(), ctx.promiseDate());
        state.applyPlannedSupplyOrderRollup(graph);
        return buildSupplyOrderChainDto(ctx, state, state.hasSupplyOrderPeg ? "PLANNED" : "ON_TRACK");
    }

    private record WorkOrderChainContext(
            String salesOrderNo,
            int salesOrderLineNo,
            String productCode,
            LocalDate dueDate,
            LocalDate promiseDate,
            CustomerOrderLine customerOrderLine) {
    }

    private WorkOrderChainContext resolveWorkOrderChainContext(
            OntologyGraph graph,
            String supplyOrderId,
            SupplyOrder supplyOrder) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(supplyOrderId);
        SalesOrderLineEntity orderLine = null;
        if (wo != null && wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()) {
            orderLine = SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
        }
        LocalDate dueDate = wo != null && wo.needDate != null
                ? wo.needDate
                : supplyOrder.getNeedDate() != null
                        ? supplyOrder.getNeedDate()
                        : LocalDate.now().plusDays(7);
        LocalDate promiseDate = orderLine != null && orderLine.promiseDate != null ? orderLine.promiseDate : dueDate;
        String salesOrderNo = wo != null && wo.salesOrderNo != null ? wo.salesOrderNo
                : orderLine != null ? orderLine.salesOrderNo : "";
        int salesOrderLineNo = wo != null ? wo.salesOrderLineNo
                : orderLine != null ? orderLine.salesOrderLineNo : 0;
        String productCode = supplyOrder.getProductCode();
        CustomerOrderLine line = null;
        if (salesOrderNo != null && !salesOrderNo.isBlank() && salesOrderLineNo > 0) {
            line = graph.customerOrderLine(OntologyIds.customerOrderLineId(salesOrderNo, salesOrderLineNo));
        }
        return new WorkOrderChainContext(
                salesOrderNo, salesOrderLineNo, productCode, dueDate, promiseDate, line);
    }

    private OrderFulfillmentChainDto buildSupplyOrderChainDto(
            WorkOrderChainContext ctx,
            ProjectionState state,
            String overallOverride) {
        String overall = overallOverride != null
                ? overallOverride
                : (state.hasShortage ? "AT_RISK" : (state.hasSupplyOrderPeg ? "PLANNED" : "ON_TRACK"));
        String kitting = state.hasShortage ? "SHORTAGE" : "KITTING_OK";
        return new OrderFulfillmentChainDto(
                ctx.salesOrderNo(),
                ctx.salesOrderLineNo(),
                ctx.productCode(),
                ctx.dueDate(),
                ctx.promiseDate(),
                overall,
                kitting,
                List.copyOf(state.nodes),
                List.copyOf(state.edges),
                List.of(),
                null);
    }

    private void expandSupplyOrderConsumers(
            OntologyGraph graph,
            String supplyOrderId,
            String fromNodeId,
            int depth,
            ProjectionState state) {
        if (!state.seenDownstreamSupplyOrders.add(supplyOrderId)) {
            return;
        }
        for (Fulfillment fulfillment : graph.fulfillmentsForSupplyOrder(supplyOrderId)) {
            expandDemandAsConsumer(graph, fulfillment, fromNodeId, depth, state);
        }
    }

    private void expandDemandAsConsumer(
            OntologyGraph graph,
            Fulfillment fulfillment,
            String fromNodeId,
            int depth,
            ProjectionState state) {
        String demandId = fulfillment.getDemandId();
        if (!state.seenDemands.add(demandId)) {
            return;
        }
        Demand demand = graph.demand(demandId);
        if (demand == null) {
            return;
        }
        if (demand.getSourceType() == DemandSourceType.CUSTOMER_DELIVERY) {
            String deliveryId = demand.getSourceId();
            CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(deliveryId);
            if (delivery == null) {
                return;
            }
            CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
            if (line == null) {
                return;
            }
            SalesOrderLineEntity orderLine = SalesOrderLineEntity.findByKey(
                    line.getSalesOrderNo(), line.getSalesOrderLineNo());
            String consumerNodeId = state.addSalesOrderNode(line, delivery, orderLine, depth);
            state.addEdge(fromNodeId, consumerNodeId, "DEMAND_PEG", fulfillment.getQuantity());
            return;
        }
        if (demand.getSourceType() == DemandSourceType.BOM_COMPONENT) {
            String parentSupplyOrderId = demand.getSourceId();
            if (parentSupplyOrderId == null || parentSupplyOrderId.isBlank()) {
                return;
            }
            Supply parentSupply = resolveRepresentativeSupply(graph, parentSupplyOrderId);
            if (parentSupply == null) {
                return;
            }
            String consumerNodeId = state.ensureSupplyOrderNode(
                    graph, parentSupply, fulfillment.getQuantity(), depth);
            if (consumerNodeId == null) {
                return;
            }
            state.addEdge(fromNodeId, consumerNodeId, "WORK_ORDER_PEG", fulfillment.getQuantity());
            expandSupplyOrderConsumers(graph, parentSupplyOrderId, consumerNodeId, depth + 1, state);
        }
    }

    private static Supply resolveRepresentativeSupply(OntologyGraph graph, String supplyOrderId) {
        List<Supply> supplies = graph.suppliesForSupplyOrder(supplyOrderId);
        if (!supplies.isEmpty()) {
            return supplies.get(0);
        }
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        if (supplyOrder == null) {
            return null;
        }
        return new Supply(
                OntologyIds.supplyId(supplyOrderId, 1),
                supplyOrder.getProductCode(),
                supplyOrder.getPispId(),
                supplyOrder.getQuantity(),
                supplyOrderId);
    }

    private void expandDemandFulfillments(
            OntologyGraph graph,
            String demandId,
            String demanderNodeId,
            int depth,
            ProjectionState state) {
        if (!state.seenDemands.add(demandId)) {
            return;
        }
        List<Fulfillment> fulfillments = graph.fulfillmentsForDemand(demandId).stream()
                .sorted(Comparator.comparing(ff -> ff.getType().name()))
                .toList();
        for (Fulfillment fulfillment : fulfillments) {
            Supply supply = graph.supply(fulfillment.getSupplyId());
            if (supply == null) {
                continue;
            }
            String supplierNodeId = state.ensureSupplierNode(graph, supply, fulfillment, depth);
            if (supplierNodeId == null) {
                continue;
            }
            state.addEdge(supplierNodeId, demanderNodeId, fulfillment.getType().name(), fulfillment.getQuantity());

            if (fulfillment.getType() == FulfillmentType.WORK_ORDER_PEG && supply.getSupplyOrderId() != null) {
                expandSupplyOrderMaterialNeeds(graph, supply.getSupplyOrderId(), supplierNodeId, depth + 1, state);
            }
        }
    }

    private void expandSupplyOrderMaterialNeeds(
            OntologyGraph graph,
            String supplyOrderId,
            String parentSupplierNodeId,
            int depth,
            ProjectionState state) {
        if (!state.seenSupplyOrders.add(supplyOrderId)) {
            return;
        }
        Set<String> childDemandIds = new HashSet<>();
        for (Operation operation : graph.operationsForSupplyOrder(supplyOrderId)) {
            for (OperationInputMaterial oim : graph.operationInputMaterialsForOperation(operation.getId())) {
                childDemandIds.add(oim.getDemandId());
            }
        }
        for (String childDemandId : childDemandIds) {
            expandDemandFulfillments(graph, childDemandId, parentSupplierNodeId, depth, state);
        }
    }

    private static final class ProjectionState {
        final List<FulfillmentChainNodeDto> nodes = new ArrayList<>();
        final List<FulfillmentPegEdgeDto> edges = new ArrayList<>();
        final Set<String> nodeIds = new HashSet<>();
        final Set<String> seenDemands = new HashSet<>();
        final Set<String> seenSupplyOrders = new HashSet<>();
        final Set<String> seenDownstreamSupplyOrders = new HashSet<>();
        final LocalDate dueDate;
        boolean hasShortage;
        boolean hasSupplyOrderPeg;
        String rootNodeId;

        ProjectionState(LocalDate dueDate) {
            this.dueDate = dueDate;
        }

        void markRoot(String nodeId) {
            this.rootNodeId = nodeId;
        }

        String addSalesOrderRoot(
                CustomerOrderLine line,
                CustomerOrderLineDelivery delivery,
                SalesOrderLineEntity orderLine) {
            return addSalesOrderNode(line, delivery, orderLine, 0);
        }

        String addSalesOrderNode(
                CustomerOrderLine line,
                CustomerOrderLineDelivery delivery,
                SalesOrderLineEntity orderLine,
                int depth) {
            String id = "so-" + line.getSalesOrderNo() + "-" + line.getSalesOrderLineNo();
            if (nodeIds.contains(id)) {
                return id;
            }
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            int priority = orderLine != null ? orderLine.priority : 0;
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("deliveryId", delivery.getId());
            attrs.put("dueDate", dueDate.toString());
            attrs.put("priority", priority);
            attrs.put("ontologyMode", true);
            addNode(new FulfillmentChainNodeDto(
                    id,
                    "SALES_ORDER",
                    "SALES_ORDER",
                    "客户交付 " + line.getSalesOrderNo() + "-" + line.getSalesOrderLineNo(),
                    "DEMAND",
                    depth,
                    line.getProductCode(),
                    BigDecimal.valueOf(delivery.getDeliveryQty()),
                    placeholder,
                    placeholder,
                    attrs,
                    List.of()));
            return id;
        }

        String ensureRootSupplyOrderNode(
                OntologyGraph graph,
                String supplyOrderId,
                SupplyOrder supplyOrder) {
            Supply supply = resolveRepresentativeSupply(graph, supplyOrderId);
            if (supply == null) {
                return null;
            }
            return ensureSupplyOrderNode(graph, supply, supplyOrder.getQuantity(), 0);
        }

        String ensureSupplierNode(
                OntologyGraph graph,
                Supply supply,
                Fulfillment fulfillment,
                int depth) {
            return switch (fulfillment.getType()) {
                case INVENTORY_PEG -> ensureInventoryNode(supply, fulfillment.getQuantity(), depth);
                case SHORTAGE_PEG -> ensureShortageNode(supply, fulfillment.getQuantity(), depth);
                case WORK_ORDER_PEG -> ensureSupplyOrderNode(graph, supply, fulfillment.getQuantity(), depth);
            };
        }

        String ensureInventoryNode(Supply supply, double qty, int depth) {
            String id = "inv-" + supply.getProductCode() + "-" + depth;
            if (nodeIds.contains(id)) {
                return id;
            }
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            Map<String, Object> attrs = Map.of(
                    "pegType", "INVENTORY_PEG",
                    "supplyId", supply.getId(),
                    "ontologyMode", true);
            addNode(node(
                    id,
                    "INVENTORY",
                    "库存 · " + supply.getProductCode(),
                    "OK",
                    depth,
                    supply.getProductCode(),
                    qty,
                    attrs,
                    List.of(),
                    placeholder,
                    placeholder));
            return id;
        }

        String ensureShortageNode(Supply supply, double qty, int depth) {
            hasShortage = true;
            String id = "short-" + supply.getProductCode() + "-" + depth;
            if (nodeIds.contains(id)) {
                return id;
            }
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            Map<String, Object> attrs = Map.of(
                    "pegType", "SHORTAGE_PEG",
                    "supplyId", supply.getId(),
                    "ontologyMode", true);
            addNode(node(
                    id,
                    "SHORTAGE",
                    "缺料 · " + supply.getProductCode(),
                    "SHORTAGE",
                    depth,
                    supply.getProductCode(),
                    qty,
                    attrs,
                    List.of(),
                    placeholder,
                    placeholder));
            return id;
        }

        String ensureSupplyOrderNode(OntologyGraph graph, Supply supply, double qty, int depth) {
            String supplyOrderId = supply.getSupplyOrderId();
            if (supplyOrderId == null || supplyOrderId.isBlank()) {
                return null;
            }
            hasSupplyOrderPeg = true;
            String id = "supo-" + supplyOrderId;
            if (nodeIds.contains(id)) {
                return id;
            }
            SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
            String productCode = supply.getProductCode();
            String status = supplyOrder != null && supplyOrder.getStatus() != null
                    ? supplyOrder.getStatus().name()
                    : "PLANNED";
            LocalDateTime placeholder = dueDate.atTime(WORKDAY_END);
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("pegType", "WORK_ORDER_PEG");
            attrs.put("supplyOrderId", supplyOrderId);
            attrs.put("workOrderNo", supplyOrderId);
            attrs.put("supplyId", supply.getId());
            attrs.put("ontologyMode", true);
            if (supplyOrder != null && supplyOrder.getType() != null) {
                attrs.put("supplyOrderType", supplyOrder.getType().name());
            }
            List<FulfillmentOperationDto> operations = buildOperations(graph, supplyOrderId, placeholder, placeholder);
            addNode(node(
                    id,
                    "SUPPLY_ORDER",
                    "供应订单 · " + supplyOrderId,
                    status,
                    depth,
                    productCode,
                    qty,
                    attrs,
                    operations,
                    placeholder,
                    placeholder));
            return id;
        }

        void addEdge(String from, String to, String pegType, double qty) {
            edges.add(new FulfillmentPegEdgeDto(from, to, pegType, pegLabel(pegType)));
        }

        void applyScheduleTimes(OntologyGraph graph, CustomerOrderLine line, LocalDate promiseDate) {
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

            LocalDateTime rootFulfill = dueDate.atTime(WORKDAY_END);
            if (promiseDate != null && !promiseDate.isAfter(dueDate)) {
                rootFulfill = promiseDate.atTime(WORKDAY_END);
            }

            for (FulfillmentChainNodeDto node : sorted) {
                boolean isRoot = rootNodeId != null && rootNodeId.equals(node.nodeId());
                if (isRoot) {
                    LocalDateTime planFulfill = rootFulfill;
                    LocalDateTime start = planFulfill.minusDays(7).with(WORKDAY_START);
                    if (!start.isBefore(planFulfill)) {
                        start = planFulfill.minusMinutes(MIN_BAR_MINUTES);
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

            for (int i = 0; i < nodes.size(); i++) {
                FulfillmentChainNodeDto node = nodes.get(i);
                LocalDateTime start = planStart.get(node.nodeId());
                LocalDateTime fulfill = planEnd.get(node.nodeId());
                if (start == null || fulfill == null) {
                    continue;
                }
                List<FulfillmentOperationDto> ops = node.operations();
                if ("SUPPLY_ORDER".equals(node.nodeType())) {
                    Object soId = node.attributes().get("supplyOrderId");
                    if (soId != null) {
                        ops = buildOperations(graph, soId.toString(), start, fulfill);
                    }
                }
                Map<String, Object> attrs = new LinkedHashMap<>(node.attributes());
                attrs.put("plannedStartTs", start.toString());
                attrs.put("plannedEndTs", fulfill.toString());
                nodes.set(i, node(
                        node.nodeId(),
                        node.nodeType(),
                        node.label(),
                        node.status(),
                        node.depth(),
                        node.productCode(),
                        node.quantity().doubleValue(),
                        attrs,
                        ops,
                        start,
                        fulfill));
            }
        }

        void applyPlannedSupplyOrderRollup(OntologyGraph graph) {
            for (int i = 0; i < nodes.size(); i++) {
                FulfillmentChainNodeDto node = nodes.get(i);
                if (!"SUPPLY_ORDER".equals(node.nodeType())) {
                    continue;
                }
                Object soId = node.attributes() != null ? node.attributes().get("supplyOrderId") : null;
                if (soId == null) {
                    continue;
                }
                LocalDateTime rollupStart = null;
                LocalDateTime rollupEnd = null;
                for (Operation operation : graph.operationsForSupplyOrder(soId.toString())) {
                    LocalDateTime start = operation.getPlannedStartTotal();
                    LocalDateTime end = operation.getPlannedEndTotal();
                    if (start != null && (rollupStart == null || start.isBefore(rollupStart))) {
                        rollupStart = start;
                    }
                    if (end != null && (rollupEnd == null || end.isAfter(rollupEnd))) {
                        rollupEnd = end;
                    }
                }
                if (rollupStart == null || rollupEnd == null) {
                    continue;
                }
                List<FulfillmentOperationDto> ops = buildOperations(
                        graph, soId.toString(), rollupStart, rollupEnd);
                Map<String, Object> attrs = new LinkedHashMap<>(node.attributes());
                attrs.put("plannedStartTs", rollupStart.toString());
                attrs.put("plannedEndTs", rollupEnd.toString());
                attrs.put("planningLayer", "OPTIMIZER");
                nodes.set(i, node(
                        node.nodeId(),
                        node.nodeType(),
                        node.label(),
                        node.status(),
                        node.depth(),
                        node.productCode(),
                        node.quantity().doubleValue(),
                        attrs,
                        ops,
                        rollupStart,
                        rollupEnd));
            }
        }

        void applyOptimizerMetadata(OptimizerResult optimizerResult, int trialRevision) {
            if (optimizerResult == null) {
                return;
            }
            List<Map<String, Object>> signalMaps = optimizerResult.diagnostics().stream()
                    .map(OntologyFulfillmentChainProjector::toSignalMap)
                    .toList();
            for (int i = 0; i < nodes.size(); i++) {
                FulfillmentChainNodeDto node = nodes.get(i);
                Map<String, Object> attrs = new LinkedHashMap<>(node.attributes());
                attrs.put("trialRevision", trialRevision);
                attrs.put("solverEngine", optimizerResult.engineId());
                if (!signalMaps.isEmpty()) {
                    attrs.put("planningSignals", signalMaps);
                }
                nodes.set(i, node(
                        node.nodeId(),
                        node.nodeType(),
                        node.label(),
                        node.status(),
                        node.depth(),
                        node.productCode(),
                        node.quantity().doubleValue(),
                        attrs,
                        node.operations(),
                        node.startTs(),
                        node.endTs()));
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

        private static int leadMinutes(FulfillmentChainNodeDto node) {
            if ("SUPPLY_ORDER".equals(node.nodeType()) && !node.operations().isEmpty()) {
                return node.operations().stream()
                        .mapToInt(FulfillmentOperationDto::durationMinutes)
                        .sum();
            }
            return MIN_BAR_MINUTES * Math.max(1, 4 - node.depth());
        }

        private void addNode(FulfillmentChainNodeDto node) {
            nodes.add(node);
            nodeIds.add(node.nodeId());
        }

        private static FulfillmentChainNodeDto node(
                String id,
                String nodeType,
                String label,
                String status,
                int depth,
                String product,
                double qty,
                Map<String, Object> attrs,
                List<FulfillmentOperationDto> operations,
                LocalDateTime start,
                LocalDateTime end) {
            return new FulfillmentChainNodeDto(
                    id,
                    nodeType,
                    nodeType,
                    label,
                    status,
                    depth,
                    product,
                    BigDecimal.valueOf(qty),
                    start,
                    end,
                    attrs,
                    operations);
        }
    }

    private static List<FulfillmentOperationDto> buildOperations(
            OntologyGraph graph,
            String supplyOrderId,
            LocalDateTime fallbackStart,
            LocalDateTime fallbackEnd) {
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrderId).stream()
                .sorted(Comparator.comparingInt(Operation::getSequenceNr))
                .toList();
        if (operations.isEmpty()) {
            return List.of();
        }
        List<FulfillmentOperationDto> ops = new ArrayList<>();
        for (Operation operation : operations) {
            LocalDateTime start = operation.getPlannedStartTotal() != null
                    ? operation.getPlannedStartTotal()
                    : operation.getEarliestPossibleStartTotal() != null
                            ? operation.getEarliestPossibleStartTotal()
                            : fallbackStart;
            LocalDateTime end = operation.getPlannedEndTotal() != null
                    ? operation.getPlannedEndTotal()
                    : operation.getEarliestPossibleEndTotal() != null
                            ? operation.getEarliestPossibleEndTotal()
                            : fallbackEnd;
            int duration = (int) Math.max(
                    MIN_BAR_MINUTES,
                    Duration.between(start, end).toMinutes());
            String resourceId = graph.operationsOnStandardResourceFor(operation.getId()).stream()
                    .findFirst()
                    .map(OperationOnStandardResource::getStandardResourceId)
                    .orElse("UNASSIGNED");
            PlanUnit planUnit = operation.getPlanUnitId() != null
                    ? graph.planUnit(operation.getPlanUnitId())
                    : null;
            ops.add(new FulfillmentOperationDto(
                    operation.getId(),
                    operation.getOperationName() != null ? operation.getOperationName() : operation.getId(),
                    operation.getRoutingSequenceNo() > 0
                            ? operation.getRoutingSequenceNo()
                            : operation.getSequenceNr(),
                    resourceId,
                    start,
                    end,
                    duration,
                    0,
                    operation.getPlanUnitId(),
                    planUnit != null ? planUnit.getSequenceNr() : null,
                    operation.getEarliestPossibleStartTotal(),
                    operation.getLatestDesiredEnd()));
        }
        return ops;
    }

    private static Map<String, Object> toSignalMap(PlanningDiagnostic diagnostic) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", diagnostic.severity());
        map.put("reasonCode", diagnostic.reasonCode());
        map.put("message", diagnostic.message());
        map.put("entityId", diagnostic.entityId());
        return map;
    }

    private static String pegLabel(String pegType) {
        if ("INVENTORY_PEG".equals(pegType)) {
            return "库存满足";
        }
        if ("WORK_ORDER_PEG".equals(pegType)) {
            return "供应订单满足";
        }
        if ("SHORTAGE_PEG".equals(pegType)) {
            return "缺料";
        }
        return pegType;
    }
}
