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

    public record TransactionalBundleImport(
            String sourceSystem,
            List<CustomerOrderRow> customerOrders,
            List<CustomerOrderLineRow> customerOrderLines,
            List<CustomerOrderLineDeliveryRow> customerOrderLineDeliveries,
            List<WorkOrderRow> workOrders,
            List<WorkOrderOperationRow> workOrderOperations,
            List<WorkOrderOperationResourceRow> workOrderOperationResources,
            List<InventoryRow> inventories,
            List<PurchaseOrderRow> purchaseOrders) {}

    public record CustomerOrderRow(
            String customerOrderNo,
            String customerCode,
            java.time.LocalDate orderDate,
            String orderStatus,
            Integer priority) {}

    public record CustomerOrderLineRow(
            String customerOrderNo, int lineNo, String productCode, BigDecimal orderQty, String uomCode) {}

    public record CustomerOrderLineDeliveryRow(
            String customerOrderNo,
            int lineNo,
            int deliverySeq,
            BigDecimal deliveryQty,
            java.time.LocalDate requestedDate,
            String lineStatus) {}

    public record WorkOrderRow(
            String workOrderNo,
            String productCode,
            BigDecimal quantity,
            java.time.LocalDate needDate,
            boolean firmFlag,
            String dispatchStatus) {}

    public record WorkOrderOperationRow(
            String workOrderNo, int operationSeq, String operationCode, String operationName) {}

    public record WorkOrderOperationResourceRow(
            String workOrderNo,
            int operationSeq,
            String standardResourceCode,
            int resourcePriority,
            int setupTimeMinutes,
            BigDecimal processTimeSeconds) {}

    public record InventoryRow(
            String productCode,
            String stockingPointCode,
            BigDecimal onHandQty,
            BigDecimal availableQty,
            java.time.LocalDate asOfDate) {}

    public record PurchaseOrderRow(
            String purchaseOrderNo,
            int lineNo,
            String productCode,
            String stockingPointCode,
            BigDecimal orderQty,
            BigDecimal openQty,
            java.time.LocalDate promisedDate,
            String poStatus) {}

    public record IntegrationBatchDto(
            String importBatchId,
            String adapterId,
            String sourceSystem,
            int rowCount,
            int pendingCount,
            int errorCount,
            String qualityStatus,
            java.time.LocalDateTime createdAt) {}

    public record IntegrationAdapterStatusDto(
            String adapterId,
            String name,
            boolean enabled,
            boolean configured,
            java.time.LocalDateTime lastRunAt,
            String lastStatus,
            String lastMessage) {}

    public record IntegrationAdapterConfigDto(java.util.Map<String, String> config) {}

    public record IntegrationAdapterRunResultDto(String importBatchId, String status, String message) {}

    public record ExternalRowPageDto(
            String tableName,
            int page,
            int size,
            long totalElements,
            List<java.util.Map<String, Object>> rows) {}

    public record QualityReportDto(
            String importBatchId,
            int pendingCount,
            int passedCount,
            int failedCount,
            int warningCount,
            List<QualityIssueRowDto> failedRows) {}

    public record QualityIssueRowDto(
            String tableName, Long rowId, String qualityIssueCodes, String qualityIssueDetail) {}

    public record IntegrationExcelUploadResultDto(
            String importBatchId, int rowCount, String status, String message) {}
}
