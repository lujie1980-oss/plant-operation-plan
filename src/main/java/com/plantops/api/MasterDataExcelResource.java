package com.plantops.api;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.masterdata.ChangeoverExcelImportService;
import com.plantops.masterdata.EquipmentLineExcelImportService;
import com.plantops.masterdata.MasterDataExcelService;
import com.plantops.masterdata.ParallelOperationExcelImportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Path("/api/v1/master-data/excel")
public class MasterDataExcelResource {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Inject
    MasterDataExcelService excelService;

    @Inject
    ChangeoverExcelImportService changeoverExcelImportService;

    @Inject
    ParallelOperationExcelImportService parallelOperationExcelImportService;

    @Inject
    EquipmentLineExcelImportService equipmentLineExcelImportService;

    @GET
    @Path("/template")
    @Produces(XLSX)
    public Response downloadTemplate() {
        return Response.ok(excelService.buildTemplate())
                .header("Content-Disposition", "attachment; filename=\"master-data-template.xlsx\"")
                .build();
    }

    @GET
    @Path("/export")
    @Produces(XLSX)
    public Response exportAll() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return Response.ok(excelService.buildExport())
                .header("Content-Disposition", "attachment; filename=\"master-data-export-" + ts + ".xlsx\"")
                .build();
    }

    @POST
    @Path("/import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importExcel(
            byte[] body,
            @QueryParam("replace") boolean replace) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("请上传 Excel 文件内容");
        }
        return excelService.importWorkbook(new ByteArrayInputStream(body), replace);
    }

    @POST
    @Path("/changeover-import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importChangeoverExcel(
            byte[] body,
            @QueryParam("replace") boolean replace) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("请上传换型规则 Excel 文件内容");
        }
        return changeoverExcelImportService.importWorkbook(new ByteArrayInputStream(body), replace);
    }

    @POST
    @Path("/parallel-operation-import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importParallelOperationExcel(
            byte[] body,
            @QueryParam("replace") boolean replace) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("请上传并行工序规则 Excel 文件内容");
        }
        return parallelOperationExcelImportService.importWorkbook(new ByteArrayInputStream(body), replace);
    }

    /** @deprecated use {@link #importParallelOperationExcel} */
    @POST
    @Path("/u-line-pair-import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importULinePairExcel(
            byte[] body,
            @QueryParam("replace") boolean replace) {
        return importParallelOperationExcel(body, replace);
    }

    @POST
    @Path("/equipment-line-import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importEquipmentLineExcel(
            byte[] body,
            @QueryParam("replace") boolean replace) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("请上传设备基础信息 Excel 文件内容");
        }
        return equipmentLineExcelImportService.importWorkbook(new ByteArrayInputStream(body), replace);
    }
}
