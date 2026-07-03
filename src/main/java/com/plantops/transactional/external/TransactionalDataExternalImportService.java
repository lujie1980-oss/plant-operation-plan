package com.plantops.transactional.external;

import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineDeliveryRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderRow;
import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.InventoryRow;
import com.plantops.api.dto.integration.IntegrationDtos.PurchaseOrderRow;
import com.plantops.api.dto.integration.IntegrationDtos.TransactionalBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderOperationResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderOperationRow;
import com.plantops.api.dto.integration.IntegrationDtos.WorkOrderRow;
import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** §12 · TODO-14 T1：只写 external_* 交易 staging（AC-TX-01）。 */
@ApplicationScoped
public class TransactionalDataExternalImportService {

    @Transactional
    public ImportBatchResult importBundle(TransactionalBundleImport bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle 不能为空");
        }
        String sourceSystem = bundle.sourceSystem() != null && !bundle.sourceSystem().isBlank()
                ? bundle.sourceSystem()
                : "API";
        String batchId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int rows = 0;

        rows += persistCustomerOrders(batchId, sourceSystem, bundle.customerOrders());
        rows += persistCustomerOrderLines(batchId, sourceSystem, bundle.customerOrderLines());
        rows += persistCustomerOrderLineDeliveries(batchId, sourceSystem, bundle.customerOrderLineDeliveries());
        rows += persistWorkOrders(batchId, sourceSystem, bundle.workOrders());
        rows += persistWorkOrderOperations(batchId, sourceSystem, bundle.workOrderOperations());
        rows += persistWorkOrderOperationResources(batchId, sourceSystem, bundle.workOrderOperationResources());
        rows += persistInventories(batchId, sourceSystem, bundle.inventories());
        rows += persistPurchaseOrders(batchId, sourceSystem, bundle.purchaseOrders());

        return new ImportBatchResult(batchId, rows, sourceSystem);
    }

    private static int persistCustomerOrders(String batchId, String sourceSystem, List<CustomerOrderRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CustomerOrderRow row : rows) {
            ExternalCustomerOrderEntity entity = new ExternalCustomerOrderEntity();
            entity.customerOrderNo = row.customerOrderNo();
            entity.customerCode = row.customerCode();
            entity.orderDate = row.orderDate();
            entity.orderStatus = row.orderStatus();
            entity.priority = row.priority();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistCustomerOrderLines(String batchId, String sourceSystem, List<CustomerOrderLineRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CustomerOrderLineRow row : rows) {
            ExternalCustomerOrderLineEntity entity = new ExternalCustomerOrderLineEntity();
            entity.customerOrderNo = row.customerOrderNo();
            entity.lineNo = row.lineNo();
            entity.productCode = row.productCode();
            entity.orderQty = row.orderQty();
            entity.uomCode = row.uomCode();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistCustomerOrderLineDeliveries(
            String batchId, String sourceSystem, List<CustomerOrderLineDeliveryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CustomerOrderLineDeliveryRow row : rows) {
            ExternalCustomerOrderLineDeliveryEntity entity = new ExternalCustomerOrderLineDeliveryEntity();
            entity.customerOrderNo = row.customerOrderNo();
            entity.lineNo = row.lineNo();
            entity.deliverySeq = row.deliverySeq();
            entity.deliveryQty = row.deliveryQty();
            entity.requestedDate = row.requestedDate();
            entity.lineStatus = row.lineStatus();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistWorkOrders(String batchId, String sourceSystem, List<WorkOrderRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WorkOrderRow row : rows) {
            ExternalWorkOrderEntity entity = new ExternalWorkOrderEntity();
            entity.workOrderNo = row.workOrderNo();
            entity.productCode = row.productCode();
            entity.quantity = row.quantity();
            entity.needDate = row.needDate();
            entity.firmFlag = row.firmFlag();
            entity.sourceType = "EXTERNAL_SYNC";
            entity.dispatchStatus = row.dispatchStatus();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistWorkOrderOperations(
            String batchId, String sourceSystem, List<WorkOrderOperationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WorkOrderOperationRow row : rows) {
            ExternalWorkOrderOperationEntity entity = new ExternalWorkOrderOperationEntity();
            entity.workOrderNo = row.workOrderNo();
            entity.operationSeq = row.operationSeq();
            entity.operationCode = row.operationCode();
            entity.operationName = row.operationName();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistWorkOrderOperationResources(
            String batchId, String sourceSystem, List<WorkOrderOperationResourceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WorkOrderOperationResourceRow row : rows) {
            ExternalWorkOrderOperationResourceEntity entity = new ExternalWorkOrderOperationResourceEntity();
            entity.workOrderNo = row.workOrderNo();
            entity.operationSeq = row.operationSeq();
            entity.standardResourceCode = row.standardResourceCode();
            entity.resourcePriority = row.resourcePriority() > 0 ? row.resourcePriority() : 1;
            entity.setupTimeMinutes = row.setupTimeMinutes();
            entity.processTimeSeconds =
                    row.processTimeSeconds() != null ? row.processTimeSeconds() : BigDecimal.valueOf(60);
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistInventories(String batchId, String sourceSystem, List<InventoryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (InventoryRow row : rows) {
            ExternalInventoryEntity entity = new ExternalInventoryEntity();
            entity.productCode = row.productCode();
            entity.stockingPointCode = row.stockingPointCode();
            entity.onHandQty = row.onHandQty();
            entity.availableQty = row.availableQty();
            entity.asOfDate = row.asOfDate();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistPurchaseOrders(String batchId, String sourceSystem, List<PurchaseOrderRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PurchaseOrderRow row : rows) {
            ExternalPurchaseOrderEntity entity = new ExternalPurchaseOrderEntity();
            entity.purchaseOrderNo = row.purchaseOrderNo();
            entity.lineNo = row.lineNo();
            entity.productCode = row.productCode();
            entity.stockingPointCode = row.stockingPointCode();
            entity.orderQty = row.orderQty();
            entity.openQty = row.openQty();
            entity.promisedDate = row.promisedDate();
            entity.poStatus = row.poStatus();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }
}
