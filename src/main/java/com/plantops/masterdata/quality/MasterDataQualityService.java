package com.plantops.masterdata.quality;

import com.plantops.persistence.entity.ExternalPhysicalResourceEntity;
import com.plantops.persistence.entity.ExternalProductInStockingPointEntity;
import com.plantops.persistence.entity.ExternalResourceGroupEntity;
import com.plantops.persistence.entity.ExternalRoutingEntity;
import com.plantops.persistence.entity.ExternalRoutingStepEntity;
import com.plantops.persistence.entity.ExternalRoutingStepImEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOsrEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOmEntity;
import com.plantops.persistence.entity.ExternalStagingEntity;
import com.plantops.persistence.entity.ExternalStandardResourceEntity;
import com.plantops.persistence.entity.ExternalStockingPointEntity;
import com.plantops.persistence.entity.MdResourceGroupEntity;
import com.plantops.persistence.entity.MdStandardResourceEntity;
import com.plantops.persistence.entity.MdStockingPointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** §11.4 · TODO-13 M2：external_* 批次质检（AC-MD-02）。 */
@ApplicationScoped
public class MasterDataQualityService {

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

        List<ExternalStockingPointEntity> sps = ExternalStockingPointEntity.listForBatch(importBatchId);
        List<ExternalProductInStockingPointEntity> pisps =
                ExternalProductInStockingPointEntity.listForBatch(importBatchId);
        List<ExternalRoutingEntity> routings = ExternalRoutingEntity.listForBatch(importBatchId);
        List<ExternalRoutingStepEntity> steps = ExternalRoutingStepEntity.listForBatch(importBatchId);
        List<ExternalRoutingStepOsrEntity> osrs = ExternalRoutingStepOsrEntity.listForBatch(importBatchId);
        List<ExternalRoutingStepImEntity> ims = ExternalRoutingStepImEntity.listForBatch(importBatchId);
        List<ExternalRoutingStepOmEntity> oms = ExternalRoutingStepOmEntity.listForBatch(importBatchId);
        List<ExternalResourceGroupEntity> groups = ExternalResourceGroupEntity.listForBatch(importBatchId);
        List<ExternalStandardResourceEntity> srs = ExternalStandardResourceEntity.listForBatch(importBatchId);
        List<ExternalPhysicalResourceEntity> prs = ExternalPhysicalResourceEntity.listForBatch(importBatchId);

        Set<String> spCodes = codes(sps, e -> e.stockingPointCode);
        spCodes.addAll(MdStockingPointEntity.listInWorkspace().stream().map(e -> e.code).collect(Collectors.toSet()));

        Set<String> srCodes = codes(srs, e -> e.standardResourceCode);
        srCodes.addAll(MdStandardResourceEntity.listInWorkspace().stream().map(e -> e.code).collect(Collectors.toSet()));

        Set<String> routingCodes = codes(routings, e -> e.routingCode);

        for (ExternalStockingPointEntity row : sps) {
            apply(row, List.of());
        }

        for (ExternalProductInStockingPointEntity row : pisps) {
            List<String> issues = new ArrayList<>();
            if (row.stockingPointCode == null || !spCodes.contains(row.stockingPointCode)) {
                issues.add(MasterDataQualityIssueCodes.SP_01);
            }
            apply(row, issues);
        }

        Set<String> pathKeys = new HashSet<>();
        for (ExternalRoutingEntity row : routings) {
            List<String> issues = new ArrayList<>();
            if (row.stockingPointCode == null || !spCodes.contains(row.stockingPointCode)) {
                issues.add(MasterDataQualityIssueCodes.RT_02);
            }
            String pathKey = row.productCode + "|" + row.stockingPointCode + "|" + row.pathPriority;
            if (!pathKeys.add(pathKey)) {
                issues.add(MasterDataQualityIssueCodes.RT_01);
            }
            apply(row, issues);
        }

        Map<String, Set<Integer>> seqByRouting = new HashMap<>();
        for (ExternalRoutingStepEntity row : steps) {
            List<String> issues = new ArrayList<>();
            if (row.routingCode == null || !routingCodes.contains(row.routingCode)) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            if (row.sequenceNo <= 0) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            Set<Integer> seqs = seqByRouting.computeIfAbsent(row.routingCode, ignored -> new HashSet<>());
            if (!seqs.add(row.sequenceNo)) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            apply(row, issues);
        }

        for (ExternalRoutingStepOsrEntity row : osrs) {
            List<String> issues = new ArrayList<>();
            if (row.routingCode == null || !routingCodes.contains(row.routingCode)) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            if (row.standardResourceCode == null || !srCodes.contains(row.standardResourceCode)) {
                issues.add(MasterDataQualityIssueCodes.FK_02);
            }
            apply(row, issues);
        }

        for (ExternalResourceGroupEntity row : groups) {
            apply(row, List.of());
        }
        for (ExternalStandardResourceEntity row : srs) {
            List<String> issues = new ArrayList<>();
            if (row.resourceGroupCode != null
                    && groups.stream().noneMatch(g -> row.resourceGroupCode.equals(g.resourceGroupCode))
                    && MdResourceGroupEntity.listInWorkspace().stream()
                            .noneMatch(rg -> row.resourceGroupCode.equals(rg.code))) {
                issues.add(MasterDataQualityIssueCodes.RG_01);
            }
            apply(row, issues);
        }
        for (ExternalPhysicalResourceEntity row : prs) {
            List<String> issues = new ArrayList<>();
            if (row.standardResourceCode == null || !srCodes.contains(row.standardResourceCode)) {
                issues.add(MasterDataQualityIssueCodes.SR_01);
            }
            apply(row, issues);
        }
        for (ExternalRoutingStepImEntity row : ims) {
            List<String> issues = new ArrayList<>();
            if (row.routingCode == null || !routingCodes.contains(row.routingCode)) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            apply(row, issues);
        }
        for (ExternalRoutingStepOmEntity row : oms) {
            List<String> issues = new ArrayList<>();
            if (row.routingCode == null || !routingCodes.contains(row.routingCode)) {
                issues.add(MasterDataQualityIssueCodes.RS_01);
            }
            apply(row, issues);
        }

        // batch 闭包：planning_relevant PISP 须至少一条 Routing
        Set<String> productsWithRouting =
                routings.stream().map(r -> r.productCode).collect(Collectors.toSet());
        for (ExternalProductInStockingPointEntity row : pisps) {
            if (row.planningRelevant && !productsWithRouting.contains(row.productCode)) {
                List<String> issues = new ArrayList<>(List.of(MasterDataQualityIssueCodes.PISP_01));
                if ("PASSED".equals(row.qualityStatus)) {
                    apply(row, issues);
                }
            }
        }

        // Routing 须至少一条 step
        Map<String, Long> stepCountByRouting =
                steps.stream().collect(Collectors.groupingBy(s -> s.routingCode, Collectors.counting()));
        for (ExternalRoutingEntity row : routings) {
            if (stepCountByRouting.getOrDefault(row.routingCode, 0L) == 0) {
                apply(row, List.of(MasterDataQualityIssueCodes.RT_03));
            }
        }

        // RS 须至少一条 OSR
        Map<String, Long> osrCountByStep = osrs.stream()
                .collect(Collectors.groupingBy(o -> o.routingCode + "|" + o.sequenceNo, Collectors.counting()));
        for (ExternalRoutingStepEntity row : steps) {
            String key = row.routingCode + "|" + row.sequenceNo;
            if (osrCountByStep.getOrDefault(key, 0L) == 0) {
                apply(row, List.of(MasterDataQualityIssueCodes.RS_03));
            }
        }

        // RS 须至少一条 PASSED OSR（batch 闭包）
        Set<String> passedOsrStepKeys = osrs.stream()
                .filter(o -> "PASSED".equals(o.qualityStatus))
                .map(o -> o.routingCode + "|" + o.sequenceNo)
                .collect(Collectors.toSet());
        for (ExternalRoutingStepEntity row : steps) {
            String key = row.routingCode + "|" + row.sequenceNo;
            if (!passedOsrStepKeys.contains(key) && !"FAILED".equals(row.qualityStatus)) {
                apply(row, List.of(MasterDataQualityIssueCodes.RS_03));
            }
        }

        // Routing 须至少一条 PASSED step
        Map<String, Long> passedStepCountByRouting = steps.stream()
                .filter(s -> "PASSED".equals(s.qualityStatus))
                .collect(Collectors.groupingBy(s -> s.routingCode, Collectors.counting()));
        for (ExternalRoutingEntity row : routings) {
            if (passedStepCountByRouting.getOrDefault(row.routingCode, 0L) == 0
                    && !"FAILED".equals(row.qualityStatus)) {
                apply(row, List.of(MasterDataQualityIssueCodes.RT_03));
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
        all.addAll(ExternalStockingPointEntity.listForBatch(batchId));
        all.addAll(ExternalProductInStockingPointEntity.listForBatch(batchId));
        all.addAll(ExternalRoutingEntity.listForBatch(batchId));
        all.addAll(ExternalRoutingStepEntity.listForBatch(batchId));
        all.addAll(ExternalRoutingStepOsrEntity.listForBatch(batchId));
        all.addAll(ExternalRoutingStepImEntity.listForBatch(batchId));
        all.addAll(ExternalRoutingStepOmEntity.listForBatch(batchId));
        all.addAll(ExternalResourceGroupEntity.listForBatch(batchId));
        all.addAll(ExternalStandardResourceEntity.listForBatch(batchId));
        all.addAll(ExternalPhysicalResourceEntity.listForBatch(batchId));
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
