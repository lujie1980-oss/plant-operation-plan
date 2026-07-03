package com.plantops.masterdata.sync;

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
import com.plantops.persistence.entity.MdPhysicalResourceEntity;
import com.plantops.persistence.entity.MdPispEntity;
import com.plantops.persistence.entity.MdResourceGroupEntity;
import com.plantops.persistence.entity.MdRoutingEntity;
import com.plantops.persistence.entity.MdRoutingStepEntity;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.persistence.entity.MdRoutingStepOmEntity;
import com.plantops.persistence.entity.MdRoutingStepOsrEntity;
import com.plantops.persistence.entity.MdStandardResourceEntity;
import com.plantops.persistence.entity.MdStockingPointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** §11 · TODO-13 M3：PASSED external_* → md_*（AC-MD-03）。 */
@ApplicationScoped
public class MasterDataSyncService {

    @Inject
    MasterDataLegacyBridge legacyBridge;

    public record SyncReport(String importBatchId, int syncedRows, int skippedRows) {}

    @Transactional
    public SyncReport syncPassedBatch(String importBatchId) {
        if (importBatchId == null || importBatchId.isBlank()) {
            throw new IllegalArgumentException("importBatchId 不能为空");
        }
        int synced = 0;
        int skipped = 0;

        for (ExternalStockingPointEntity row : ExternalStockingPointEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertStockingPoint(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalResourceGroupEntity row : ExternalResourceGroupEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertResourceGroup(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalStandardResourceEntity row : ExternalStandardResourceEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertStandardResource(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalPhysicalResourceEntity row : ExternalPhysicalResourceEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertPhysicalResource(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalProductInStockingPointEntity row :
                ExternalProductInStockingPointEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertPisp(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalRoutingEntity row : ExternalRoutingEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertRouting(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalRoutingStepEntity row : ExternalRoutingStepEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertRoutingStep(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalRoutingStepOsrEntity row : ExternalRoutingStepOsrEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertRoutingStepOsr(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalRoutingStepImEntity row : ExternalRoutingStepImEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertRoutingStepIm(row);
                synced++;
            } else {
                skipped++;
            }
        }
        for (ExternalRoutingStepOmEntity row : ExternalRoutingStepOmEntity.listForBatch(importBatchId)) {
            if (syncRow(row)) {
                upsertRoutingStepOm(row);
                synced++;
            } else {
                skipped++;
            }
        }

        legacyBridge.syncFromMd();
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

    private static void upsertStockingPoint(ExternalStockingPointEntity row) {
        MdStockingPointEntity md = findStockingPoint(row.stockingPointCode);
        if (md == null) {
            md = new MdStockingPointEntity();
            md.code = row.stockingPointCode;
            md.ensureWorkspace();
        }
        md.name = row.stockingPointName;
        md.siteCode = row.siteCode;
        md.persist();
        row.internalKey = md.code;
    }

    private static void upsertResourceGroup(ExternalResourceGroupEntity row) {
        MdResourceGroupEntity md = findResourceGroup(row.resourceGroupCode);
        if (md == null) {
            md = new MdResourceGroupEntity();
            md.code = row.resourceGroupCode;
            md.ensureWorkspace();
        }
        md.name = row.resourceGroupName;
        md.calendarCode = row.calendarCode;
        md.resourceEfficiency = row.resourceEfficiency;
        md.persist();
        row.internalKey = md.code;
    }

    private static void upsertStandardResource(ExternalStandardResourceEntity row) {
        MdStandardResourceEntity md = findStandardResource(row.standardResourceCode);
        if (md == null) {
            md = new MdStandardResourceEntity();
            md.code = row.standardResourceCode;
            md.ensureWorkspace();
        }
        md.name = row.standardResourceName;
        md.resourceGroupCode = row.resourceGroupCode;
        md.capacityUom = row.capacityUom;
        md.bottleneck = row.bottleneck;
        md.resourceEfficiency = row.resourceEfficiency;
        md.persist();
        row.internalKey = md.code;
    }

    private static void upsertPhysicalResource(ExternalPhysicalResourceEntity row) {
        MdPhysicalResourceEntity md = findPhysicalResource(row.physicalResourceCode);
        if (md == null) {
            md = new MdPhysicalResourceEntity();
            md.code = row.physicalResourceCode;
            md.ensureWorkspace();
        }
        md.name = row.physicalResourceName;
        md.standardResourceCode = row.standardResourceCode;
        md.productionLineCode = row.productionLineCode;
        md.status = row.status;
        md.persist();
        row.internalKey = md.code;
    }

    private static void upsertPisp(ExternalProductInStockingPointEntity row) {
        MdPispEntity md = findPisp(row.productCode, row.stockingPointCode);
        if (md == null) {
            md = new MdPispEntity();
            md.productCode = row.productCode;
            md.stockingPointCode = row.stockingPointCode;
            md.ensureWorkspace();
        }
        md.planningRelevant = row.planningRelevant;
        md.ppq = row.ppq;
        md.lotSize = row.lotSize;
        md.minQuantity = row.minQuantity;
        md.maxQuantity = row.maxQuantity;
        md.minQtyStrategy = row.minQtyStrategy;
        md.procurementType = row.procurementType;
        md.persist();
        row.internalKey = md.productCode + "|" + md.stockingPointCode;
    }

    private static void upsertRouting(ExternalRoutingEntity row) {
        MdRoutingEntity md = findRouting(row.routingCode);
        if (md == null) {
            md = new MdRoutingEntity();
            md.routingCode = row.routingCode;
            md.ensureWorkspace();
        }
        md.productCode = row.productCode;
        md.stockingPointCode = row.stockingPointCode;
        md.pathPriority = row.pathPriority;
        md.name = row.routingName;
        md.effectiveFrom = row.effectiveFrom;
        md.effectiveTo = row.effectiveTo;
        md.persist();
        row.internalKey = md.routingCode;
    }

    private static void upsertRoutingStep(ExternalRoutingStepEntity row) {
        MdRoutingStepEntity md = findRoutingStep(row.routingCode, row.sequenceNo);
        if (md == null) {
            md = new MdRoutingStepEntity();
            md.routingCode = row.routingCode;
            md.sequenceNo = row.sequenceNo;
            md.ensureWorkspace();
        }
        md.operationCode = row.operationCode;
        md.operationName = row.operationName;
        md.resourceGroupCode = row.standardResourceGroupCode;
        md.yieldRate = row.yieldRate;
        md.preProcessingMinutes = row.preProcessingMinutes;
        md.schedulingSpaceMinutes = row.schedulingSpaceMinutes;
        md.productionMinutes = row.productionMinutes;
        md.postProcessingMinutes = row.postProcessingMinutes;
        md.persist();
        row.internalKey = md.routingCode + "|" + md.sequenceNo;
    }

    private static void upsertRoutingStepOsr(ExternalRoutingStepOsrEntity row) {
        MdRoutingStepOsrEntity md = findRoutingStepOsr(row.routingCode, row.sequenceNo, row.standardResourceCode);
        if (md == null) {
            md = new MdRoutingStepOsrEntity();
            md.routingCode = row.routingCode;
            md.sequenceNo = row.sequenceNo;
            md.standardResourceCode = row.standardResourceCode;
            md.ensureWorkspace();
        }
        md.resourcePriority = row.resourcePriority;
        md.setupTimeMinutes = row.setupTimeMinutes;
        md.processTimeSeconds = row.processTimeSeconds;
        md.processTimeUom = row.processTimeUom;
        md.productionRate = row.productionRate;
        md.resourceUsageType = row.resourceUsageType != null ? row.resourceUsageType : "SINGLE";
        md.batchSize = row.batchSize;
        md.batchDurationMinutes = row.batchDurationMinutes;
        md.persist();
        row.internalKey = md.routingCode + "|" + md.sequenceNo + "|" + md.standardResourceCode;
    }

    private static void upsertRoutingStepIm(ExternalRoutingStepImEntity row) {
        MdRoutingStepImEntity md = findRoutingStepIm(row.routingCode, row.sequenceNo, row.componentProductCode);
        if (md == null) {
            md = new MdRoutingStepImEntity();
            md.routingCode = row.routingCode;
            md.sequenceNo = row.sequenceNo;
            md.componentProductCode = row.componentProductCode;
            md.ensureWorkspace();
        }
        md.componentQty = row.componentQty;
        md.componentUom = row.componentUom;
        md.issueStockingPointCode = row.issueStockingPointCode;
        md.persist();
        row.internalKey = md.routingCode + "|" + md.sequenceNo + "|" + md.componentProductCode;
    }

    private static void upsertRoutingStepOm(ExternalRoutingStepOmEntity row) {
        MdRoutingStepOmEntity md = findRoutingStepOm(row.routingCode, row.sequenceNo, row.outputProductCode);
        if (md == null) {
            md = new MdRoutingStepOmEntity();
            md.routingCode = row.routingCode;
            md.sequenceNo = row.sequenceNo;
            md.outputProductCode = row.outputProductCode;
            md.ensureWorkspace();
        }
        md.outputQty = row.outputQty;
        md.receiveStockingPointCode = row.receiveStockingPointCode;
        md.persist();
        row.internalKey = md.routingCode + "|" + md.sequenceNo + "|" + md.outputProductCode;
    }

    private static MdStockingPointEntity findStockingPoint(String code) {
        return MdStockingPointEntity.find("workspaceId = ?1 and code = ?2", MdStockingPointEntity.ws(), code)
                .firstResult();
    }

    private static MdResourceGroupEntity findResourceGroup(String code) {
        return MdResourceGroupEntity.find("workspaceId = ?1 and code = ?2", MdResourceGroupEntity.ws(), code)
                .firstResult();
    }

    private static MdStandardResourceEntity findStandardResource(String code) {
        return MdStandardResourceEntity.find("workspaceId = ?1 and code = ?2", MdStandardResourceEntity.ws(), code)
                .firstResult();
    }

    private static MdPhysicalResourceEntity findPhysicalResource(String code) {
        return MdPhysicalResourceEntity.find("workspaceId = ?1 and code = ?2", MdPhysicalResourceEntity.ws(), code)
                .firstResult();
    }

    private static MdPispEntity findPisp(String productCode, String stockingPointCode) {
        return MdPispEntity.find(
                        "workspaceId = ?1 and productCode = ?2 and stockingPointCode = ?3",
                        MdPispEntity.ws(),
                        productCode,
                        stockingPointCode)
                .firstResult();
    }

    private static MdRoutingEntity findRouting(String routingCode) {
        return MdRoutingEntity.find("workspaceId = ?1 and routingCode = ?2", MdRoutingEntity.ws(), routingCode)
                .firstResult();
    }

    private static MdRoutingStepEntity findRoutingStep(String routingCode, int sequenceNo) {
        return MdRoutingStepEntity.find(
                        "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3",
                        MdRoutingStepEntity.ws(),
                        routingCode,
                        sequenceNo)
                .firstResult();
    }

    private static MdRoutingStepOsrEntity findRoutingStepOsr(
            String routingCode, int sequenceNo, String standardResourceCode) {
        return MdRoutingStepOsrEntity.find(
                        "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3 and standardResourceCode = ?4",
                        MdRoutingStepOsrEntity.ws(),
                        routingCode,
                        sequenceNo,
                        standardResourceCode)
                .firstResult();
    }

    private static MdRoutingStepImEntity findRoutingStepIm(
            String routingCode, int sequenceNo, String componentProductCode) {
        return MdRoutingStepImEntity.find(
                        "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3 and componentProductCode = ?4",
                        MdRoutingStepImEntity.ws(),
                        routingCode,
                        sequenceNo,
                        componentProductCode)
                .firstResult();
    }

    private static MdRoutingStepOmEntity findRoutingStepOm(
            String routingCode, int sequenceNo, String outputProductCode) {
        return MdRoutingStepOmEntity.find(
                        "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3 and outputProductCode = ?4",
                        MdRoutingStepOmEntity.ws(),
                        routingCode,
                        sequenceNo,
                        outputProductCode)
                .firstResult();
    }
}
