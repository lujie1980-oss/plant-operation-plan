package com.plantops.scenario;

import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.knowledge.MaterialLeadTimeKnowledgeService;
import com.plantops.persistence.entity.OperationPostProcessingRuleEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class WorkOrderTimingService {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);

    @Inject
    ParameterRegistry parameters;

    @Inject
    BusinessRuleScopeService ruleScope;

    @Inject
    MaterialLeadTimeKnowledgeService materialLeadTimeKnowledge;

    private int procurementLeadTimeDays(String productCode) {
        return materialLeadTimeKnowledge.leadTimeDaysForProduct(productCode);
    }

    public WorkOrderTimingWindowDto compute(String workOrderNo, String masterPlanVersionId) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            return null;
        }
        int productionMinutes = ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
        int postProcessingMinutes = resolvePostProcessingMinutes(wo.productCode);
        Map<String, WorkOrderTimingWindowDto> memo = new HashMap<>();
        return computeRecursive(wo, masterPlanVersionId, productionMinutes, postProcessingMinutes, memo, new HashSet<>());
    }

    /** 主计划构建问题前：为全部工单预计算最早可行开始（不含当前计划槽位抬升）。 */
    public WorkOrderTimingBoundsContext buildMasterPlanBounds() {
        Map<String, LocalDateTime> earliestByWo = new HashMap<>();
        Map<String, WorkOrderTimingWindowDto> memo = new HashMap<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listAllOrdered()) {
            int productionMinutes = ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
            int postProcessingMinutes = resolvePostProcessingMinutes(wo.productCode);
            WorkOrderTimingWindowDto window = computeRecursive(
                    wo, null, productionMinutes, postProcessingMinutes, memo, new HashSet<>());
            if (window != null && window.earliestPossibleStart() != null) {
                earliestByWo.put(wo.workOrderNo, window.earliestPossibleStart());
            }
        }
        return new WorkOrderTimingBoundsContext(earliestByWo);
    }

    private WorkOrderTimingWindowDto computeRecursive(
            WorkOrderEntity wo,
            String versionId,
            int productionMinutes,
            int postProcessingMinutes,
            Map<String, WorkOrderTimingWindowDto> memo,
            Set<String> visiting) {
        if (memo.containsKey(wo.workOrderNo)) {
            return memo.get(wo.workOrderNo);
        }
        if (!visiting.add(wo.workOrderNo)) {
            return buildWindow(productionMinutes, postProcessingMinutes, LocalDateTime.now(), LocalDateTime.now());
        }

        LocalDateTime latestDelivery = resolveLatestDelivery(wo, versionId, memo, visiting);
        LocalDateTime latestEnd = latestDelivery.minusMinutes(postProcessingMinutes);
        LocalDateTime latestStart = latestEnd.minusMinutes(productionMinutes);
        if (!latestStart.isBefore(latestEnd)) {
            latestStart = latestEnd.minusMinutes(Math.max(1, productionMinutes));
        }

        LocalDateTime earliestStartOwn = resolveEarliestStartOwn(wo);
        LocalDateTime earliestEndOwn = earliestStartOwn.plusMinutes(productionMinutes);
        LocalDateTime earliestDeliveryOwn = earliestEndOwn.plusMinutes(postProcessingMinutes);

        LocalDateTime earliestStart = resolveEarliestStartWithUpstream(
                wo, versionId, memo, visiting, earliestStartOwn);
        LocalDateTime earliestEnd = earliestStart.plusMinutes(productionMinutes);
        LocalDateTime earliestDelivery = earliestEnd.plusMinutes(postProcessingMinutes);

        WorkOrderTimingWindowDto window = new WorkOrderTimingWindowDto(
                latestStart,
                latestEnd,
                latestDelivery,
                earliestStart,
                earliestEnd,
                earliestDelivery,
                earliestStartOwn,
                earliestEndOwn,
                earliestDeliveryOwn,
                productionMinutes,
                postProcessingMinutes);
        memo.put(wo.workOrderNo, window);
        visiting.remove(wo.workOrderNo);
        return window;
    }

    private LocalDateTime resolveLatestDelivery(
            WorkOrderEntity wo,
            String versionId,
            Map<String, WorkOrderTimingWindowDto> memo,
            Set<String> visiting) {
        List<WorkOrderBomDependencyEntity> parents = WorkOrderBomDependencyEntity.findByChild(wo.workOrderNo);
        if (!parents.isEmpty()) {
            LocalDateTime minParentStart = null;
            for (WorkOrderBomDependencyEntity dep : parents) {
                WorkOrderEntity parent = WorkOrderEntity.findByNo(dep.parentWorkOrderNo);
                if (parent == null) {
                    continue;
                }
                WorkOrderTimingWindowDto parentWindow = computeRecursive(
                        parent,
                        versionId,
                        ProductRoutingSteps.totalDurationMinutes(parent.productCode, parent.quantity),
                        resolvePostProcessingMinutes(parent.productCode),
                        memo,
                        visiting);
                LocalDateTime needBy = parentWindow.latestDesiredStart();
                minParentStart = minParentStart == null || needBy.isBefore(minParentStart) ? needBy : minParentStart;
            }
            if (minParentStart != null) {
                return minParentStart;
            }
        }

        if (wo.bomLevel == 0) {
            LocalDateTime minDue = null;
            for (WorkOrderPeggingEntity peg : WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo)) {
                SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(peg.salesOrderNo, peg.salesOrderLineNo);
                LocalDate due = order != null ? order.dueDate : peg.needDate;
                if (due == null) {
                    continue;
                }
                LocalDateTime dueTs = due.atTime(WORKDAY_END);
                minDue = minDue == null || dueTs.isBefore(minDue) ? dueTs : minDue;
            }
            if (minDue != null) {
                return minDue;
            }
        }

        if (wo.needDate != null) {
            return wo.needDate.atTime(WORKDAY_END);
        }
        MasterPlanAllocationEntity firstAlloc = findFirstAllocation(versionId, wo.workOrderNo);
        if (firstAlloc != null) {
            return firstAlloc.slotDate.atTime(WORKDAY_END);
        }
        return LocalDate.now().plusDays(7).atTime(WORKDAY_END);
    }

    private MasterPlanAllocationEntity findFirstAllocation(String versionId, String workOrderNo) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        return MasterPlanAllocationEntity
                .find("planVersionId = ?1 and workOrderNo = ?2 order by slotDate, slotIndex", versionId, workOrderNo)
                .firstResult();
    }

    private LocalDateTime resolveEarliestStartOwn(WorkOrderEntity wo) {
        LocalDate today = LocalDate.now();
        LocalDateTime stockReady = today.atTime(WORKDAY_START);
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity);
            BigDecimal available = InventoryEntity.listInWorkspace().stream()
                    .filter(i -> bom.componentProductCode.equals(i.productCode))
                    .map(InventoryEntity::availableQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(need) < 0) {
                // 缺料件按采购提前期到货；多个缺料件并行备料，取最迟一个（max），不累加。
                LocalDateTime ready = today.plusDays(procurementLeadTimeDays(bom.componentProductCode))
                        .atTime(WORKDAY_START);
                if (ready.isAfter(stockReady)) {
                    stockReady = ready;
                }
            }
        }
        return stockReady;
    }

    private LocalDateTime resolveEarliestStartWithUpstream(
            WorkOrderEntity wo,
            String versionId,
            Map<String, WorkOrderTimingWindowDto> memo,
            Set<String> visiting,
            LocalDateTime ownFloor) {
        LocalDateTime upstreamReady = ownFloor;
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            WorkOrderEntity childWo = WorkOrderEntity.findChildByDependency(wo.workOrderNo, bom.componentProductCode);
            if (childWo == null) {
                for (WorkOrderEntity candidate : WorkOrderEntity.findChildren(wo.workOrderNo)) {
                    if (bom.componentProductCode.equals(candidate.productCode)) {
                        childWo = candidate;
                        break;
                    }
                }
            }
            if (childWo != null) {
                // 有子件工单：取子件“最早可交付”，多子件并行取最迟一个（max）。
                WorkOrderTimingWindowDto childWindow = computeRecursive(
                        childWo,
                        versionId,
                        ProductRoutingSteps.totalDurationMinutes(childWo.productCode, childWo.quantity),
                        resolvePostProcessingMinutes(childWo.productCode),
                        memo,
                        visiting);
                if (childWindow.earliestPossibleDelivery().isAfter(upstreamReady)) {
                    upstreamReady = childWindow.earliestPossibleDelivery();
                }
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity);
            BigDecimal available = InventoryEntity.listInWorkspace().stream()
                    .filter(i -> bom.componentProductCode.equals(i.productCode))
                    .map(InventoryEntity::availableQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(need) < 0) {
                // 缺料且无子件工单：按采购提前期到货，并行取最迟一个（max），与自身阶段一致不重复累加。
                LocalDateTime ready = LocalDate.now()
                        .plusDays(procurementLeadTimeDays(bom.componentProductCode))
                        .atTime(WORKDAY_START);
                if (ready.isAfter(upstreamReady)) {
                    upstreamReady = ready;
                }
            }
        }
        return upstreamReady;
    }

    public int resolvePostProcessingMinutes(String productCode) {
        if (!ruleScope.isMasterPlanEnabled(BusinessRuleTypeIds.OPERATION_POST_PROCESSING)) {
            return 0;
        }
        List<ProductResourceEntity> routing = ProductResourceEntity.findByProductOrdered(productCode);
        String lastOp = routing.isEmpty()
                ? null
                : routing.stream()
                        .max(Comparator.comparingInt(r -> r.sequenceNo != null ? r.sequenceNo : 0))
                        .map(r -> r.operationName)
                        .orElse(null);
        if (lastOp != null && !lastOp.isBlank()) {
            OperationPostProcessingRuleEntity exact = OperationPostProcessingRuleEntity.findEntry(productCode, lastOp);
            if (exact != null) {
                return Math.max(0, exact.postProcessingMinutes);
            }
        }
        OperationPostProcessingRuleEntity wildcard = OperationPostProcessingRuleEntity.findEntry(productCode, "*");
        if (wildcard != null) {
            return Math.max(0, wildcard.postProcessingMinutes);
        }
        return 0;
    }

    private static WorkOrderTimingWindowDto buildWindow(
            int productionMinutes,
            int postProcessingMinutes,
            LocalDateTime latestDelivery,
            LocalDateTime earliestStart) {
        LocalDateTime latestEnd = latestDelivery.minusMinutes(postProcessingMinutes);
        LocalDateTime latestStart = latestEnd.minusMinutes(productionMinutes);
        LocalDateTime earliestEnd = earliestStart.plusMinutes(productionMinutes);
        LocalDateTime earliestDelivery = earliestEnd.plusMinutes(postProcessingMinutes);
        return new WorkOrderTimingWindowDto(
                latestStart,
                latestEnd,
                latestDelivery,
                earliestStart,
                earliestEnd,
                earliestDelivery,
                earliestStart,
                earliestEnd,
                earliestDelivery,
                productionMinutes,
                postProcessingMinutes);
    }
}
