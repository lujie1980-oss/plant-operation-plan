package com.plantops.ontology.supply;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.ontology.OntologyGraph;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * B.5：按 {@link ParallelOperationRuleEntity} 为本体 {@link Operation} 标记并行工序组。
 * 与 {@link com.plantops.scenario.MasterPlanParallelBindingService} 配对逻辑一致（首道可上该线体资源的工序）。
 */
@ApplicationScoped
public class OperationParallelBindingService {

    @Inject
    BusinessRuleScopeService ruleScope;

    public int applyToGraph(OntologyGraph graph) {
        if (graph == null || graph.operationsById().isEmpty()) {
            return 0;
        }
        if (!ruleScope.isMasterPlanEnabled(BusinessRuleTypeIds.PARALLEL_OPERATIONS)) {
            return 0;
        }

        Map<String, List<OperationCandidate>> byOrderLine = indexByOrderLine(graph);
        int groups = 0;
        for (ParallelOperationRuleEntity rule : ParallelOperationRuleEntity.listInWorkspace()) {
            ProductionLineEntity line = ProductionLineEntity.findByLineId(rule.lineId);
            if (line == null || line.resourceId == null || line.resourceId.isBlank()) {
                continue;
            }
            String resourceId = line.resourceId;
            for (Map.Entry<String, List<OperationCandidate>> entry : byOrderLine.entrySet()) {
                Operation first = findLeadOperation(entry.getValue(), rule.firstProductCode, resourceId, graph);
                Operation second = findLeadOperation(entry.getValue(), rule.secondProductCode, resourceId, graph);
                if (first != null && second != null) {
                    String baseGroupId = "MPP-" + rule.id + "-" + entry.getKey() + "-" + rule.lineId;
                    linkPair(first, second, baseGroupId);
                    groups++;
                }
            }
        }
        return groups;
    }

    static Operation findLeadOperation(
            List<OperationCandidate> candidates,
            String productCode,
            String resourceId,
            OntologyGraph graph) {
        return candidates.stream()
                .filter(c -> productCode.equals(c.productCode()))
                .filter(c -> canUseResource(graph, c.operation(), resourceId))
                .min(Comparator
                        .comparingInt((OperationCandidate c) -> c.operation().getRoutingSequenceNo())
                        .thenComparingInt(c -> c.operation().getSegmentIndex()))
                .map(OperationCandidate::operation)
                .orElse(null);
    }

    private static void linkPair(Operation first, Operation second, String baseGroupId) {
        first.setParallelGroupId(baseGroupId);
        second.setParallelGroupId(baseGroupId);
    }

    private static boolean canUseResource(OntologyGraph graph, Operation operation, String resourceId) {
        if (resourceId == null) {
            return false;
        }
        if (resourceId.equals(OperationResourceBinding.primaryResourceId(graph, operation.getId()))) {
            return true;
        }
        return OperationResourceBinding.allowedResourceIds(graph, operation.getId()).contains(resourceId);
    }

    private static Map<String, List<OperationCandidate>> indexByOrderLine(OntologyGraph graph) {
        Map<String, List<OperationCandidate>> index = new HashMap<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            WorkOrderEntity workOrder = WorkOrderEntity.findByNo(supplyOrder.getId());
            if (workOrder == null || workOrder.salesOrderNo == null || workOrder.salesOrderNo.isBlank()) {
                continue;
            }
            String key = workOrder.salesOrderNo + "#" + workOrder.salesOrderLineNo;
            for (Operation operation : graph.operationsForSupplyOrder(supplyOrder.getId())) {
                index.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new OperationCandidate(operation, supplyOrder.getId(), supplyOrder.getProductCode()));
            }
        }
        return index;
    }

    record OperationCandidate(Operation operation, String supplyOrderId, String productCode) {
    }
}
