package com.plantops.masterdata.external;

import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.PhysicalResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.PispRow;
import com.plantops.api.dto.integration.IntegrationDtos.ResourceGroupRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepImRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepOsrRow;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingStepRow;
import com.plantops.api.dto.integration.IntegrationDtos.StandardResourceRow;
import com.plantops.api.dto.integration.IntegrationDtos.StockingPointRow;
import com.plantops.persistence.entity.ExternalPhysicalResourceEntity;
import com.plantops.persistence.entity.ExternalProductInStockingPointEntity;
import com.plantops.persistence.entity.ExternalResourceGroupEntity;
import com.plantops.persistence.entity.ExternalRoutingEntity;
import com.plantops.persistence.entity.ExternalRoutingStepEntity;
import com.plantops.persistence.entity.ExternalRoutingStepImEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOsrEntity;
import com.plantops.persistence.entity.ExternalStandardResourceEntity;
import com.plantops.persistence.entity.ExternalStockingPointEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** §11 · TODO-13 M1：只写 external_* staging（AC-MD-01 · RULE-MD-02）。 */
@ApplicationScoped
public class MasterDataExternalImportService {

    @Transactional
    public ImportBatchResult importRoutingBundle(RoutingBundleImport bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle 不能为空");
        }
        String sourceSystem = bundle.sourceSystem() != null && !bundle.sourceSystem().isBlank()
                ? bundle.sourceSystem()
                : "API";
        String batchId = "MD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int rows = 0;

        rows += persistStockingPoints(batchId, sourceSystem, bundle.stockingPoints());
        rows += persistResourceGroups(batchId, sourceSystem, bundle.resourceGroups());
        rows += persistStandardResources(batchId, sourceSystem, bundle.standardResources());
        rows += persistPhysicalResources(batchId, sourceSystem, bundle.physicalResources());
        rows += persistPisps(batchId, sourceSystem, bundle.productInStockingPoints());
        rows += persistRoutings(batchId, sourceSystem, bundle.routings());
        rows += persistRoutingSteps(batchId, sourceSystem, bundle.routingSteps());
        rows += persistRoutingStepOsrs(batchId, sourceSystem, bundle.routingStepOsrs());
        rows += persistRoutingStepIms(batchId, sourceSystem, bundle.routingStepInputMaterials());

        return new ImportBatchResult(batchId, rows, sourceSystem);
    }

    private static int persistStockingPoints(String batchId, String sourceSystem, List<StockingPointRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (StockingPointRow row : rows) {
            ExternalStockingPointEntity entity = new ExternalStockingPointEntity();
            entity.stockingPointCode = row.code();
            entity.stockingPointName = row.name();
            entity.siteCode = row.siteCode();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistResourceGroups(String batchId, String sourceSystem, List<ResourceGroupRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ResourceGroupRow row : rows) {
            ExternalResourceGroupEntity entity = new ExternalResourceGroupEntity();
            entity.resourceGroupCode = row.code();
            entity.resourceGroupName = row.name();
            entity.calendarCode = row.calendarCode();
            entity.resourceEfficiency = row.resourceEfficiency();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistStandardResources(String batchId, String sourceSystem, List<StandardResourceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (StandardResourceRow row : rows) {
            ExternalStandardResourceEntity entity = new ExternalStandardResourceEntity();
            entity.standardResourceCode = row.code();
            entity.standardResourceName = row.name();
            entity.resourceGroupCode = row.resourceGroupCode();
            entity.capacityUom = row.capacityUom();
            entity.bottleneck = row.bottleneck();
            entity.resourceEfficiency =
                    row.resourceEfficiency() != null ? row.resourceEfficiency() : BigDecimal.ONE;
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistPhysicalResources(String batchId, String sourceSystem, List<PhysicalResourceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PhysicalResourceRow row : rows) {
            ExternalPhysicalResourceEntity entity = new ExternalPhysicalResourceEntity();
            entity.physicalResourceCode = row.code();
            entity.physicalResourceName = row.name();
            entity.standardResourceCode = row.standardResourceCode();
            entity.productionLineCode = row.productionLineCode();
            entity.status = row.status() != null ? row.status() : "ACTIVE";
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistPisps(String batchId, String sourceSystem, List<PispRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PispRow row : rows) {
            ExternalProductInStockingPointEntity entity = new ExternalProductInStockingPointEntity();
            entity.productCode = row.productCode();
            entity.stockingPointCode = row.stockingPointCode();
            entity.planningRelevant = row.planningRelevant();
            entity.ppq = row.ppq();
            entity.lotSize = row.lotSize();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistRoutings(String batchId, String sourceSystem, List<RoutingRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoutingRow row : rows) {
            ExternalRoutingEntity entity = new ExternalRoutingEntity();
            entity.routingCode = row.routingCode();
            entity.productCode = row.productCode();
            entity.stockingPointCode = row.stockingPointCode();
            entity.pathPriority = row.pathPriority() > 0 ? row.pathPriority() : 1;
            entity.routingName = row.routingName();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistRoutingSteps(String batchId, String sourceSystem, List<RoutingStepRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoutingStepRow row : rows) {
            ExternalRoutingStepEntity entity = new ExternalRoutingStepEntity();
            entity.routingCode = row.routingCode();
            entity.sequenceNo = row.sequenceNo();
            entity.operationCode = row.operationCode();
            entity.operationName = row.operationName();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistRoutingStepOsrs(String batchId, String sourceSystem, List<RoutingStepOsrRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoutingStepOsrRow row : rows) {
            ExternalRoutingStepOsrEntity entity = new ExternalRoutingStepOsrEntity();
            entity.routingCode = row.routingCode();
            entity.sequenceNo = row.sequenceNo();
            entity.standardResourceCode = row.standardResourceCode();
            entity.resourcePriority = row.resourcePriority() > 0 ? row.resourcePriority() : 1;
            entity.setupTimeMinutes = row.setupTimeMinutes();
            entity.processTimeSeconds =
                    row.processTimeSeconds() != null ? row.processTimeSeconds() : BigDecimal.valueOf(60);
            entity.resourceUsageType = "SINGLE";
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }

    private static int persistRoutingStepIms(String batchId, String sourceSystem, List<RoutingStepImRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoutingStepImRow row : rows) {
            ExternalRoutingStepImEntity entity = new ExternalRoutingStepImEntity();
            entity.routingCode = row.routingCode();
            entity.sequenceNo = row.sequenceNo();
            entity.componentProductCode = row.componentProductCode();
            entity.componentQty = row.componentQty() != null ? row.componentQty() : BigDecimal.ONE;
            entity.issueStockingPointCode = row.issueStockingPointCode();
            entity.stampImport(batchId, sourceSystem);
            entity.persist();
            count++;
        }
        return count;
    }
}
