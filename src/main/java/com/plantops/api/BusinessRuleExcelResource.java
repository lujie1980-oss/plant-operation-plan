package com.plantops.api;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.masterdata.BusinessRuleExcelKind;
import com.plantops.masterdata.BusinessRuleExcelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Path("/api/v1/business-rules/excel")
public class BusinessRuleExcelResource {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Inject
    BusinessRuleExcelService businessRuleExcelService;

    @GET
    @Path("/{kind}/template")
    @Produces(XLSX)
    public Response downloadTemplate(@PathParam("kind") String kind) {
        BusinessRuleExcelKind ruleKind = BusinessRuleExcelKind.fromPath(kind);
        return Response.ok(businessRuleExcelService.buildTemplate(ruleKind))
                .header("Content-Disposition", "attachment; filename=\"" + ruleKind.pathSegment + "-template.xlsx\"")
                .build();
    }

    @GET
    @Path("/{kind}/export")
    @Produces(XLSX)
    public Response exportRules(@PathParam("kind") String kind) {
        BusinessRuleExcelKind ruleKind = BusinessRuleExcelKind.fromPath(kind);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return Response.ok(businessRuleExcelService.buildExport(ruleKind))
                .header("Content-Disposition",
                        "attachment; filename=\"" + ruleKind.pathSegment + "-export-" + ts + ".xlsx\"")
                .build();
    }

    @POST
    @Path("/{kind}/import")
    @Consumes({XLSX, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public MasterDataImportResult importRules(
            @PathParam("kind") String kind,
            byte[] body,
            @QueryParam("replace") boolean replace) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("请上传 Excel 文件内容");
        }
        BusinessRuleExcelKind ruleKind = BusinessRuleExcelKind.fromPath(kind);
        return businessRuleExcelService.importWorkbook(ruleKind, new ByteArrayInputStream(body), replace);
    }
}
