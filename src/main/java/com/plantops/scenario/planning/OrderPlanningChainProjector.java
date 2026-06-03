package com.plantops.scenario.planning;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentOperationDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.*;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrderPlanningChainProjector {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);

    public static OrderPlanningChainDto project(
            OrderFulfillmentChainDto topology,
            MasterPlanPlanningContext mpCtx,
            DetailSchedulePlanningContext dsCtx,
            List<String> chainWorkOrderNos) {
        return project(topology, mpCtx, dsCtx, chainWorkOrderNos, null, null);
    }

    public static OrderPlanningChainDto project(
            OrderFulfillmentChainDto topology,
            MasterPlanPlanningContext mpCtx,
            DetailSchedulePlanningContext dsCtx,
            List<String> chainWorkOrderNos,
            String baselineVersionId,
            BaselineWindowResolver baselineResolver) {
        Set<String> woSet = new LinkedHashSet<>(chainWorkOrderNos);
        Map<String, List<OrderAllocation>> allocByWo = mpCtx.orderAllocations().stream()
                .filter(a -> woSet.contains(a.getWorkOrderNo()))
                .collect(Collectors.groupingBy(OrderAllocation::getWorkOrderNo));

        Map<String, List<OperationAssignment>> opByWo = dsCtx != null
                ? dsCtx.operations().stream()
                        .filter(o -> woSet.contains(o.getWorkOrderNo()))
                        .collect(Collectors.groupingBy(OperationAssignment::getWorkOrderNo))
                : Map.of();

        List<OrderPlanningChainNodeDto> nodes = new ArrayList<>();
        for (FulfillmentChainNodeDto src : topology.nodes()) {
            nodes.add(mapNode(src, allocByWo, opByWo, mpCtx, baselineVersionId, baselineResolver));
        }
        nodes = rollupParentWindows(topology, nodes);

        String overall = aggregateStatus(nodes);
        OrderPlanningChainSummaryDto summary = buildSummary(mpCtx, dsCtx, woSet, nodes);

        OrderPlanningChainCompareDto compare = baselineVersionId != null && baselineResolver != null
                ? buildCompare(baselineVersionId, nodes, baselineResolver)
                : null;

        return new OrderPlanningChainDto(
                topology.salesOrderNo(),
                topology.salesOrderLineNo(),
                topology.productCode(),
                topology.dueDate(),
                topology.promiseDate(),
                overall,
                topology.kittingStatus(),
                summary,
                nodes,
                topology.edges(),
                compare);
    }

    static OrderPlanningChainNodeDto mapNode(
            FulfillmentChainNodeDto src,
            Map<String, List<OrderAllocation>> allocByWo,
            Map<String, List<OperationAssignment>> opByWo,
            MasterPlanPlanningContext mpCtx,
            String baselineVersionId,
            BaselineWindowResolver baselineResolver) {
        String nodeType = src.nodeType();
        String workOrderNo = workOrderNoFrom(src);
        List<OrderAllocation> woAllocs = workOrderNo != null
                ? allocByWo.getOrDefault(workOrderNo, List.of())
                : List.of();
        List<OperationAssignment> woOps = workOrderNo != null
                ? opByWo.getOrDefault(workOrderNo, List.of())
                : List.of();

        List<PlanningSignalDto> signals = new ArrayList<>();
        LocalDate windowStart = null;
        LocalDate windowEnd = null;
        String status;
        String planningLayer = "PEG";
        Map<String, Object> attributes = new LinkedHashMap<>(src.attributes() != null ? src.attributes() : Map.of());
        List<FulfillmentOperationDto> operations = List.of();

        if ("WORK_ORDER".equals(nodeType)) {
            planningLayer = dsCtxPresent(woOps) ? "S05" : "S04";
            signals.addAll(signalsForWorkOrder(mpCtx, woAllocs, workOrderNo));
            if (dsCtxPresent(woOps)) {
                signals.addAll(signalsForDetailOps(woOps));
            }
            if (woAllocs.isEmpty()) {
                status = "BLOCKED";
                signals.add(new PlanningSignalDto(
                        "SKIP",
                        PlanningDiagnosticCodes.WO_NO_ALLOCATIONS,
                        "工单未进入主计划候选分配",
                        null));
            } else if (hasEmptyEligible(woAllocs)) {
                status = "BLOCKED";
                if (signals.stream().noneMatch(s -> PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS.equals(s.reasonCode()))) {
                    signals.add(new PlanningSignalDto(
                            "WARN",
                            PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS,
                            "工序分配无 eligible 槽位",
                            woAllocs.get(0).getId()));
                }
            } else {
                windowStart = minSlotDate(woAllocs);
                windowEnd = maxSlotDate(woAllocs);
                status = signals.stream().anyMatch(s -> "SKIP".equals(s.severity()))
                        ? "BLOCKED"
                        : signals.stream().anyMatch(s -> "WARN".equals(s.severity())) ? "WARN" : "OK";
            }
            attributes.put("workOrderNo", workOrderNo);
            attributes.put("allocationCount", woAllocs.size());
            attributes.put("eligibleSlotCount", totalEligibleSlots(woAllocs));
            operations = buildOperationsFromAllocations(woAllocs);
            if (dsCtxPresent(woOps)) {
                operations = mergeDetailOperations(operations, woOps);
            }
        } else if ("SALES_ORDER".equals(nodeType)) {
            windowEnd = mpCtx.planningStart() != null ? topologyDueEnd(src, mpCtx) : src.endTs() != null
                    ? src.endTs().toLocalDate()
                    : null;
            status = "OK";
        } else if ("SHORTAGE".equals(nodeType)) {
            status = "BLOCKED";
            signals.add(new PlanningSignalDto(
                    "SKIP",
                    PlanningDiagnosticCodes.WO_KITTING_SHORT,
                    "物料短缺节点",
                    null));
        } else if ("INVENTORY".equals(nodeType)) {
            planningLayer = "S04";
            LocalDate checkDate = mpCtx.planningStart();
            BigDecimal closing = mpCtx.materialFeasibility() != null
                    ? mpCtx.materialFeasibility().closingOn(src.productCode(), checkDate)
                    : BigDecimal.ZERO;
            if (closing.compareTo(BigDecimal.ZERO) < 0) {
                status = "WARN";
                signals.add(new PlanningSignalDto(
                        "WARN",
                        PlanningDiagnosticCodes.WO_KITTING_SHORT,
                        "MRP 期初闭合不足 " + src.productCode(),
                        null));
            } else {
                status = "OK";
            }
            windowStart = checkDate;
            windowEnd = checkDate;
        } else {
            status = pegStatusToPlanning(src.status());
            if (src.startTs() != null) {
                windowStart = src.startTs().toLocalDate();
            }
            if (src.endTs() != null) {
                windowEnd = src.endTs().toLocalDate();
            }
        }

        if (baselineVersionId != null && baselineResolver != null && workOrderNo != null) {
            LocalDate[] baseline = baselineResolver.resolve(workOrderNo);
            if (baseline != null && baseline.length == 2) {
                attributes.put("baselineWindowStart", baseline[0].toString());
                attributes.put("baselineWindowEnd", baseline[1].toString());
            }
        }

        return new OrderPlanningChainNodeDto(
                src.nodeId(),
                nodeType,
                src.laneId(),
                src.label(),
                status,
                src.depth(),
                src.productCode(),
                src.quantity(),
                windowStart,
                windowEnd,
                planningLayer,
                List.copyOf(signals),
                attributes,
                operations);
    }

    private static List<OrderPlanningChainNodeDto> rollupParentWindows(
            OrderFulfillmentChainDto topology,
            List<OrderPlanningChainNodeDto> nodes) {
        Map<String, OrderPlanningChainNodeDto> byId = nodes.stream()
                .collect(Collectors.toMap(OrderPlanningChainNodeDto::nodeId, n -> n, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (var edge : topology.edges()) {
            childrenByParent.computeIfAbsent(edge.toNodeId(), k -> new ArrayList<>()).add(edge.fromNodeId());
        }

        List<OrderPlanningChainNodeDto> updated = new ArrayList<>();
        for (OrderPlanningChainNodeDto node : nodes) {
            if (!"SALES_ORDER".equals(node.nodeType())) {
                updated.add(node);
                continue;
            }
            List<String> childIds = childrenByParent.getOrDefault(node.nodeId(), List.of());
            LocalDate minStart = null;
            LocalDate maxEnd = node.windowEnd();
            String worst = node.status();
            for (String childId : childIds) {
                OrderPlanningChainNodeDto child = byId.get(childId);
                if (child == null) {
                    continue;
                }
                if (child.windowStart() != null) {
                    minStart = minStart == null ? child.windowStart() : min(minStart, child.windowStart());
                }
                if (child.windowEnd() != null) {
                    maxEnd = maxEnd == null ? child.windowEnd() : max(maxEnd, child.windowEnd());
                }
                worst = worstStatus(worst, child.status());
            }
            List<PlanningSignalDto> signals = new ArrayList<>(node.planningSignals());
            updated.add(new OrderPlanningChainNodeDto(
                    node.nodeId(),
                    node.nodeType(),
                    node.laneId(),
                    node.label(),
                    worst,
                    node.depth(),
                    node.productCode(),
                    node.quantity(),
                    minStart,
                    maxEnd != null ? maxEnd : node.windowEnd(),
                    node.planningLayer(),
                    signals,
                    node.attributes(),
                    node.operations()));
        }
        return updated;
    }

    static LocalDate minSlotDate(List<OrderAllocation> allocations) {
        return allocations.stream()
                .flatMap(a -> a.getEligibleTimeSlots().stream())
                .map(TimeSlot::getDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    static LocalDate maxSlotDate(List<OrderAllocation> allocations) {
        return allocations.stream()
                .flatMap(a -> a.getEligibleTimeSlots().stream())
                .map(TimeSlot::getDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    static boolean hasEmptyEligible(List<OrderAllocation> allocations) {
        return allocations.stream().anyMatch(a -> a.getEligibleTimeSlots() == null || a.getEligibleTimeSlots().isEmpty());
    }

    static int totalEligibleSlots(List<OrderAllocation> allocations) {
        return allocations.stream()
                .mapToInt(a -> a.getEligibleTimeSlots() != null ? a.getEligibleTimeSlots().size() : 0)
                .sum();
    }

    private static List<PlanningSignalDto> signalsForWorkOrder(
            MasterPlanPlanningContext mpCtx,
            List<OrderAllocation> woAllocs,
            String workOrderNo) {
        List<PlanningSignalDto> signals = new ArrayList<>();
        if (mpCtx.diagnostics() != null && mpCtx.diagnostics().issues() != null) {
            for (PlanningDiagnosticIssue issue : mpCtx.diagnostics().issues()) {
                if (workOrderNo != null && workOrderNo.equals(issue.workOrderNo())) {
                    signals.add(toSignal(issue));
                }
            }
        }
        for (OrderAllocation alloc : woAllocs) {
            if (alloc.getEligibleTimeSlots() == null || alloc.getEligibleTimeSlots().isEmpty()) {
                continue;
            }
            if (mpCtx.workOrderTimingBounds() != null) {
                boolean anyFeasible = alloc.getEligibleTimeSlots().stream()
                        .anyMatch(s -> mpCtx.workOrderTimingBounds().slotAllowed(alloc.getWorkOrderNo(), s));
                if (!anyFeasible && signals.stream().noneMatch(s ->
                        PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK.equals(s.reasonCode())
                                && alloc.getId().equals(s.entityId()))) {
                    signals.add(new PlanningSignalDto(
                            "WARN",
                            PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK,
                            "时窗回退：无「不早于最早可行」槽位",
                            alloc.getId()));
                }
            }
        }
        return signals;
    }

    private static List<PlanningSignalDto> signalsForDetailOps(List<OperationAssignment> woOps) {
        List<PlanningSignalDto> signals = new ArrayList<>();
        for (OperationAssignment op : woOps) {
            if (!op.isKittingEligible()) {
                signals.add(new PlanningSignalDto(
                        "WARN",
                        PlanningDiagnosticCodes.WO_KITTING_SHORT,
                        "工序未齐套",
                        op.getOperationId()));
            }
            if (op.getMpContractStartDate() == null && op.getMpTargetEndDate() != null) {
                signals.add(new PlanningSignalDto(
                        "WARN",
                        PlanningDiagnosticCodes.OP_MP_TARGET_FALLBACK,
                        "无工序级主计划契约，使用末槽回退",
                        op.getOperationId()));
            }
        }
        return signals;
    }

    private static PlanningSignalDto toSignal(PlanningDiagnosticIssue issue) {
        return new PlanningSignalDto(
                issue.severity(),
                issue.reasonCode(),
                issue.message(),
                issue.entityId());
    }

    private static List<FulfillmentOperationDto> buildOperationsFromAllocations(List<OrderAllocation> allocations) {
        List<FulfillmentOperationDto> ops = new ArrayList<>();
        List<OrderAllocation> sorted = new ArrayList<>(allocations);
        sorted.sort(Comparator.comparingInt(OrderAllocation::getOperationSeq)
                .thenComparingInt(OrderAllocation::getSegmentIndex));
        for (OrderAllocation alloc : sorted) {
            LocalDate startDate = minSlotDate(List.of(alloc));
            LocalDate endDate = maxSlotDate(List.of(alloc));
            LocalDateTime start = startDate != null ? startDate.atTime(WORKDAY_START) : null;
            LocalDateTime end = endDate != null ? endDate.atTime(WORKDAY_END) : null;
            ops.add(new FulfillmentOperationDto(
                    alloc.getId(),
                    alloc.getOperationName() != null ? alloc.getOperationName() : "OP" + alloc.getOperationSeq(),
                    alloc.getOperationSeq(),
                    alloc.getResourceId(),
                    start,
                    end,
                    alloc.getDurationMinutes(),
                    0));
        }
        return ops;
    }

    private static List<FulfillmentOperationDto> mergeDetailOperations(
            List<FulfillmentOperationDto> fromMp,
            List<OperationAssignment> woOps) {
        if (woOps.isEmpty()) {
            return fromMp;
        }
        Map<Integer, OperationAssignment> bySeq = woOps.stream()
                .collect(Collectors.toMap(OperationAssignment::getOperationSeq, o -> o, (a, b) -> a));
        List<FulfillmentOperationDto> merged = new ArrayList<>();
        for (FulfillmentOperationDto op : fromMp) {
            OperationAssignment ds = bySeq.get(op.sequenceNo());
            if (ds == null) {
                merged.add(op);
                continue;
            }
            LocalDate cStart = ds.getMpContractStartDate();
            LocalDate cEnd = ds.getMpContractEndDate() != null ? ds.getMpContractEndDate() : ds.getMpTargetEndDate();
            LocalDateTime start = cStart != null ? cStart.atTime(WORKDAY_START) : op.startTs();
            LocalDateTime end = cEnd != null ? cEnd.atTime(WORKDAY_END) : op.endTs();
            merged.add(new FulfillmentOperationDto(
                    ds.getOperationId(),
                    op.operationName(),
                    op.sequenceNo(),
                    ds.getMpContractResourceId() != null ? ds.getMpContractResourceId() : op.resourceId(),
                    start,
                    end,
                    op.durationMinutes(),
                    op.utilizationPct()));
        }
        return merged;
    }

    private static OrderPlanningChainSummaryDto buildSummary(
            MasterPlanPlanningContext mpCtx,
            DetailSchedulePlanningContext dsCtx,
            Set<String> workOrderNos,
            List<OrderPlanningChainNodeDto> nodes) {
        Map<String, Integer> issueCounts = new LinkedHashMap<>();
        if (mpCtx.diagnostics() != null && mpCtx.diagnostics().issues() != null) {
            for (PlanningDiagnosticIssue issue : mpCtx.diagnostics().issues()) {
                if (issue.workOrderNo() != null && workOrderNos.contains(issue.workOrderNo())) {
                    issueCounts.merge(issue.severity(), 1, Integer::sum);
                }
            }
        }
        int opCount = mpCtx.orderAllocations().stream()
                .filter(a -> workOrderNos.contains(a.getWorkOrderNo()))
                .mapToInt(a -> 1)
                .sum();
        if (dsCtx != null) {
            opCount = (int) dsCtx.operations().stream()
                    .filter(o -> workOrderNos.contains(o.getWorkOrderNo()))
                    .count();
        }
        String snapshotId = mpCtx.materialPlanning() != null
                ? mpCtx.materialPlanning().inventorySnapshotId()
                : null;
        return new OrderPlanningChainSummaryDto(
                mpCtx.capacityStrategy() != null ? mpCtx.capacityStrategy().name() : null,
                snapshotId,
                workOrderNos.size(),
                opCount,
                issueCounts,
                Instant.now());
    }

    private static OrderPlanningChainCompareDto buildCompare(
            String baselineVersionId,
            List<OrderPlanningChainNodeDto> nodes,
            BaselineWindowResolver baselineResolver) {
        List<OrderPlanningChainNodeDeltaDto> deltas = new ArrayList<>();
        for (OrderPlanningChainNodeDto node : nodes) {
            if (!"WORK_ORDER".equals(node.nodeType())) {
                continue;
            }
            Object woAttr = node.attributes().get("workOrderNo");
            if (woAttr == null) {
                continue;
            }
            LocalDate[] baseline = baselineResolver.resolve(woAttr.toString());
            if (baseline == null || baseline.length < 2) {
                continue;
            }
            boolean changed = !Objects.equals(baseline[0], node.windowStart())
                    || !Objects.equals(baseline[1], node.windowEnd());
            deltas.add(new OrderPlanningChainNodeDeltaDto(
                    node.nodeId(),
                    baseline[0],
                    baseline[1],
                    node.windowStart(),
                    node.windowEnd(),
                    changed));
        }
        return new OrderPlanningChainCompareDto(baselineVersionId, deltas);
    }

    static String aggregateStatus(List<OrderPlanningChainNodeDto> nodes) {
        String overall = "OK";
        for (OrderPlanningChainNodeDto node : nodes) {
            overall = worstStatus(overall, node.status());
        }
        if ("WARN".equals(overall)) {
            return "AT_RISK";
        }
        return overall;
    }

    private static String worstStatus(String current, String next) {
        int c = statusRank(current);
        int n = statusRank(next);
        return n > c ? next : current;
    }

    private static int statusRank(String status) {
        if ("BLOCKED".equals(status) || "SKIPPED".equals(status) || "SHORTAGE".equals(status)) {
            return 3;
        }
        if ("WARN".equals(status) || "AT_RISK".equals(status)) {
            return 2;
        }
        return 1;
    }

    private static String pegStatusToPlanning(String pegStatus) {
        if ("SHORTAGE".equals(pegStatus) || "AT_RISK".equals(pegStatus)) {
            return "WARN";
        }
        return "OK";
    }

    private static boolean dsCtxPresent(List<OperationAssignment> woOps) {
        return woOps != null && !woOps.isEmpty();
    }

    private static String workOrderNoFrom(FulfillmentChainNodeDto src) {
        if (!"WORK_ORDER".equals(src.nodeType())) {
            return null;
        }
        if (src.attributes() != null && src.attributes().get("workOrderNo") != null) {
            return src.attributes().get("workOrderNo").toString();
        }
        return null;
    }

    private static LocalDate topologyDueEnd(FulfillmentChainNodeDto src, MasterPlanPlanningContext mpCtx) {
        if (src.endTs() != null) {
            return src.endTs().toLocalDate();
        }
        return mpCtx.planningStart();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    @FunctionalInterface
    public interface BaselineWindowResolver {
        LocalDate[] resolve(String workOrderNo);
    }
}
