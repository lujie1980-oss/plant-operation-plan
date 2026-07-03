package com.plantops.api.dto.integration;

import java.math.BigDecimal;
import java.util.List;

/** §19 · MOD-DI · API-INT-* DTOs（TODO-13）。 */
public final class IntegrationDtos {

    private IntegrationDtos() {}

    public record ExternalTableInfoDto(String tableName, String label, Long rowCount) {}

    public record ImportBatchRequest(String sourceSystem) {}

    public record ImportBatchResult(String importBatchId, int rowCount, String sourceSystem) {}

    public record QualityCheckRequest(String importBatchId) {}

    public record QualityCheckResult(
            String importBatchId,
            int pendingCount,
            int passedCount,
            int failedCount,
            int warningCount) {}

    public record SyncRequest(String importBatchId) {}

    public record SyncResult(String importBatchId, int syncedRows, int skippedRows) {}

    public record RoutingBundleImport(
            String sourceSystem,
            List<StockingPointRow> stockingPoints,
            List<ResourceGroupRow> resourceGroups,
            List<StandardResourceRow> standardResources,
            List<PhysicalResourceRow> physicalResources,
            List<PispRow> productInStockingPoints,
            List<RoutingRow> routings,
            List<RoutingStepRow> routingSteps,
            List<RoutingStepOsrRow> routingStepOsrs,
            List<RoutingStepImRow> routingStepInputMaterials) {}

    public record StockingPointRow(String code, String name, String siteCode) {}

    public record ResourceGroupRow(String code, String name, String calendarCode, BigDecimal resourceEfficiency) {}

    public record StandardResourceRow(
            String code,
            String name,
            String resourceGroupCode,
            String capacityUom,
            boolean bottleneck,
            BigDecimal resourceEfficiency) {}

    public record PhysicalResourceRow(
            String code, String name, String standardResourceCode, String productionLineCode, String status) {}

    public record PispRow(
            String productCode,
            String stockingPointCode,
            boolean planningRelevant,
            BigDecimal ppq,
            BigDecimal lotSize) {}

    public record RoutingRow(
            String routingCode,
            String productCode,
            String stockingPointCode,
            int pathPriority,
            String routingName) {}

    public record RoutingStepRow(
            String routingCode, int sequenceNo, String operationCode, String operationName) {}

    public record RoutingStepOsrRow(
            String routingCode,
            int sequenceNo,
            String standardResourceCode,
            int resourcePriority,
            int setupTimeMinutes,
            BigDecimal processTimeSeconds) {}

    public record RoutingStepImRow(
            String routingCode,
            int sequenceNo,
            String componentProductCode,
            BigDecimal componentQty,
            String issueStockingPointCode) {}
}
