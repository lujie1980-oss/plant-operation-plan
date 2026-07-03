package com.plantops.integration.batch;

import com.plantops.api.dto.integration.IntegrationDtos.IntegrationBatchDto;
import com.plantops.persistence.entity.ExternalStagingEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPhysicalResourceEntity;
import com.plantops.persistence.entity.ExternalProductInStockingPointEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalResourceGroupEntity;
import com.plantops.persistence.entity.ExternalRoutingEntity;
import com.plantops.persistence.entity.ExternalRoutingStepEntity;
import com.plantops.persistence.entity.ExternalRoutingStepImEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOmEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOsrEntity;
import com.plantops.persistence.entity.ExternalStandardResourceEntity;
import com.plantops.persistence.entity.ExternalStockingPointEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-INT-01：从 external_* 聚合导入批次。 */
@ApplicationScoped
public class IntegrationBatchService {

    public List<IntegrationBatchDto> listBatches(int limit) {
        Map<String, BatchAgg> batches = new LinkedHashMap<>();
        collect(batches, ExternalStockingPointEntity.listInWorkspace());
        collect(batches, ExternalProductInStockingPointEntity.listInWorkspace());
        collect(batches, ExternalRoutingEntity.listInWorkspace());
        collect(batches, ExternalRoutingStepEntity.listInWorkspace());
        collect(batches, ExternalRoutingStepOsrEntity.listInWorkspace());
        collect(batches, ExternalRoutingStepImEntity.listInWorkspace());
        collect(batches, ExternalRoutingStepOmEntity.listInWorkspace());
        collect(batches, ExternalResourceGroupEntity.listInWorkspace());
        collect(batches, ExternalStandardResourceEntity.listInWorkspace());
        collect(batches, ExternalPhysicalResourceEntity.listInWorkspace());
        collect(batches, ExternalCustomerOrderEntity.listInWorkspace());
        collect(batches, ExternalCustomerOrderLineEntity.listInWorkspace());
        collect(batches, ExternalCustomerOrderLineDeliveryEntity.listInWorkspace());
        collect(batches, ExternalWorkOrderEntity.listInWorkspace());
        collect(batches, ExternalWorkOrderOperationEntity.listInWorkspace());
        collect(batches, ExternalWorkOrderOperationResourceEntity.listInWorkspace());
        collect(batches, ExternalInventoryEntity.listInWorkspace());
        collect(batches, ExternalPurchaseOrderEntity.listInWorkspace());

        return batches.values().stream()
                .sorted(Comparator.comparing((BatchAgg b) -> b.createdAt).reversed())
                .limit(Math.max(1, limit))
                .map(BatchAgg::toDto)
                .toList();
    }

    private static void collect(Map<String, BatchAgg> batches, List<? extends ExternalStagingEntity> rows) {
        for (ExternalStagingEntity row : rows) {
            if (row.importBatchId == null || row.importBatchId.isBlank()) {
                continue;
            }
            BatchAgg agg = batches.computeIfAbsent(row.importBatchId, BatchAgg::new);
            agg.sourceSystem = row.sourceSystem != null ? row.sourceSystem : agg.sourceSystem;
            if (row.importedAt != null
                    && (agg.createdAt == null || row.importedAt.isBefore(agg.createdAt) == false)) {
                agg.createdAt = row.importedAt;
            }
            agg.rowCount++;
            switch (row.qualityStatus) {
                case "PENDING" -> agg.pendingCount++;
                case "FAILED" -> agg.errorCount++;
                default -> {}
            }
            agg.qualityStatus = worstStatus(agg.qualityStatus, row.qualityStatus);
        }
    }

    private static String worstStatus(String current, String next) {
        if ("FAILED".equals(next) || "FAILED".equals(current)) {
            return "FAILED";
        }
        if ("PENDING".equals(next) || "PENDING".equals(current)) {
            return "PENDING";
        }
        if ("WARNING".equals(next) || "WARNING".equals(current)) {
            return "WARNING";
        }
        return next != null ? next : current;
    }

    private static final class BatchAgg {
        final String importBatchId;
        String sourceSystem = "API";
        LocalDateTime createdAt = LocalDateTime.now();
        int rowCount;
        int pendingCount;
        int errorCount;
        String qualityStatus = "PASSED";

        BatchAgg(String importBatchId) {
            this.importBatchId = importBatchId;
        }

        IntegrationBatchDto toDto() {
            return new IntegrationBatchDto(
                    importBatchId,
                    adapterIdForSource(sourceSystem),
                    sourceSystem,
                    rowCount,
                    pendingCount,
                    errorCount,
                    qualityStatus,
                    createdAt);
        }
    }

    static String adapterIdForSource(String sourceSystem) {
        if (sourceSystem == null) {
            return "ADP-EXCEL";
        }
        return switch (sourceSystem) {
            case "ERP_SAP" -> "ADP-ERP-SAP";
            case "MES_DEFAULT" -> "ADP-MES";
            case "EXCEL_IMPORT" -> "ADP-EXCEL";
            default -> sourceSystem.startsWith("EXCEL") ? "ADP-EXCEL" : "ADP-EXCEL";
        };
    }
}
