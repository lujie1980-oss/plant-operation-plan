package com.plantops.api;

import com.plantops.api.dto.integration.IntegrationDtos.ExternalRowPageDto;
import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterConfigDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterStatusDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationBatchDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationExcelUploadResultDto;
import com.plantops.api.dto.integration.IntegrationDtos.QualityCheckRequest;
import com.plantops.api.dto.integration.IntegrationDtos.QualityCheckResult;
import com.plantops.api.dto.integration.IntegrationDtos.QualityReportDto;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.SyncRequest;
import com.plantops.api.dto.integration.IntegrationDtos.SyncResult;
import com.plantops.api.dto.integration.IntegrationDtos.TransactionalBundleImport;
import com.plantops.integration.adapter.IntegrationAdapterService;
import com.plantops.integration.batch.IntegrationBatchService;
import com.plantops.integration.erp.ErpPort;
import com.plantops.integration.external.ExternalDataBrowseService;
import com.plantops.integration.mes.MesPort;
import com.plantops.integration.quality.IntegrationQualityReportService;
import com.plantops.masterdata.external.ExternalMasterDataCatalog;
import com.plantops.masterdata.external.MasterDataExternalImportService;
import com.plantops.masterdata.quality.MasterDataQualityService;
import com.plantops.masterdata.sync.MasterDataSyncService;
import com.plantops.transactional.external.TransactionalDataExternalImportService;
import com.plantops.transactional.quality.TransactionalDataQualityService;
import com.plantops.transactional.sync.TransactionalDataSyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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

    @Inject
    TransactionalDataExternalImportService transactionalImportService;

    @Inject
    TransactionalDataQualityService transactionalQualityService;

    @Inject
    TransactionalDataSyncService transactionalSyncService;

    @Inject
    IntegrationBatchService batchService;

    @Inject
    ExternalDataBrowseService browseService;

    @Inject
    IntegrationAdapterService adapterService;

    @Inject
    IntegrationQualityReportService qualityReportService;

    @GET
    @Path("/batches")
    public List<IntegrationBatchDto> listBatches(@QueryParam("limit") @DefaultValue("20") int limit) {
        return batchService.listBatches(limit);
    }

    @GET
    @Path("/adapters")
    public List<IntegrationAdapterStatusDto> listAdapters() {
        return adapterService.listAdapters();
    }

    @POST
    @Path("/adapters/{adapterId}/run")
    public IntegrationAdapterRunResultDto runAdapter(@PathParam("adapterId") String adapterId) {
        return adapterService.runAdapter(adapterId);
    }

    @PUT
    @Path("/adapters/{adapterId}/config")
    @Consumes(MediaType.APPLICATION_JSON)
    public IntegrationAdapterConfigDto saveAdapterConfig(
            @PathParam("adapterId") String adapterId, IntegrationAdapterConfigDto config) {
        return adapterService.saveConfig(adapterId, config);
    }

    @POST
    @Path("/adapters/excel/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public IntegrationExcelUploadResultDto uploadExcel(
            @RestForm("file") FileUpload file, @RestForm("validateOnly") String validateOnly) throws IOException {
        if (file == null || file.uploadedFile() == null) {
            throw new BadRequestException("file required");
        }
        boolean validate = validateOnly != null && Boolean.parseBoolean(validateOnly);
        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            return adapterService.uploadExcel(in, validate);
        }
    }

    @GET
    @Path("/quality")
    public QualityReportDto qualityReport(
            @QueryParam("importBatchId") String importBatchId, @QueryParam("issueCode") String issueCode) {
        return qualityReportService.report(importBatchId, issueCode);
    }

    @GET
    @Path("/external/{domain}/{table}")
    public ExternalRowPageDto browseExternal(
            @PathParam("domain") String domain,
            @PathParam("table") String table,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("importBatchId") String importBatchId,
            @QueryParam("qualityStatus") String qualityStatus) {
        return browseService.browse(domain, table, page, size, importBatchId, qualityStatus);
    }

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
        return switch (domain) {
            case "master" -> ExternalMasterDataCatalog.masterTables();
            case "transactional" -> ExternalMasterDataCatalog.transactionalTables();
            default -> throw new NotFoundException("Unknown external domain: " + domain);
        };
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

    @POST
    @Path("/transactional-data/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportBatchResult importTransactionalBundle(TransactionalBundleImport bundle) {
        return transactionalImportService.importBundle(bundle);
    }

    @POST
    @Path("/transactional-data/quality/check")
    @Consumes(MediaType.APPLICATION_JSON)
    public QualityCheckResult checkTransactionalQuality(QualityCheckRequest request) {
        TransactionalDataQualityService.QualityReport report =
                transactionalQualityService.checkBatch(request.importBatchId());
        return new QualityCheckResult(
                report.importBatchId(),
                report.pendingCount(),
                report.passedCount(),
                report.failedCount(),
                report.warningCount());
    }

    @POST
    @Path("/transactional-data/sync")
    @Consumes(MediaType.APPLICATION_JSON)
    public SyncResult syncTransactionalData(SyncRequest request) {
        TransactionalDataSyncService.SyncReport report =
                transactionalSyncService.syncPassedBatch(request.importBatchId());
        return new SyncResult(report.importBatchId(), report.syncedRows(), report.skippedRows());
    }
}
