package com.plantops.scenario.planning;

import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从主计划版本加载工序级契约（资源 + 开始/结束日），供 S05 推演与软约束使用。
 */
@ApplicationScoped
public class MasterPlanContractLoader {

    private static final Pattern MP_OPERATION_ALLOCATION_PATTERN =
            Pattern.compile("@OP(?<seq>\\d+)(?:_(?<ord>\\d+))?#");

    public record OperationContract(String resourceId, LocalDate startDate, LocalDate endDate) {
    }

    public record ContractSnapshot(
            Map<String, LocalDate> workOrderEndByWorkOrder,
            Map<String, OperationContract> operationContracts) {
    }

    public ContractSnapshot load(String masterPlanVersionId) {
        Map<String, LocalDate> endByWorkOrder = new HashMap<>();
        Map<String, OperationContract> contracts = new HashMap<>();
        if (masterPlanVersionId == null || masterPlanVersionId.isBlank()) {
            return new ContractSnapshot(endByWorkOrder, contracts);
        }
        List<MasterPlanAllocationEntity> rows = MasterPlanAllocationEntity
                .find("planVersionId = ?1 order by workOrderNo, slotDate, slotIndex", masterPlanVersionId)
                .list();
        for (MasterPlanAllocationEntity row : rows) {
            if (row.workOrderNo == null || row.slotDate == null) {
                continue;
            }
            endByWorkOrder.put(row.workOrderNo, row.slotDate);
            ParsedOperationKey keyParts = parseOperationKey(row.allocationId);
            if (keyParts == null) {
                continue;
            }
            String key = contractKey(row.workOrderNo, keyParts.operationSeq(), keyParts.operationOrdinal());
            OperationContract current = contracts.get(key);
            if (current == null) {
                contracts.put(key, new OperationContract(row.resourceId, row.slotDate, row.slotDate));
                continue;
            }
            LocalDate start = row.slotDate.isBefore(current.startDate()) ? row.slotDate : current.startDate();
            LocalDate end = row.slotDate.isAfter(current.endDate()) ? row.slotDate : current.endDate();
            String resource = current.resourceId();
            if (resource == null || resource.isBlank()) {
                resource = row.resourceId;
            }
            contracts.put(key, new OperationContract(resource, start, end));
        }
        return new ContractSnapshot(endByWorkOrder, contracts);
    }

    public static String contractKey(String workOrderNo, int operationSeq, int operationOrdinal) {
        return workOrderNo + "#" + operationSeq + "#" + operationOrdinal;
    }

    public static OperationContract resolveForStep(
            Map<String, OperationContract> contracts,
            String workOrderNo,
            int operationSeq,
            int operationOrdinal) {
        OperationContract exact = contracts.get(contractKey(workOrderNo, operationSeq, operationOrdinal));
        if (exact != null) {
            return exact;
        }
        return contracts.get(contractKey(workOrderNo, operationSeq, 0));
    }

    public static LocalDate computeFallbackTargetEndDate(
            LocalDate workOrderMpEnd,
            int operationSeq,
            int maxSeq,
            LocalDate planningAnchor) {
        if (workOrderMpEnd == null) {
            return null;
        }
        int stepsAfterLast = Math.max(0, maxSeq - operationSeq);
        LocalDate target = workOrderMpEnd.minusDays(stepsAfterLast);
        return target.isBefore(planningAnchor) ? planningAnchor : target;
    }

    private static ParsedOperationKey parseOperationKey(String allocationId) {
        if (allocationId == null || allocationId.isBlank()) {
            return null;
        }
        Matcher matcher = MP_OPERATION_ALLOCATION_PATTERN.matcher(allocationId);
        if (!matcher.find()) {
            return null;
        }
        try {
            int seq = Integer.parseInt(matcher.group("seq"));
            String ordRaw = matcher.group("ord");
            int ord = ordRaw != null ? Integer.parseInt(ordRaw) : 0;
            return new ParsedOperationKey(seq, ord);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private record ParsedOperationKey(int operationSeq, int operationOrdinal) {
    }
}
