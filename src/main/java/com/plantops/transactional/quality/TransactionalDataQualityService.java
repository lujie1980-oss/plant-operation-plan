package com.plantops.transactional.quality;

import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalStagingEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;
import com.plantops.persistence.entity.MdPispEntity;
import com.plantops.persistence.entity.MdStandardResourceEntity;
import com.plantops.persistence.entity.MdStockingPointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** §12.5 · TODO-14 T2：external_* 交易批次质检（AC-TX-02）。 */
@ApplicationScoped
public class TransactionalDataQualityService {

    public record QualityReport(
            String importBatchId,
            int pendingCount,
            int passedCount,
            int failedCount,
            int warningCount) {}

    @Transactional
    public QualityReport checkBatch(String importBatchId) {
        if (importBatchId == null || importBatchId.isBlank()) {
            throw new IllegalArgumentException("importBatchId 不能为空");
        }

        List<ExternalCustomerOrderEntity> cos = ExternalCustomerOrderEntity.listForBatch(importBatchId);
        List<ExternalCustomerOrderLineEntity> cols = ExternalCustomerOrderLineEntity.listForBatch(importBatchId);
        List<ExternalCustomerOrderLineDeliveryEntity> colds =
                ExternalCustomerOrderLineDeliveryEntity.listForBatch(importBatchId);
        List<ExternalWorkOrderEntity> wos = ExternalWorkOrderEntity.listForBatch(importBatchId);
        List<ExternalWorkOrderOperationEntity> woos = ExternalWorkOrderOperationEntity.listForBatch(importBatchId);
        List<ExternalWorkOrderOperationResourceEntity> woors =
                ExternalWorkOrderOperationResourceEntity.listForBatch(importBatchId);
        List<ExternalInventoryEntity> invs = ExternalInventoryEntity.listForBatch(importBatchId);
        List<ExternalPurchaseOrderEntity> pos = ExternalPurchaseOrderEntity.listForBatch(importBatchId);

        Set<String> coNos = codes(cos, e -> e.customerOrderNo);
        Set<String> woNos = codes(wos, e -> e.workOrderNo);
        Set<String> pispProducts = MdPispEntity.listInWorkspace().stream()
                .map(e -> e.productCode)
                .collect(Collectors.toSet());
        Set<String> spCodes = MdStockingPointEntity.listInWorkspace().stream()
                .map(e -> e.code)
                .collect(Collectors.toSet());
        Set<String> srCodes = MdStandardResourceEntity.listInWorkspace().stream()
                .map(e -> e.code)
                .collect(Collectors.toSet());

        for (ExternalCustomerOrderEntity row : cos) {
            apply(row, List.of());
        }

        Set<String> colKeys = new HashSet<>();
        for (ExternalCustomerOrderLineEntity row : cols) {
            List<String> issues = new ArrayList<>();
            if (row.customerOrderNo == null || !coNos.contains(row.customerOrderNo)) {
                issues.add(TransactionalDataQualityIssueCodes.FK_01);
            }
            if (row.productCode == null || !pispProducts.contains(row.productCode)) {
                issues.add(TransactionalDataQualityIssueCodes.CO_01);
            }
            String key = row.customerOrderNo + "|" + row.lineNo;
            if (!colKeys.add(key)) {
                issues.add(TransactionalDataQualityIssueCodes.DUP_01);
            }
            apply(row, issues);
        }

        for (ExternalCustomerOrderLineDeliveryEntity row : colds) {
            List<String> issues = new ArrayList<>();
            if (row.customerOrderNo == null || !coNos.contains(row.customerOrderNo)) {
                issues.add(TransactionalDataQualityIssueCodes.FK_01);
            }
            if (!colKeys.contains(row.customerOrderNo + "|" + row.lineNo)) {
                issues.add(TransactionalDataQualityIssueCodes.FK_01);
            }
            apply(row, issues);
        }

        for (ExternalWorkOrderEntity row : wos) {
            List<String> issues = new ArrayList<>();
            if (!row.firmFlag) {
                issues.add(TransactionalDataQualityIssueCodes.WO_01);
            }
            if (row.productCode == null || !pispProducts.contains(row.productCode)) {
                issues.add(TransactionalDataQualityIssueCodes.WO_02);
            }
            apply(row, issues);
        }

        Set<String> opSeqKeys = new HashSet<>();
        for (ExternalWorkOrderOperationEntity row : woos) {
            List<String> issues = new ArrayList<>();
            if (row.workOrderNo == null || !woNos.contains(row.workOrderNo)) {
                issues.add(TransactionalDataQualityIssueCodes.WOO_01);
            }
            String key = row.workOrderNo + "|" + row.operationSeq;
            if (!opSeqKeys.add(key)) {
                issues.add(TransactionalDataQualityIssueCodes.WOO_02);
            }
            apply(row, issues);
        }

        for (ExternalWorkOrderOperationResourceEntity row : woors) {
            List<String> issues = new ArrayList<>();
            if (row.workOrderNo == null || !woNos.contains(row.workOrderNo)) {
                issues.add(TransactionalDataQualityIssueCodes.FK_01);
            }
            if (row.standardResourceCode == null || !srCodes.contains(row.standardResourceCode)) {
                issues.add(TransactionalDataQualityIssueCodes.WOOR_01);
            }
            apply(row, issues);
        }

        for (ExternalInventoryEntity row : invs) {
            List<String> issues = new ArrayList<>();
            if (row.stockingPointCode == null || !spCodes.contains(row.stockingPointCode)) {
                issues.add(TransactionalDataQualityIssueCodes.INV_01);
            }
            apply(row, issues);
        }

        for (ExternalPurchaseOrderEntity row : pos) {
            List<String> issues = new ArrayList<>();
            if (row.openQty != null && row.openQty.signum() < 0) {
                issues.add(TransactionalDataQualityIssueCodes.PO_01);
            }
            if (row.productCode == null || !pispProducts.contains(row.productCode)) {
                issues.add(TransactionalDataQualityIssueCodes.PO_01);
            }
            apply(row, issues);
        }

        // batch 闭包：每条 Operation 至少 1 条 WOOR
        Map<String, Long> woorByOp = woors.stream()
                .collect(Collectors.groupingBy(o -> o.workOrderNo + "|" + o.operationSeq, Collectors.counting()));
        for (ExternalWorkOrderOperationEntity row : woos) {
            String key = row.workOrderNo + "|" + row.operationSeq;
            if (woorByOp.getOrDefault(key, 0L) == 0) {
                apply(row, List.of(TransactionalDataQualityIssueCodes.WOOR_02));
            }
        }

        Set<String> passedOpKeys = woos.stream()
                .filter(o -> "PASSED".equals(o.qualityStatus))
                .map(o -> o.workOrderNo)
                .collect(Collectors.toSet());
        for (ExternalWorkOrderEntity row : wos) {
            if (!passedOpKeys.contains(row.workOrderNo) && !"FAILED".equals(row.qualityStatus)) {
                boolean hasPassedOp = woos.stream()
                        .anyMatch(o -> row.workOrderNo.equals(o.workOrderNo) && "PASSED".equals(o.qualityStatus));
                if (!hasPassedOp) {
                    apply(row, List.of(TransactionalDataQualityIssueCodes.WOO_01));
                }
            }
        }

        return summarize(importBatchId);
    }

    private QualityReport summarize(String importBatchId) {
        int pending = 0;
        int passed = 0;
        int failed = 0;
        int warning = 0;
        for (ExternalStagingEntity row : allRowsForBatch(importBatchId)) {
            switch (row.qualityStatus) {
                case "PASSED" -> passed++;
                case "FAILED" -> failed++;
                case "WARNING" -> warning++;
                default -> pending++;
            }
        }
        return new QualityReport(importBatchId, pending, passed, failed, warning);
    }

    private static List<ExternalStagingEntity> allRowsForBatch(String batchId) {
        List<ExternalStagingEntity> all = new ArrayList<>();
        all.addAll(ExternalCustomerOrderEntity.listForBatch(batchId));
        all.addAll(ExternalCustomerOrderLineEntity.listForBatch(batchId));
        all.addAll(ExternalCustomerOrderLineDeliveryEntity.listForBatch(batchId));
        all.addAll(ExternalWorkOrderEntity.listForBatch(batchId));
        all.addAll(ExternalWorkOrderOperationEntity.listForBatch(batchId));
        all.addAll(ExternalWorkOrderOperationResourceEntity.listForBatch(batchId));
        all.addAll(ExternalInventoryEntity.listForBatch(batchId));
        all.addAll(ExternalPurchaseOrderEntity.listForBatch(batchId));
        return all;
    }

    private static <T extends ExternalStagingEntity> Set<String> codes(
            List<T> rows, java.util.function.Function<T, String> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static void apply(ExternalStagingEntity row, List<String> issueCodes) {
        row.qualityCheckedAt = LocalDateTime.now();
        if (issueCodes == null || issueCodes.isEmpty()) {
            row.qualityStatus = "PASSED";
            row.blocked = false;
            row.qualityIssueCodes = null;
            row.qualityIssueDetail = null;
            return;
        }
        row.qualityStatus = "FAILED";
        row.blocked = true;
        row.qualityIssueCodes = String.join(",", issueCodes);
        row.qualityIssueDetail = issueCodes.stream().distinct().collect(Collectors.joining("; "));
    }
}
