package com.plantops.scenario;

import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 并行工序规则绑定：两个料号在指定产线上需同时加工（同起同止）。
 * 仅一头存在时作为孤儿工序，可排到其它可生产产线。
 */
@ApplicationScoped
public class ParallelOperationBindingService {

    @Inject
    BusinessRuleScopeService ruleScope;

    public void applyBindings(List<OperationAssignment> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        if (!ruleScope.isDetailScheduleEnabled(BusinessRuleTypeIds.PARALLEL_OPERATIONS)) {
            return;
        }
        Map<String, WorkOrderEntity> workOrders = loadWorkOrders(operations);
        Map<String, List<OperationAssignment>> byOrderLine = indexByOrderLine(operations, workOrders);

        for (ParallelOperationRuleEntity rule : ParallelOperationRuleEntity.listInWorkspace()) {
            ProductionLineEntity line = ProductionLineEntity.findByLineId(rule.lineId);
            if (line == null || line.resourceId == null || line.resourceId.isBlank()) {
                continue;
            }
            String lineResourceId = line.resourceId;
            for (Map.Entry<String, List<OperationAssignment>> entry : byOrderLine.entrySet()) {
                List<OperationAssignment> candidates = entry.getValue().stream()
                        .filter(op -> lineResourceId.equals(op.getResourceId()))
                        .filter(op -> rule.firstProductCode.equals(op.getProductCode())
                                || rule.secondProductCode.equals(op.getProductCode()))
                        .toList();
                if (candidates.isEmpty()) {
                    continue;
                }
                OperationAssignment firstOp = findByProduct(candidates, rule.firstProductCode);
                OperationAssignment secondOp = findByProduct(candidates, rule.secondProductCode);
                if (firstOp != null && secondOp != null) {
                    linkPair(firstOp, secondOp, rule, entry.getKey());
                } else {
                    for (OperationAssignment orphan : candidates) {
                        markOrphan(orphan, rule);
                    }
                }
            }
        }
    }

    private static void linkPair(
            OperationAssignment firstOp,
            OperationAssignment secondOp,
            ParallelOperationRuleEntity rule,
            String orderLineKey) {
        String pairGroupId = "POP-" + rule.id + "-" + orderLineKey + "-" + rule.lineId;
        int pairedDuration = Math.max(firstOp.getDurationMinutes(), secondOp.getDurationMinutes());
        firstOp.setParallelPaired(true);
        secondOp.setParallelPaired(true);
        firstOp.setParallelOrphan(false);
        secondOp.setParallelOrphan(false);
        firstOp.setPairGroupId(pairGroupId);
        secondOp.setPairGroupId(pairGroupId);
        firstOp.setPairMateOperationId(secondOp.getOperationId());
        secondOp.setPairMateOperationId(firstOp.getOperationId());
        firstOp.setDesignatedLineId(rule.lineId);
        secondOp.setDesignatedLineId(rule.lineId);
        firstOp.setDurationMinutes(pairedDuration);
        secondOp.setDurationMinutes(pairedDuration);
        firstOp.setAllowedLineIds(List.of(rule.lineId));
        secondOp.setAllowedLineIds(List.of(rule.lineId));
    }

    private static void markOrphan(OperationAssignment op, ParallelOperationRuleEntity rule) {
        if (op.isParallelPaired()) {
            return;
        }
        op.setParallelOrphan(true);
        op.setDesignatedLineId(rule.lineId);
        op.setAllowedLineIds(resolveAllowedLineIds(op.getProductCode()));
        op.setAllowedResourceIds(MasterPlanParallelSlotSupport.resolveAllowedResourceIds(op.getProductCode()));
    }

    static List<String> resolveAllowedLineIds(String productCode) {
        LinkedHashSet<String> resourceIds = new LinkedHashSet<>();
        for (ProductResourceEntity row : ProductResourceEntity.findByProductOrdered(productCode)) {
            if (row.resourceId != null && !row.resourceId.isBlank()) {
                resourceIds.add(row.resourceId);
            }
        }
        LinkedHashSet<String> lineIds = new LinkedHashSet<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.lineId != null && resourceIds.contains(line.resourceId)) {
                lineIds.add(line.lineId);
            }
        }
        return List.copyOf(lineIds);
    }

    private static OperationAssignment findByProduct(List<OperationAssignment> ops, String productCode) {
        return ops.stream()
                .filter(op -> productCode.equals(op.getProductCode()))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, WorkOrderEntity> loadWorkOrders(List<OperationAssignment> operations) {
        Map<String, WorkOrderEntity> map = new HashMap<>();
        for (OperationAssignment op : operations) {
            if (op.getWorkOrderNo() == null || map.containsKey(op.getWorkOrderNo())) {
                continue;
            }
            WorkOrderEntity wo = WorkOrderEntity.findByNo(op.getWorkOrderNo());
            if (wo != null) {
                map.put(op.getWorkOrderNo(), wo);
            }
        }
        return map;
    }

    private static Map<String, List<OperationAssignment>> indexByOrderLine(
            List<OperationAssignment> operations,
            Map<String, WorkOrderEntity> workOrders) {
        Map<String, List<OperationAssignment>> index = new HashMap<>();
        for (OperationAssignment op : operations) {
            WorkOrderEntity wo = workOrders.get(op.getWorkOrderNo());
            if (wo == null) {
                continue;
            }
            String key = orderLineKey(wo);
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(op);
        }
        return index;
    }

    private static String orderLineKey(WorkOrderEntity wo) {
        return wo.salesOrderNo + "#" + wo.salesOrderLineNo;
    }
}
