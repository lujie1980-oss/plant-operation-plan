package com.plantops.masterdata;

import com.plantops.api.MasterDataResource;
import com.plantops.api.dto.masterdata.MasterDataDtos.SalesOrderDto;
import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.ValidationIssue;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.ContinuousProductionRuleEntity;
import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;
import com.plantops.scenario.ChangeoverAttributeKey;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class BusinessRuleExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DataFormatter CELL_FORMAT = new DataFormatter(Locale.ROOT);

    private static final List<DemandColumn> DEMAND_COLUMNS = List.of(
            new DemandColumn("id", "系统ID(留空=按订单号更新)"),
            new DemandColumn("salesOrderNo", "销售订单号"),
            new DemandColumn("salesOrderLineNo", "行号"),
            new DemandColumn("priority", "优先级"),
            new DemandColumn("expediteLevel", "加急等级"),
            new DemandColumn("scheduleLockFlag", "排程锁定(是/否)"),
            new DemandColumn("dueDate", "交期(yyyy-MM-dd)"));

    @Inject
    MasterDataExcelService masterDataExcelService;

    @Inject
    ChangeoverExcelImportService changeoverExcelImportService;

    @Inject
    ParallelOperationExcelImportService parallelOperationExcelImportService;

    @Inject
    OperationTransferTimeExcelImportService operationTransferTimeExcelImportService;

    @Inject
    ContinuousProductionExcelImportService continuousProductionExcelImportService;

    @Inject
    MasterDataResource masterDataResource;

    @Inject
    MasterDataValidationService validationService;

    public byte[] buildTemplate(BusinessRuleExcelKind kind) {
        return switch (kind) {
            case CHANGEOVER -> buildChangeoverWorkbook(false);
            case PARALLEL_OPERATIONS -> buildParallelWorkbook(false);
            case OPERATION_TRANSFER_TIME -> buildOperationTransferTimeWorkbook(false);
            case CONTINUOUS_PRODUCTION -> buildContinuousProductionWorkbook(false);
            case BOM_RULES -> masterDataExcelService.buildSingleSheetWorkbook(MasterDataExcelSheet.BOM, false);
            case SHIFT_HEADCOUNT_RULES ->
                    masterDataExcelService.buildSingleSheetWorkbook(MasterDataExcelSheet.SHIFT_HEADCOUNT, false);
            case DEMAND_PRIORITY_RULES -> buildDemandWorkbook(false);
        };
    }

    public byte[] buildExport(BusinessRuleExcelKind kind) {
        return switch (kind) {
            case CHANGEOVER -> buildChangeoverWorkbook(true);
            case PARALLEL_OPERATIONS -> buildParallelWorkbook(true);
            case OPERATION_TRANSFER_TIME -> buildOperationTransferTimeWorkbook(true);
            case CONTINUOUS_PRODUCTION -> buildContinuousProductionWorkbook(true);
            case BOM_RULES -> masterDataExcelService.buildSingleSheetWorkbook(MasterDataExcelSheet.BOM, true);
            case SHIFT_HEADCOUNT_RULES ->
                    masterDataExcelService.buildSingleSheetWorkbook(MasterDataExcelSheet.SHIFT_HEADCOUNT, true);
            case DEMAND_PRIORITY_RULES -> buildDemandWorkbook(true);
        };
    }

    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 3600)
    public MasterDataImportResult importWorkbook(BusinessRuleExcelKind kind, InputStream input, boolean replace) {
        return switch (kind) {
            case CHANGEOVER -> changeoverExcelImportService.importWorkbook(input, replace);
            case PARALLEL_OPERATIONS -> parallelOperationExcelImportService.importWorkbook(input, replace);
            case OPERATION_TRANSFER_TIME -> operationTransferTimeExcelImportService.importWorkbook(input, replace);
            case CONTINUOUS_PRODUCTION -> continuousProductionExcelImportService.importWorkbook(input, replace);
            case BOM_RULES -> masterDataExcelService.importSingleSheet(MasterDataExcelSheet.BOM, input, replace);
            case SHIFT_HEADCOUNT_RULES ->
                    masterDataExcelService.importSingleSheet(MasterDataExcelSheet.SHIFT_HEADCOUNT, input, replace);
            case DEMAND_PRIORITY_RULES -> importDemandWorkbook(input);
        };
    }

    private byte[] buildChangeoverWorkbook(boolean withData) {
        String sheetName = "KTPrefixDuration";
        List<String> headersCn = List.of("工序", "属性", "前任务属性值", "后任务属性值", "换型时长");
        List<String> headersEn = List.of("operationId", "attribute", "previousProductId", "nextProductId", "prefixDuration");
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeReadme(wb, "换型规则（泰科蓝图格式）：工作表 KTPrefixDuration；换型时长支持 HH:MM:SS 或分钟数。");
            Sheet sheet = wb.createSheet(sheetName);
            Row headerCn = sheet.createRow(0);
            for (int i = 0; i < headersCn.size(); i++) {
                headerCn.createCell(i).setCellValue(headersCn.get(i));
            }
            Row headerEn = sheet.createRow(1);
            for (int i = 0; i < headersEn.size(); i++) {
                headerEn.createCell(i).setCellValue(headersEn.get(i));
            }
            if (withData) {
                int rowIdx = 2;
                for (ChangeoverMatrixEntity e : ChangeoverMatrixEntity.listInWorkspace()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(e.operationName);
                    row.createCell(1).setCellValue(ChangeoverAttributeKey.displayLabel(e.attributeKey));
                    row.createCell(2).setCellValue(e.fromAttributeValue);
                    row.createCell(3).setCellValue(e.toAttributeValue);
                    row.createCell(4).setCellValue(ChangeoverExcelImportService.formatDurationMinutes(e.setupMinutes));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成换型规则 Excel 失败", e);
        }
    }

    private byte[] buildParallelWorkbook(boolean withData) {
        String sheetName = "U型线清单";
        List<String> headers = List.of("半品第一头PN", "半品第二头PN", "机台");
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeReadme(wb, "并行生产规则（U型线清单）：半品第一头 PN、半品第二头 PN、机台（产线 ID）。");
            Sheet sheet = wb.createSheet(sheetName);
            writeStringHeader(sheet, headers);
            if (withData) {
                int rowIdx = 1;
                for (ParallelOperationRuleEntity e : ParallelOperationRuleEntity.listInWorkspace()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(e.firstProductCode);
                    row.createCell(1).setCellValue(e.secondProductCode);
                    row.createCell(2).setCellValue(e.lineId);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成并行工序 Excel 失败", e);
        }
    }

    private byte[] buildOperationTransferTimeWorkbook(boolean withData) {
        String sheetName = "工序流转时间";
        List<String> headersCn = List.of("产品", "前工序", "后工序", "流转时间", "最小流转时间");
        List<String> headersEn = List.of(
                "productCode", "fromOperationName", "toOperationName", "transferMinutes", "minTransferMinutes");
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeReadme(wb, "工序流转时间：产品、前工序、后工序、流转时间、最小流转时间（支持 HH:MM:SS 或分钟数）。");
            Sheet sheet = wb.createSheet(sheetName);
            Row headerCn = sheet.createRow(0);
            for (int i = 0; i < headersCn.size(); i++) {
                headerCn.createCell(i).setCellValue(headersCn.get(i));
            }
            Row headerEn = sheet.createRow(1);
            for (int i = 0; i < headersEn.size(); i++) {
                headerEn.createCell(i).setCellValue(headersEn.get(i));
            }
            if (withData) {
                int rowIdx = 2;
                for (OperationTransferTimeRuleEntity e : OperationTransferTimeRuleEntity.listInWorkspace()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(e.productCode);
                    row.createCell(1).setCellValue(e.fromOperationName);
                    row.createCell(2).setCellValue(e.toOperationName);
                    row.createCell(3).setCellValue(ChangeoverExcelImportService.formatDurationMinutes(e.transferMinutes));
                    row.createCell(4).setCellValue(ChangeoverExcelImportService.formatDurationMinutes(e.minTransferMinutes));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成工序流转时间 Excel 失败", e);
        }
    }

    private byte[] buildContinuousProductionWorkbook(boolean withData) {
        String sheetName = "连续生产料号清单";
        List<String> headers = List.of("半品第一头PN", "半品第二头PN", "成品", "机台");
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeReadme(wb, "连续生产规则：指定机台上关联料号须连续排产，中间不得插入其它料号。");
            Sheet sheet = wb.createSheet(sheetName);
            writeStringHeader(sheet, headers);
            if (withData) {
                int rowIdx = 1;
                for (ContinuousProductionRuleEntity e : ContinuousProductionRuleEntity.listInWorkspace()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(e.firstProductCode);
                    row.createCell(1).setCellValue(e.secondProductCode);
                    row.createCell(2).setCellValue(e.finishedProductCode);
                    row.createCell(3).setCellValue(e.lineId);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成连续生产 Excel 失败", e);
        }
    }

    private byte[] buildDemandWorkbook(boolean withData) {
        String sheetName = "订单优先级";
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeReadme(wb, "需求规则：按销售订单号与行号更新优先级、加急等级、排程锁定与交期。");
            Sheet sheet = wb.createSheet(sheetName);
            writeDemandHeader(sheet);
            if (withData) {
                int rowIdx = 1;
                for (SalesOrderDto dto : masterDataResource.listSalesOrders()) {
                    Row row = sheet.createRow(rowIdx++);
                    writeDemandRow(row, dto);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成订单优先级 Excel 失败", e);
        }
    }

    private MasterDataImportResult importDemandWorkbook(InputStream input) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook wb = WorkbookFactory.create(input)) {
            Sheet sheet = wb.getSheet("订单优先级");
            if (sheet == null && wb.getNumberOfSheets() > 0) {
                sheet = wb.getSheetAt(0);
            }
            if (sheet == null) {
                errors.add("未找到「订单优先级」工作表");
                return new MasterDataImportResult(0, errors);
            }
            Map<String, Integer> header = readDemandHeader(sheet.getRow(0));
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isDemandRowEmpty(row, header)) {
                    continue;
                }
                try {
                    imported += importDemandRow(row, header);
                } catch (Exception ex) {
                    errors.add("第" + (r + 1) + "行: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            errors.add("读取 Excel 失败: " + e.getMessage());
        }
        if (!errors.isEmpty()) {
            return new MasterDataImportResult(imported, errors);
        }
        MasterDataValidationDtos.ValidationReport report = validationService.validateAll();
        for (ValidationIssue issue : report.errors()) {
            errors.add("[规则 " + issue.ruleId() + "] " + issue.entityType() + " " + issue.entityKey() + ": "
                    + issue.reason());
        }
        if (!errors.isEmpty()) {
            MasterDataImportResult result = new MasterDataImportResult(imported, errors);
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(result)
                            .build());
        }
        return new MasterDataImportResult(imported, List.of());
    }

    private int importDemandRow(Row row, Map<String, Integer> header) {
        Long id = parseLongOrNull(cell(row, header, "id", "系统ID(留空=按订单号更新)"));
        String salesOrderNo = requiredCell(row, header, "salesOrderNo", "销售订单号");
        int lineNo = parseInt(requiredCell(row, header, "salesOrderLineNo", "行号"), "行号");
        SalesOrderLineEntity e = id != null
                ? SalesOrderLineEntity.findById(id)
                : SalesOrderLineEntity.findByKey(salesOrderNo, lineNo);
        if (e == null) {
            throw new IllegalArgumentException("订单不存在: " + salesOrderNo + "-" + lineNo);
        }
        if (id != null && !salesOrderNo.equals(e.salesOrderNo)) {
            throw new IllegalArgumentException("系统ID 与订单号不匹配");
        }
        e.priority = parseInt(cell(row, header, "priority", "优先级"), "优先级");
        e.expediteLevel = parseInt(cell(row, header, "expediteLevel", "加急等级"), "加急等级");
        e.scheduleLockFlag = parseBoolean(cell(row, header, "scheduleLockFlag", "排程锁定(是/否)"), false);
        String dueRaw = cell(row, header, "dueDate", "交期(yyyy-MM-dd)");
        if (!dueRaw.isBlank()) {
            e.dueDate = parseDate(dueRaw, "交期");
        }
        return 1;
    }

    private void writeReadme(Workbook wb, String hint) {
        Sheet readme = wb.createSheet("说明");
        readme.createRow(0).createCell(0).setCellValue("业务规则 Excel：第一行为表头，从第二行起填写数据。");
        readme.createRow(1).createCell(0).setCellValue(hint);
    }

    private void writeStringHeader(Sheet sheet, List<String> headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            header.createCell(i).setCellValue(headers.get(i));
        }
    }

    private void writeDemandHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < DEMAND_COLUMNS.size(); i++) {
            header.createCell(i).setCellValue(DEMAND_COLUMNS.get(i).header());
        }
    }

    private void writeDemandRow(Row row, SalesOrderDto dto) {
        row.createCell(0).setCellValue(dto.id() != null ? String.valueOf(dto.id()) : "");
        row.createCell(1).setCellValue(dto.salesOrderNo());
        row.createCell(2).setCellValue(dto.salesOrderLineNo());
        row.createCell(3).setCellValue(dto.priority());
        row.createCell(4).setCellValue(dto.expediteLevel());
        row.createCell(5).setCellValue(dto.scheduleLockFlag() ? "是" : "否");
        if (dto.dueDate() != null) {
            row.createCell(6).setCellValue(dto.dueDate().format(DATE_FMT));
        }
    }

    private Map<String, Integer> readDemandHeader(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) {
            return map;
        }
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String h = cellString(headerRow.getCell(c));
            if (!h.isBlank()) {
                map.put(h.trim(), c);
                for (DemandColumn col : DEMAND_COLUMNS) {
                    if (col.header().equals(h.trim())) {
                        map.put(col.field(), c);
                    }
                }
            }
        }
        return map;
    }

    private boolean isDemandRowEmpty(Row row, Map<String, Integer> header) {
        String orderNo = cell(row, header, "salesOrderNo", "销售订单号");
        String lineNo = cell(row, header, "salesOrderLineNo", "行号");
        return orderNo.isBlank() && lineNo.isBlank();
    }

    private static String cell(Row row, Map<String, Integer> header, String field, String headerLabel) {
        Integer idx = header.get(field);
        if (idx == null) {
            idx = header.get(headerLabel);
        }
        if (idx == null || idx < 0) {
            return "";
        }
        return cellString(row.getCell(idx));
    }

    private static String requiredCell(Row row, Map<String, Integer> header, String field, String headerLabel) {
        String v = cell(row, header, field, headerLabel);
        if (v.isBlank()) {
            throw new IllegalArgumentException("缺少必填列: " + headerLabel);
        }
        return v;
    }

    private static String cellString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return CELL_FORMAT.formatCellValue(cell).trim();
    }

    private static Long parseLongOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return Long.parseLong(v.trim());
    }

    private static int parseInt(String v, String label) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return (int) Double.parseDouble(v.trim());
    }

    private static boolean parseBoolean(String v, boolean defaultValue) {
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("是") || s.equals("true") || s.equals("1") || s.equals("y") || s.equals("yes");
    }

    private static LocalDate parseDate(String v, String label) {
        try {
            return LocalDate.parse(v.trim(), DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd");
        }
    }

    private record DemandColumn(String field, String header) {
    }
}
