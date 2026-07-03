package com.plantops.scenario;

import com.plantops.api.dto.CustomerOrderLineDeliveryListItemDto;
import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.DemandPoolSummaryDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyGraphSessionCache;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.fulfillment.OntologyFulfillmentChainProjector;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandbox;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxStore;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OntologyFulfillmentService {

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyGraphSessionCache graphSessionCache;

    @Inject
    OntologyFulfillmentChainProjector chainProjector;

    @Inject
    DemandService demandService;

    @Inject
    DeliveryPlanningSandboxStore deliveryPlanningSandboxStore;

    public List<CustomerOrderLineDeliveryListItemDto> listDeliveries(String masterPlanVersionId) {
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        return graph.customerOrderLineDeliveriesById().values().stream()
                .filter(delivery -> isActiveCustomerDelivery(graph, delivery))
                .sorted(Comparator
                        .comparingInt((CustomerOrderLineDelivery d) -> demandPriority(graph, d))
                        .thenComparing(d -> deliveryDueDate(graph, d)))
                .map(delivery -> toListItem(graph, delivery))
                .toList();
    }

    public DemandPoolSummaryDto deliverySummary(String masterPlanVersionId) {
        List<CustomerOrderLineDeliveryListItemDto> deliveries = listDeliveries(masterPlanVersionId);
        LocalDate today = LocalDate.now();
        int shortage = 0;
        int kittingOk = 0;
        int dueSoon = 0;
        int overdue = 0;
        int atRisk = 0;
        double totalQty = 0;

        for (CustomerOrderLineDeliveryListItemDto d : deliveries) {
            if ("SHORTAGE".equals(d.kittingStatus())) {
                shortage++;
            } else {
                kittingOk++;
            }
            if ("AT_RISK".equals(d.fulfillmentStatus())) {
                atRisk++;
            }
            LocalDate due = d.latestDesiredDate();
            if (due != null) {
                if (!due.isBefore(today) && !due.isAfter(today.plusDays(7))) {
                    dueSoon++;
                }
                if (due.isBefore(today)) {
                    overdue++;
                }
            }
            totalQty += d.deliveryQty();
        }

        List<DemandPoolKpiDto> kpis = List.of(
                kpi("TOTAL_DELIVERIES", "客户交付数", deliveries.size(), "条", "info"),
                kpi("KITTING_OK", "齐套OK", kittingOk, "条", "ok"),
                kpi("SHORTAGE", "缺料交付", shortage, "条", shortage > 0 ? "warn" : "ok"),
                kpi("AT_RISK", "满足风险", atRisk, "条", atRisk > 0 ? "warn" : "ok"),
                kpi("DUE_7D", "7日内交期", dueSoon, "条", "info"),
                kpi("OVERDUE", "已逾期", overdue, "条", overdue > 0 ? "danger" : "ok"),
                kpi("TOTAL_QTY", "总交付量", totalQty, "件", "info")
        );
        return new DemandPoolSummaryDto(kpis);
    }

    public String deliveryIdForOrderLine(String salesOrderNo, int salesOrderLineNo) {
        return OntologyIds.customerOrderLineDeliveryId(salesOrderNo, salesOrderLineNo, 0);
    }

    public OrderFulfillmentChainDto fulfillmentChainForOrderLine(
            String salesOrderNo, int salesOrderLineNo, String masterPlanVersionId) {
        return fulfillmentChain(deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo), masterPlanVersionId);
    }

    /** 轻量校验，避免建链前再装载全场景本体图。 */
    private static void requireDeliveryExists(String deliveryId) {
        OntologyIds.CustomerOrderLineDeliveryKey key =
                OntologyIds.parseCustomerOrderLineDeliveryId(deliveryId);
        if (key == null) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
        SalesOrderLineEntity line = SalesOrderLineEntity.findByKey(key.salesOrderNo(), key.salesOrderLineNo());
        if (line == null || "CANCELLED".equals(line.status)) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
        String expectedId = OntologyIds.customerOrderLineDeliveryId(
                key.salesOrderNo(), key.salesOrderLineNo(), key.deliverySeq());
        if (!expectedId.equals(deliveryId)) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
    }

    public CustomerOrderLineDelivery requireDelivery(String deliveryId, String masterPlanVersionId) {
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(deliveryId);
        if (delivery == null || !isActiveCustomerDelivery(graph, delivery)) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
        return delivery;
    }

    public CustomerOrderLine requireCustomerOrderLine(String deliveryId, String masterPlanVersionId) {
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        CustomerOrderLineDelivery delivery = requireDelivery(deliveryId, masterPlanVersionId);
        CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
        if (line == null) {
            throw new NotFoundException("Customer order line not found for delivery: " + deliveryId);
        }
        return line;
    }

    public OrderFulfillmentChainDto buildUpstreamChain(String deliveryId, String masterPlanVersionId) {
        requireDeliveryExists(deliveryId);
        LocalDate planningStart = LocalDate.now();
        OntologyGraph graph = ontologyLoader.buildUpstreamFulfillmentGraph(deliveryId, planningStart);
        authoritativeOntologyGraph.invalidate(WorkspaceResolver.currentWorkspaceId(), blankToNull(masterPlanVersionId));
        OrderFulfillmentChainDto chain = chainProjector.project(graph, deliveryId);
        if (chain == null) {
            throw new NotFoundException("Fulfillment chain not available for delivery: " + deliveryId);
        }
        CustomerOrderLine line = graph.customerOrderLine(
                graph.customerOrderLineDelivery(deliveryId).getCustomerOrderLineId());
        String kitting = line != null
                ? demandService.resolveKittingStatusPublic(line.getSalesOrderNo(), line.getSalesOrderLineNo())
                : chain.kittingStatus();
        return new OrderFulfillmentChainDto(
                chain.salesOrderNo(),
                chain.salesOrderLineNo(),
                chain.productCode(),
                chain.dueDate(),
                chain.promiseDate(),
                chain.overallStatus(),
                kitting,
                chain.nodes(),
                chain.edges(),
                chain.utilizationBuckets(),
                deliveryId);
    }

    public OrderFulfillmentChainDto fulfillmentChain(String deliveryId, String masterPlanVersionId) {
        DeliveryPlanningSandbox sandbox = deliveryPlanningSandboxStore.findByDelivery(
                WorkspaceResolver.currentWorkspaceId(), deliveryId);
        if (sandbox != null && sandbox.trialRevision() > 0) {
            return enrichChain(
                    chainProjector.project(
                            sandbox.graph(),
                            deliveryId,
                            sandbox.lastOptimizerResult(),
                            sandbox.trialRevision()),
                    sandbox.graph(),
                    deliveryId);
        }
        return fulfillmentChainFromDatabase(deliveryId, masterPlanVersionId);
    }

    OrderFulfillmentChainDto fulfillmentChainFromDatabase(String deliveryId, String masterPlanVersionId) {
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(deliveryId);
        if (delivery == null || !isActiveCustomerDelivery(graph, delivery)) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
        OrderFulfillmentChainDto chain = chainProjector.project(graph, deliveryId);
        if (chain == null) {
            throw new NotFoundException("Fulfillment chain not available for delivery: " + deliveryId);
        }
        return enrichChain(chain, graph, deliveryId);
    }

    /** 取消计划等只读刷新：使用权威 ENT-OG（ADR-07），不再装载投影图。 */
    public OrderFulfillmentChainDto fulfillmentChainFromDeliveryScoped(String deliveryId, String masterPlanVersionId) {
        return fulfillmentChainFromDatabase(deliveryId, masterPlanVersionId);
    }

    public OrderFulfillmentChainDto supplyOrderUpstreamChain(String workOrderNo, String masterPlanVersionId) {
        requireSupplyOrderExists(workOrderNo);
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        OrderFulfillmentChainDto chain = chainProjector.projectUpstreamForSupplyOrder(graph, workOrderNo);
        if (chain == null) {
            throw new NotFoundException("Upstream fulfillment chain not available for work order: " + workOrderNo);
        }
        return enrichWorkOrderChain(chain, workOrderNo);
    }

    public OrderFulfillmentChainDto supplyOrderDownstreamChain(String workOrderNo, String masterPlanVersionId) {
        requireSupplyOrderExists(workOrderNo);
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        OrderFulfillmentChainDto chain = chainProjector.projectDownstreamForSupplyOrder(graph, workOrderNo);
        if (chain == null) {
            throw new NotFoundException("Downstream fulfillment chain not available for work order: " + workOrderNo);
        }
        return enrichWorkOrderChain(chain, workOrderNo);
    }

    private static void requireSupplyOrderExists(String workOrderNo) {
        if (WorkOrderEntity.findByNo(workOrderNo) == null) {
            throw new NotFoundException("Work order not found: " + workOrderNo);
        }
    }

    private OrderFulfillmentChainDto enrichWorkOrderChain(OrderFulfillmentChainDto chain, String workOrderNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            return chain;
        }
        String kitting = wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()
                ? resolveKittingStatus(wo.salesOrderNo, wo.salesOrderLineNo)
                : chain.kittingStatus();
        return new OrderFulfillmentChainDto(
                chain.salesOrderNo(),
                chain.salesOrderLineNo(),
                chain.productCode(),
                chain.dueDate(),
                chain.promiseDate(),
                chain.overallStatus(),
                kitting,
                chain.nodes(),
                chain.edges(),
                chain.utilizationBuckets(),
                chain.deliveryId());
    }

    private OrderFulfillmentChainDto enrichChain(
            OrderFulfillmentChainDto chain,
            OntologyGraph graph,
            String deliveryId) {
        if (chain == null) {
            throw new NotFoundException("Fulfillment chain not available for delivery: " + deliveryId);
        }
        CustomerOrderLineDelivery delivery = graph.customerOrderLineDelivery(deliveryId);
        CustomerOrderLine line = delivery != null
                ? graph.customerOrderLine(delivery.getCustomerOrderLineId())
                : null;
        String kitting = line != null
                ? demandService.resolveKittingStatusPublic(line.getSalesOrderNo(), line.getSalesOrderLineNo())
                : chain.kittingStatus();
        return new OrderFulfillmentChainDto(
                chain.salesOrderNo(),
                chain.salesOrderLineNo(),
                chain.productCode(),
                chain.dueDate(),
                chain.promiseDate(),
                chain.overallStatus(),
                kitting,
                chain.nodes(),
                chain.edges(),
                chain.utilizationBuckets(),
                deliveryId);
    }

    private OntologyGraph loadGraph(String masterPlanVersionId) {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        return graphSessionCache.getOrLoad(masterPlanVersionId, () ->
                authoritativeOntologyGraph.getOrLoad(workspaceId, masterPlanVersionId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isActiveCustomerDelivery(OntologyGraph graph, CustomerOrderLineDelivery delivery) {
        if ("CANCELLED".equals(delivery.getStatus())) {
            return false;
        }
        String demandId = OntologyIds.demandFromCustomerDeliveryId(delivery.getId());
        Demand demand = graph.demand(demandId);
        return demand != null && demand.getSourceType() == com.plantops.ontology.demand.DemandSourceType.CUSTOMER_DELIVERY;
    }

    private CustomerOrderLineDeliveryListItemDto toListItem(OntologyGraph graph, CustomerOrderLineDelivery delivery) {
        CustomerOrderLine line = graph.customerOrderLine(delivery.getCustomerOrderLineId());
        SalesOrderLineEntity orderLine = line != null
                ? SalesOrderLineEntity.findByKey(line.getSalesOrderNo(), line.getSalesOrderLineNo())
                : null;
        String kitting = line != null
                ? resolveKittingStatus(line.getSalesOrderNo(), line.getSalesOrderLineNo())
                : "UNKNOWN";
        return new CustomerOrderLineDeliveryListItemDto(
                delivery.getId(),
                delivery.getCustomerOrderLineId(),
                line != null ? line.getSalesOrderNo() : "",
                line != null ? line.getSalesOrderLineNo() : 0,
                line != null ? line.getProductCode() : "",
                delivery.getDeliveryQty(),
                delivery.getRequestedDate(),
                delivery.getLatestDesiredDate(),
                orderLine != null ? orderLine.promiseDate : delivery.getRequestedDate(),
                line != null ? demandPriority(graph, delivery) : 0,
                delivery.getStatus(),
                kitting,
                resolveFulfillmentStatus(graph, delivery.getId()));
    }

    private static int demandPriority(OntologyGraph graph, CustomerOrderLineDelivery delivery) {
        Demand demand = graph.demand(OntologyIds.demandFromCustomerDeliveryId(delivery.getId()));
        return demand != null ? demand.getPriority() : 0;
    }

    private static LocalDate deliveryDueDate(OntologyGraph graph, CustomerOrderLineDelivery delivery) {
        if (delivery.getLatestDesiredDate() != null) {
            return delivery.getLatestDesiredDate();
        }
        Demand demand = graph.demand(OntologyIds.demandFromCustomerDeliveryId(delivery.getId()));
        return demand != null ? demand.getNeedDate() : LocalDate.MAX;
    }

    private static String resolveFulfillmentStatus(OntologyGraph graph, String deliveryId) {
        String demandId = OntologyIds.demandFromCustomerDeliveryId(deliveryId);
        List<Fulfillment> fulfillments = graph.fulfillmentsForDemand(demandId);
        if (fulfillments.isEmpty()) {
            return "PENDING";
        }
        boolean shortage = fulfillments.stream().anyMatch(ff -> ff.getType() == FulfillmentType.SHORTAGE_PEG);
        if (shortage) {
            return "AT_RISK";
        }
        boolean supplyOrder = fulfillments.stream().anyMatch(ff -> ff.getType() == FulfillmentType.WORK_ORDER_PEG);
        if (supplyOrder) {
            return "PLANNED";
        }
        return "ON_TRACK";
    }

    private String resolveKittingStatus(String salesOrderNo, int salesOrderLineNo) {
        KittingResultEntity r = KittingResultEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2 order by computedTs desc",
                        salesOrderNo, salesOrderLineNo)
                .firstResult();
        if (r != null) {
            return r.kittingStatus;
        }
        return demandService.resolveKittingStatusPublic(salesOrderNo, salesOrderLineNo);
    }

    private static DemandPoolKpiDto kpi(String id, String label, double value, String unit, String severity) {
        return new DemandPoolKpiDto(id, label, value, unit, severity);
    }
}
