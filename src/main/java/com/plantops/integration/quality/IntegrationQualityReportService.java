package com.plantops.integration.quality;

import com.plantops.api.dto.integration.IntegrationDtos.QualityIssueRowDto;
import com.plantops.api.dto.integration.IntegrationDtos.QualityReportDto;
import com.plantops.masterdata.quality.MasterDataQualityService;
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
import com.plantops.transactional.quality.TransactionalDataQualityService;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/** API-INT-08：质检报告摘要。 */
@ApplicationScoped
public class IntegrationQualityReportService {

    @Inject
    MasterDataQualityService masterDataQualityService;

    @Inject
    TransactionalDataQualityService transactionalQualityService;

    public QualityReportDto report(String importBatchId, String issueCode) {
        if (importBatchId == null || importBatchId.isBlank()) {
            return new QualityReportDto(null, 0, 0, 0, 0, List.of());
        }
        MasterDataQualityService.QualityReport md = masterDataQualityService.checkBatch(importBatchId);
        TransactionalDataQualityService.QualityReport tx = transactionalQualityService.checkBatch(importBatchId);
        int pending = md.pendingCount() + tx.pendingCount();
        int passed = md.passedCount() + tx.passedCount();
        int failed = md.failedCount() + tx.failedCount();
        int warning = md.warningCount() + tx.warningCount();
        List<QualityIssueRowDto> failedRows = collectFailedRows(importBatchId, issueCode);
        return new QualityReportDto(importBatchId, pending, passed, failed, warning, failedRows);
    }

    private static List<QualityIssueRowDto> collectFailedRows(String importBatchId, String issueCode) {
        List<QualityIssueRowDto> rows = new ArrayList<>();
        appendFailed(rows, "external_stocking_point", ExternalStockingPointEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_routing", ExternalRoutingEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_routing_step_on_standard_resource", ExternalRoutingStepOsrEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_product_in_stocking_point", ExternalProductInStockingPointEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_resource_group", ExternalResourceGroupEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_standard_resource", ExternalStandardResourceEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_physical_resource", ExternalPhysicalResourceEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_routing_step", ExternalRoutingStepEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_routing_step_input_material", ExternalRoutingStepImEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_routing_step_output_material", ExternalRoutingStepOmEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_customer_order", ExternalCustomerOrderEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_customer_order_line", ExternalCustomerOrderLineEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_customer_order_line_delivery", ExternalCustomerOrderLineDeliveryEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_work_order", ExternalWorkOrderEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_work_order_operation", ExternalWorkOrderOperationEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_work_order_operation_resource", ExternalWorkOrderOperationResourceEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_inventory", ExternalInventoryEntity.listForBatch(importBatchId), issueCode);
        appendFailed(rows, "external_purchase_order", ExternalPurchaseOrderEntity.listForBatch(importBatchId), issueCode);
        return rows;
    }

    private static void appendFailed(
            List<QualityIssueRowDto> out,
            String tableName,
            List<? extends ExternalStagingEntity> entities,
            String issueCode) {
        for (ExternalStagingEntity entity : entities) {
            if (!"FAILED".equals(entity.qualityStatus)) {
                continue;
            }
            if (issueCode != null
                    && !issueCode.isBlank()
                    && (entity.qualityIssueCodes == null || !entity.qualityIssueCodes.contains(issueCode))) {
                continue;
            }
            Long id = entity instanceof PanacheEntity panache ? panache.id : null;
            out.add(new QualityIssueRowDto(
                    tableName, id, entity.qualityIssueCodes, entity.qualityIssueDetail));
        }
    }
}
