package com.plantops.scenario;

import com.plantops.api.dto.OrderLineWorkOrderDto;
import com.plantops.api.dto.WorkOrderDto;
import com.plantops.api.dto.WorkOrderOrderLineTreeDto;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 按销售订单行 + pegging 筛工单，再按 BOM 依赖拼订单视角工单树。
 */
@ApplicationScoped
public class WorkOrderOrderLineTreeService {

    @Inject
    WorkOrderService workOrderService;

    public WorkOrderOrderLineTreeDto buildTree(String salesOrderNo, int salesOrderLineNo) {
        return buildTree(salesOrderNo, salesOrderLineNo, null);
    }

    public WorkOrderOrderLineTreeDto buildTree(
            String salesOrderNo,
            int salesOrderLineNo,
            String masterPlanVersionId) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, salesOrderLineNo);
        if (order == null) {
            throw new NotFoundException("Sales order line not found: " + salesOrderNo + "-" + salesOrderLineNo);
        }

        List<WorkOrderPeggingEntity> pegs = WorkOrderPeggingEntity.findByOrderLine(salesOrderNo, salesOrderLineNo);
        if (pegs.isEmpty()) {
            return new WorkOrderOrderLineTreeDto(
                    salesOrderNo,
                    salesOrderLineNo,
                    order.productCode,
                    order.dueDate,
                    List.of());
        }

        Map<String, BigDecimal> peggedQtyByWo = new LinkedHashMap<>();
        Set<String> woNos = new HashSet<>();
        for (WorkOrderPeggingEntity peg : pegs) {
            woNos.add(peg.workOrderNo);
            peggedQtyByWo.merge(
                    peg.workOrderNo,
                    peg.peggedQty != null ? peg.peggedQty : BigDecimal.ZERO,
                    BigDecimal::add);
        }

        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (WorkOrderBomDependencyEntity dep : WorkOrderBomDependencyEntity.listInWorkspace()) {
            if (!woNos.contains(dep.parentWorkOrderNo) || !woNos.contains(dep.childWorkOrderNo)) {
                continue;
            }
            childrenByParent
                    .computeIfAbsent(dep.parentWorkOrderNo, k -> new ArrayList<>())
                    .add(dep.childWorkOrderNo);
        }

        List<String> roots = woNos.stream()
                .map(WorkOrderEntity::findByNo)
                .filter(wo -> wo != null && wo.bomLevel == 0)
                .map(wo -> wo.workOrderNo)
                .sorted()
                .toList();
        if (roots.isEmpty()) {
            roots = woNos.stream()
                    .map(WorkOrderEntity::findByNo)
                    .filter(wo -> wo != null)
                    .min(Comparator.comparingInt((WorkOrderEntity wo) -> wo.bomLevel)
                            .thenComparing(wo -> wo.workOrderNo))
                    .map(wo -> List.of(wo.workOrderNo))
                    .orElse(List.of());
        }

        Map<String, String> treeParentByWo = new HashMap<>();
        for (String root : roots) {
            treeParentByWo.putIfAbsent(root, null);
            Queue<String> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                String parent = queue.poll();
                List<String> children = new ArrayList<>(childrenByParent.getOrDefault(parent, List.of()));
                children.sort(String::compareTo);
                for (String child : children) {
                    if (treeParentByWo.containsKey(child)) {
                        continue;
                    }
                    treeParentByWo.put(child, parent);
                    queue.add(child);
                }
            }
        }
        for (String woNo : woNos) {
            treeParentByWo.putIfAbsent(woNo, null);
        }

        List<OrderLineWorkOrderDto> nodes = new ArrayList<>();
        for (String woNo : sortByBomTraversal(roots, childrenByParent, woNos)) {
            WorkOrderEntity wo = WorkOrderEntity.findByNo(woNo);
            if (wo == null) {
                continue;
            }
            WorkOrderDto dto = workOrderService.toWorkOrderDto(wo, masterPlanVersionId);
            nodes.add(new OrderLineWorkOrderDto(
                    dto,
                    treeParentByWo.get(woNo),
                    peggedQtyByWo.getOrDefault(woNo, BigDecimal.ZERO)));
        }

        return new WorkOrderOrderLineTreeDto(
                salesOrderNo,
                salesOrderLineNo,
                order.productCode,
                order.dueDate,
                nodes);
    }

    private static List<String> sortByBomTraversal(
            List<String> roots,
            Map<String, List<String>> childrenByParent,
            Set<String> woNos) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String root : roots) {
            appendPreorder(root, childrenByParent, seen, ordered);
        }
        woNos.stream().filter(no -> !seen.contains(no)).sorted().forEach(ordered::add);
        return ordered;
    }

    private static void appendPreorder(
            String woNo,
            Map<String, List<String>> childrenByParent,
            Set<String> seen,
            List<String> ordered) {
        if (!seen.add(woNo)) {
            return;
        }
        ordered.add(woNo);
        List<String> children = new ArrayList<>(childrenByParent.getOrDefault(woNo, List.of()));
        children.sort(String::compareTo);
        for (String child : children) {
            appendPreorder(child, childrenByParent, seen, ordered);
        }
    }
}
