package com.plantops.scenario.planning;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.ProductionBatchEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.planning.MasterPlanContractLoader.OperationContract;
import com.plantops.solver.detailschedule.OperationAssignment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 从工单 + 工艺工序（按 sequenceNo 分组）构建 {@link OperationAssignment}。 */
final class DetailScheduleAssignmentBuilder {

    private DetailScheduleAssignmentBuilder() {
    }

    static List<OperationAssignment> buildForWorkOrder(
            WorkOrderEntity wo,
            List<ProductRoutingSteps.Operation> operations,
            boolean kittingOk,
            int kittingLockMinutes,
            boolean pinned,
            LocalDate dueDate,
            LocalDate woMpEnd,
            LocalDate planningAnchor,
            Map<String, OperationContract> operationContracts,
            int sequenceHintStart,
            BusinessRuleScopeService businessRuleScopeService) {
        int maxSeq = operations.stream()
                .mapToInt(ProductRoutingSteps.Operation::sequenceNo)
                .max()
                .orElse(operations.size());
        List<OperationAssignment> out = new ArrayList<>(operations.size());
        int seqHint = sequenceHintStart;
        for (int i = 0; i < operations.size(); i++) {
            ProductRoutingSteps.Operation operation = operations.get(i);
            OperationAssignment op = new OperationAssignment();
            op.setOperationId("OP-" + wo.workOrderNo + "-" + operation.sequenceNo() + "_0");
            op.setWorkOrderNo(wo.workOrderNo);
            op.setProductCode(wo.productCode);
            op.setOperationName(operation.operationName());
            op.setDueDate(dueDate);
            op.setOperationSeq(operation.sequenceNo());
            op.setLastOperationForDueDate(i == operations.size() - 1);

            OperationContract contract = MasterPlanContractLoader.resolveForStep(
                    operationContracts, wo.workOrderNo, operation.sequenceNo(), 0);
            String preferredResource = contract != null && contract.resourceId() != null && !contract.resourceId().isBlank()
                    ? contract.resourceId()
                    : operation.primaryResourceId();
            op.setResourceId(preferredResource);
            op.setAllowedResourceIds(operation.allowedResourceIds());
            op.setAllowedLineIds(DetailScheduleRoutingSupport.lineIdsForResources(operation.allowedResourceIds()));

            if (contract != null) {
                op.setMpContractResourceId(contract.resourceId());
                op.setMpContractStartDate(contract.startDate());
                op.setMpContractEndDate(contract.endDate());
                op.setMpTargetEndDate(contract.endDate());
            } else {
                op.setMpTargetEndDate(MasterPlanContractLoader.computeFallbackTargetEndDate(
                        woMpEnd, operation.sequenceNo(), maxSeq, planningAnchor));
            }

            if (contract != null && contract.resourceId() != null && !contract.resourceId().isBlank()) {
                op.setDurationMinutes(ProductRoutingSteps.durationMinutesForResource(
                        wo.productCode, contract.resourceId(), wo.quantity));
            } else {
                op.setDurationMinutes(ProductRoutingSteps.durationMinutesForOperation(operation, wo.quantity));
            }
            op.setKittingEligible(kittingOk);
            op.setEarliestStartMinute(kittingOk ? 0 : kittingLockMinutes);
            op.setPinned(pinned);
            op.setSequenceHint(seqHint++);
            out.add(op);
        }
        linkRoutingPredecessors(out);
        return out;
    }

    static List<OperationAssignment> buildForBatch(
            ProductionBatchEntity batch,
            WorkOrderEntity wo,
            List<ProductRoutingSteps.Operation> operations,
            boolean kittingOk,
            int kittingLockMinutes,
            boolean pinned,
            LocalDate dueDate,
            LocalDate woMpEnd,
            LocalDate planningAnchor,
            Map<String, OperationContract> operationContracts,
            int sequenceHintStart,
            BusinessRuleScopeService businessRuleScopeService) {
        BigDecimal runQuantity = batch.quantity != null ? batch.quantity : BigDecimal.ZERO;
        int maxSeq = operations.stream()
                .mapToInt(ProductRoutingSteps.Operation::sequenceNo)
                .max()
                .orElse(operations.size());
        List<OperationAssignment> out = new ArrayList<>(operations.size());
        int seqHint = sequenceHintStart;
        for (int i = 0; i < operations.size(); i++) {
            ProductRoutingSteps.Operation operation = operations.get(i);
            OperationAssignment op = new OperationAssignment();
            op.setOperationId("OP-" + batch.batchNo + "-" + operation.sequenceNo() + "_0");
            op.setWorkOrderNo(wo.workOrderNo);
            op.setBatchNo(batch.batchNo);
            op.setBatchQuantity(runQuantity);
            op.setProductCode(wo.productCode);
            op.setOperationName(operation.operationName());
            op.setDueDate(dueDate);
            op.setOperationSeq(operation.sequenceNo());
            op.setLastOperationForDueDate(i == operations.size() - 1);

            OperationContract contract = MasterPlanContractLoader.resolveForStep(
                    operationContracts, wo.workOrderNo, operation.sequenceNo(), 0);
            String preferredResource = contract != null && contract.resourceId() != null && !contract.resourceId().isBlank()
                    ? contract.resourceId()
                    : operation.primaryResourceId();
            op.setResourceId(preferredResource);
            op.setAllowedResourceIds(operation.allowedResourceIds());
            op.setAllowedLineIds(DetailScheduleRoutingSupport.lineIdsForResources(operation.allowedResourceIds()));

            if (contract != null) {
                op.setMpContractResourceId(contract.resourceId());
                op.setMpContractStartDate(contract.startDate());
                op.setMpContractEndDate(contract.endDate());
                op.setMpTargetEndDate(contract.endDate());
            } else {
                op.setMpTargetEndDate(MasterPlanContractLoader.computeFallbackTargetEndDate(
                        woMpEnd, operation.sequenceNo(), maxSeq, planningAnchor));
            }

            if (contract != null && contract.resourceId() != null && !contract.resourceId().isBlank()) {
                op.setDurationMinutes(ProductRoutingSteps.durationMinutesForResource(
                        wo.productCode, contract.resourceId(), runQuantity));
            } else {
                op.setDurationMinutes(ProductRoutingSteps.durationMinutesForOperation(operation, runQuantity));
            }
            op.setKittingEligible(kittingOk);
            op.setEarliestStartMinute(kittingOk ? 0 : kittingLockMinutes);
            op.setPinned(pinned);
            op.setSequenceHint(seqHint++);
            out.add(op);
        }
        linkRoutingPredecessors(out);
        return out;
    }

    /** 从 {@code OP-...-{seq}_0} 解析工艺序号（与前端甘特工艺链一致）。 */
    public static int parseOperationSeqFromOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return 0;
        }
        int underscore = operationId.lastIndexOf('_');
        if (underscore <= 0) {
            return 0;
        }
        int dash = operationId.lastIndexOf('-', underscore);
        if (dash < 0 || dash >= underscore - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(operationId.substring(dash + 1, underscore));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void linkRoutingPredecessors(List<OperationAssignment> operations) {
        if (operations == null || operations.size() < 2) {
            return;
        }
        List<OperationAssignment> sorted = new ArrayList<>(operations);
        sorted.sort(java.util.Comparator
                .comparingInt(OperationAssignment::getOperationSeq)
                .thenComparing(OperationAssignment::getOperationId, java.util.Comparator.nullsLast(String::compareTo)));
        for (int i = 1; i < sorted.size(); i++) {
            sorted.get(i).setRoutingPredecessor(sorted.get(i - 1));
        }
    }

    static boolean resolvePinned(WorkOrderEntity wo, BusinessRuleScopeService businessRuleScopeService) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
        return order != null
                && businessRuleScopeService.isDetailScheduleEnabled(BusinessRuleTypeIds.DEMAND_PRIORITY_RULES)
                && order.scheduleLockFlag;
    }

    static LocalDate resolveDueDate(WorkOrderEntity wo) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
        return order != null ? order.dueDate : LocalDate.now().plusDays(7);
    }
}
