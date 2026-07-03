package com.plantops.transactional.sync;

import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalStagingEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;
import com.plantops.persistence.entity.TxnCustomerOrderEntity;
import com.plantops.persistence.entity.TxnCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.TxnCustomerOrderLineEntity;
import com.plantops.persistence.entity.TxnDemandEntity;
import com.plantops.persistence.entity.TxnInventoryBalanceEntity;
import com.plantops.persistence.entity.TxnOperationEntity;
import com.plantops.persistence.entity.TxnOperationOsrEntity;
import com.plantops.persistence.entity.TxnPlanUnitEntity;
import com.plantops.persistence.entity.TxnPurchaseOrderEntity;
import com.plantops.persistence.entity.TxnSupplyOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** §12.4 · TODO-14 T3：PASSED external_* → txn_*（AC-TX-03）。 */
@ApplicationScoped
public class TransactionalDataSyncService {

    public record SyncReport(String importBatchId, int syncedRows, int skippedRows) {}

    @Transactional
    public SyncReport syncPassedBatch(String importBatchId) {
        if (importBatchId == null || importBatchId.isBlank()) {
            throw new IllegalArgumentException("importBatchId 不能为空");
        }
        int synced = 0;
        int skipped = 0;

        for (ExternalCustomerOrderEntity row : ExternalCustomerOrderEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertCustomerOrder(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalCustomerOrderLineEntity row : ExternalCustomerOrderLineEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertCustomerOrderLine(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalCustomerOrderLineDeliveryEntity row :
                ExternalCustomerOrderLineDeliveryEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertColdAndDemand(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalInventoryEntity row : ExternalInventoryEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertInventory(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalPurchaseOrderEntity row : ExternalPurchaseOrderEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertPurchaseOrder(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalWorkOrderEntity row : ExternalWorkOrderEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertSupplyOrderAndPlanUnit(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalWorkOrderOperationEntity row : ExternalWorkOrderOperationEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertOperation(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalWorkOrderOperationResourceEntity row :
                ExternalWorkOrderOperationResourceEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertOperationOsr(row);
                synced++;
            } else {
                skipped++;
            }
        }

        return new SyncReport(importBatchId, synced, skipped);
    }

    private static boolean syncRow(ExternalStagingEntity row) {
        if (!row.active || row.blocked) {
            return false;
        }
        if (!"PASSED".equals(row.qualityStatus) && !"WARNING".equals(row.qualityStatus)) {
            return false;
        }
        row.syncStatus = "SYNCED";
        row.syncedAt = LocalDateTime.now();
        return true;
    }

    private static void upsertCustomerOrder(ExternalCustomerOrderEntity row) {
        TxnCustomerOrderEntity md = findCustomerOrder(row.customerOrderNo);
        if (md == null) {
            md = new TxnCustomerOrderEntity();
            md.customerOrderNo = row.customerOrderNo;
            md.ensureWorkspace();
        }
        md.customerCode = row.customerCode;
        md.orderDate = row.orderDate;
        md.sourceStatus = row.orderStatus;
        md.customerGrade = row.customerGrade;
        md.priority = row.priority;
        md.kittingEnabled = row.kittingEnabled;
        md.kittingGranularity = row.kittingGranularity;
        md.persist();
        row.internalKey = md.customerOrderNo;
    }

    private static void upsertCustomerOrderLine(ExternalCustomerOrderLineEntity row) {
        TxnCustomerOrderLineEntity md = findCustomerOrderLine(row.customerOrderNo, row.lineNo);
        if (md == null) {
            md = new TxnCustomerOrderLineEntity();
            md.customerOrderNo = row.customerOrderNo;
            md.lineNo = row.lineNo;
            md.ensureWorkspace();
        }
        md.productCode = row.productCode;
        md.orderQty = row.orderQty;
        md.uomCode = row.uomCode;
        md.persist();
        row.internalKey = md.customerOrderNo + "|" + md.lineNo;
    }

    private static void upsertColdAndDemand(ExternalCustomerOrderLineDeliveryEntity row) {
        TxnCustomerOrderLineDeliveryEntity md = findCold(row.customerOrderNo, row.lineNo, row.deliverySeq);
        if (md == null) {
            md = new TxnCustomerOrderLineDeliveryEntity();
            md.customerOrderNo = row.customerOrderNo;
            md.lineNo = row.lineNo;
            md.deliverySeq = row.deliverySeq;
            md.ensureWorkspace();
        }
        md.deliveryQty = row.deliveryQty;
        md.deliveryMinQty = row.deliveryMinQty;
        md.deliveryMaxQty = row.deliveryMaxQty;
        md.ppq = row.ppq;
        md.deliveryGranularity = row.deliveryGranularity;
        md.earlyAllowDays = row.earlyAllowDays;
        md.lateAllowDays = row.lateAllowDays;
        md.requestedDate = row.requestedDate;
        md.confirmedDate = row.confirmedDate;
        md.status = row.lineStatus;
        md.persist();

        String coldId = OntologyIds.customerOrderLineDeliveryId(
                row.customerOrderNo, row.lineNo, row.deliverySeq);
        String demandId = OntologyIds.demandFromCustomerDeliveryId(coldId);
        TxnCustomerOrderLineEntity col = findCustomerOrderLine(row.customerOrderNo, row.lineNo);
        String productCode = col != null ? col.productCode : null;

        TxnDemandEntity demand = findDemand(demandId);
        if (demand == null) {
            demand = new TxnDemandEntity();
            demand.demandId = demandId;
            demand.ensureWorkspace();
        }
        demand.productCode = productCode;
        demand.quantity = row.deliveryQty != null ? row.deliveryQty : BigDecimal.ZERO;
        demand.needDate = row.requestedDate;
        demand.priority = 5;
        demand.sourceType = "CUSTOMER_DELIVERY";
        demand.sourceId = coldId;
        demand.persist();

        row.internalKey = coldId;
    }

    private static void upsertInventory(ExternalInventoryEntity row) {
        TxnInventoryBalanceEntity md = findInventory(row.productCode, row.stockingPointCode);
        if (md == null) {
            md = new TxnInventoryBalanceEntity();
            md.productCode = row.productCode;
            md.stockingPointCode = row.stockingPointCode;
            md.ensureWorkspace();
        }
        md.onHandQty = row.onHandQty;
        md.availableQty = row.availableQty;
        md.asOfDate = row.asOfDate;
        md.persist();
        row.internalKey = md.productCode + "|" + md.stockingPointCode;
    }

    private static void upsertPurchaseOrder(ExternalPurchaseOrderEntity row) {
        TxnPurchaseOrderEntity md = findPurchaseOrder(row.purchaseOrderNo, row.lineNo);
        if (md == null) {
            md = new TxnPurchaseOrderEntity();
            md.purchaseOrderNo = row.purchaseOrderNo;
            md.lineNo = row.lineNo;
            md.ensureWorkspace();
        }
        md.productCode = row.productCode;
        md.stockingPointCode = row.stockingPointCode;
        md.orderQty = row.orderQty;
        md.openQty = row.openQty;
        md.availableDate = row.promisedDate;
        md.status = row.poStatus;
        md.persist();
        row.internalKey = md.purchaseOrderNo + "|" + md.lineNo;
    }

    private static void upsertSupplyOrderAndPlanUnit(ExternalWorkOrderEntity row) {
        TxnSupplyOrderEntity md = findSupplyOrder(row.workOrderNo);
        if (md == null) {
            md = new TxnSupplyOrderEntity();
            md.supplyOrderId = row.workOrderNo;
            md.ensureWorkspace();
        }
        md.productCode = row.productCode;
        md.quantity = row.quantity;
        md.needDate = row.needDate;
        md.parentSupplyOrderId = row.parentWorkOrderNo;
        md.firmStatus = row.firmFlag ? TxnSupplyOrderEntity.FIRM_STATUS_FIRM : TxnSupplyOrderEntity.FIRM_STATUS_PLANNED;
        md.sourceType = row.sourceType != null ? row.sourceType : "EXTERNAL_SYNC";
        md.dispatchStatus = row.dispatchStatus;
        md.persist();

        String planUnitId = OntologyIds.planUnitId(row.workOrderNo, 0);
        TxnPlanUnitEntity pu = findPlanUnit(planUnitId);
        if (pu == null) {
            pu = new TxnPlanUnitEntity();
            pu.planUnitId = planUnitId;
            pu.supplyOrderId = row.workOrderNo;
            pu.ensureWorkspace();
        }
        pu.quantity = row.quantity;
        pu.sequenceNo = 0;
        pu.persist();

        row.internalKey = md.supplyOrderId;
    }

    private static void upsertOperation(ExternalWorkOrderOperationEntity row) {
        String operationId = OntologyIds.operationId(row.workOrderNo, row.operationSeq);
        TxnOperationEntity md = findOperation(operationId);
        if (md == null) {
            md = new TxnOperationEntity();
            md.operationId = operationId;
            md.supplyOrderId = row.workOrderNo;
            md.ensureWorkspace();
        }
        md.planUnitId = OntologyIds.planUnitId(row.workOrderNo, row.planUnitSeq);
        md.routingSequenceNo = row.operationSeq;
        md.operationCode = row.operationCode;
        md.operationName = row.operationName;
        md.plannedStart = row.plannedStart;
        md.plannedEnd = row.plannedEnd;
        md.persist();
        row.internalKey = operationId;
    }

    private static void upsertOperationOsr(ExternalWorkOrderOperationResourceEntity row) {
        String operationId = OntologyIds.operationId(row.workOrderNo, row.operationSeq);
        TxnOperationOsrEntity md = findOperationOsr(operationId, row.standardResourceCode);
        if (md == null) {
            md = new TxnOperationOsrEntity();
            md.operationId = operationId;
            md.supplyOrderId = row.workOrderNo;
            md.standardResourceCode = row.standardResourceCode;
            md.ensureWorkspace();
        }
        md.resourcePriority = row.resourcePriority;
        md.setupTimeMinutes = row.setupTimeMinutes;
        md.processTimeSeconds = row.processTimeSeconds;
        md.persist();
        row.internalKey = operationId + "|" + md.standardResourceCode;
    }

    private static TxnCustomerOrderEntity findCustomerOrder(String no) {
        return TxnCustomerOrderEntity.find("workspaceId = ?1 and customerOrderNo = ?2", TxnCustomerOrderEntity.ws(), no)
                .firstResult();
    }

    private static TxnCustomerOrderLineEntity findCustomerOrderLine(String no, int lineNo) {
        return TxnCustomerOrderLineEntity.find(
                        "workspaceId = ?1 and customerOrderNo = ?2 and lineNo = ?3",
                        TxnCustomerOrderLineEntity.ws(),
                        no,
                        lineNo)
                .firstResult();
    }

    private static TxnCustomerOrderLineDeliveryEntity findCold(String no, int lineNo, int seq) {
        return TxnCustomerOrderLineDeliveryEntity.find(
                        "workspaceId = ?1 and customerOrderNo = ?2 and lineNo = ?3 and deliverySeq = ?4",
                        TxnCustomerOrderLineDeliveryEntity.ws(),
                        no,
                        lineNo,
                        seq)
                .firstResult();
    }

    private static TxnDemandEntity findDemand(String demandId) {
        return TxnDemandEntity.find("workspaceId = ?1 and demandId = ?2", TxnDemandEntity.ws(), demandId)
                .firstResult();
    }

    private static TxnInventoryBalanceEntity findInventory(String productCode, String spCode) {
        return TxnInventoryBalanceEntity.find(
                        "workspaceId = ?1 and productCode = ?2 and stockingPointCode = ?3",
                        TxnInventoryBalanceEntity.ws(),
                        productCode,
                        spCode)
                .firstResult();
    }

    private static TxnPurchaseOrderEntity findPurchaseOrder(String poNo, int lineNo) {
        return TxnPurchaseOrderEntity.find(
                        "workspaceId = ?1 and purchaseOrderNo = ?2 and lineNo = ?3",
                        TxnPurchaseOrderEntity.ws(),
                        poNo,
                        lineNo)
                .firstResult();
    }

    private static TxnSupplyOrderEntity findSupplyOrder(String supplyOrderId) {
        return TxnSupplyOrderEntity.find(
                        "workspaceId = ?1 and supplyOrderId = ?2", TxnSupplyOrderEntity.ws(), supplyOrderId)
                .firstResult();
    }

    private static TxnPlanUnitEntity findPlanUnit(String planUnitId) {
        return TxnPlanUnitEntity.find("workspaceId = ?1 and planUnitId = ?2", TxnPlanUnitEntity.ws(), planUnitId)
                .firstResult();
    }

    private static TxnOperationEntity findOperation(String operationId) {
        return TxnOperationEntity.find("workspaceId = ?1 and operationId = ?2", TxnOperationEntity.ws(), operationId)
                .firstResult();
    }

    private static TxnOperationOsrEntity findOperationOsr(String operationId, String srCode) {
        return TxnOperationOsrEntity.find(
                        "workspaceId = ?1 and operationId = ?2 and standardResourceCode = ?3",
                        TxnOperationOsrEntity.ws(),
                        operationId,
                        srCode)
                .firstResult();
    }
}
