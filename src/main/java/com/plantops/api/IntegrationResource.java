package com.plantops.api;

import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.QualityCheckRequest;
import com.plantops.api.dto.integration.IntegrationDtos.QualityCheckResult;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.SyncRequest;
import com.plantops.api.dto.integration.IntegrationDtos.SyncResult;
import com.plantops.integration.erp.ErpPort;
import com.plantops.integration.mes.MesPort;
import com.plantops.masterdata.external.ExternalMasterDataCatalog;
import com.plantops.masterdata.external.MasterDataExternalImportService;
import com.plantops.masterdata.quality.MasterDataQualityService;
import com.plantops.masterdata.sync.MasterDataSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/integration")
@Produces(MediaType.APPLICATION_JSON)
public class IntegrationResource {

    @Inject
    ErpPort erpPort;

    @Inject
    MesPort mesPort;

    @Inject
    MasterDataExternalImportService externalImportService;

    @Inject
    MasterDataQualityService qualityService;

    @Inject
    MasterDataSyncService syncService;

    @GET
    @Path("/erp/orders")
    public Object erpOrders() {
        return erpPort.fetchOpenOrderLines();
    }

    @GET
    @Path("/mes/status")
    public Object mesStatus() {
        return java.util.Map.of("adapter", "mock", "feedback", mesPort.pollFeedback());
    }

    @GET
    @Path("/external/{domain}/tables")
    public List<?> listExternalTables(@PathParam("domain") String domain) {
        if (!"master".equals(domain)) {
            throw new NotFoundException("Unknown external domain: " + domain);
        }
        return ExternalMasterDataCatalog.masterTables();
    }

    @POST
    @Path("/master-data/import/routing")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportBatchResult importRoutingBundle(RoutingBundleImport bundle) {
        return externalImportService.importRoutingBundle(bundle);
    }

    @POST
    @Path("/master-data/quality/check")
    @Consumes(MediaType.APPLICATION_JSON)
    public QualityCheckResult checkQuality(QualityCheckRequest request) {
        MasterDataQualityService.QualityReport report = qualityService.checkBatch(request.importBatchId());
        return new QualityCheckResult(
                report.importBatchId(),
                report.pendingCount(),
                report.passedCount(),
                report.failedCount(),
                report.warningCount());
    }

    @POST
    @Path("/master-data/sync")
    @Consumes(MediaType.APPLICATION_JSON)
    public SyncResult syncMasterData(SyncRequest request) {
        MasterDataSyncService.SyncReport report = syncService.syncPassedBatch(request.importBatchId());
        return new SyncResult(report.importBatchId(), report.syncedRows(), report.skippedRows());
    }
}
