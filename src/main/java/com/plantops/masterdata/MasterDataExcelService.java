package com.plantops.masterdata;

import com.plantops.api.MasterDataResource;
import com.plantops.api.dto.masterdata.MasterDataDtos.BomDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MaterialDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ProductResourceDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ProductionLineDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ResourceCalendarDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ResourceDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ShiftHeadcountDto;
import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.ValidationIssue;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos;
import com.plantops.masterdata.MasterDataExcelSheet.ColumnDef;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ShiftHeadcountEntity;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@ApplicationScoped
public class MasterDataExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DataFormatter CELL_FORMAT = new DataFormatter();

    @Inject
    MasterDataResource masterDataResource;

    @Inject
    MasterDataValidationService validationService;

    @Inject
    MasterDataExcelColumnLayout columnLayout;

    public byte[] buildTemplate() {
        return buildWorkbook(false);
    }

    public byte[] buildExport() {
        return buildWorkbook(true);
    }

    public byte[] buildSingleSheetWorkbook(MasterDataExcelSheet def, boolean withData) {
        List<ColumnDef> columns = columnLayout.resolve(def);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet readme = wb.createSheet("说明");
            readme.createRow(0).createCell(0).setCellValue("业务规则 Excel：第一行为表头，从第二行起填写数据。");
            readme.createRow(1).createCell(0).setCellValue("工作表「" + def.sheetName + "」与系统主数据字段一致。");
            if (def.extensionEntityType() != null) {
                readme.createRow(2).createCell(0)
                        .setCellValue("Custom 列来自当前 workspace 字段目录，随目录变更而增减。");
            }
            Sheet sheet = wb.createSheet(def.sheetName);
            writeHeader(sheet, columns);
            if (withData) {
                writeDataRows(sheet, def, columns);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 3600)
    public MasterDataImportResult importSingleSheet(MasterDataExcelSheet def, InputStream input, boolean replace) {
        List<ColumnDef> columns = columnLayout.resolve(def);
        if (replace) {
            switch (def) {
                case BOM -> BomComponentEntity.delete("workspaceId", BomComponentEntity.ws());
                case SHIFT_HEADCOUNT -> ShiftHeadcountEntity.delete("workspaceId", ShiftHeadcountEntity.ws());
                default -> throw new IllegalArgumentException("不支持整表替换导入: " + def.sheetName);
            }
        }
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook wb = WorkbookFactory.create(input)) {
            Sheet sheet = wb.getSheet(def.sheetName);
            if (sheet == null && wb.getNumberOfSheets() > 0) {
                sheet = wb.getSheetAt(0);
            }
            if (sheet == null) {
                errors.add("未找到工作表「" + def.sheetName + "」");
                return new MasterDataImportResult(0, errors);
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row, columns)) {
                    continue;
                }
                try {
                    importRow(def, columns, row);
                    imported++;
                } catch (Exception ex) {
                    errors.add(def.sheetName + " 第" + (r + 1) + "行: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 Excel 失败", e);
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

    private byte[] buildWorkbook(boolean withData) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet readme = wb.createSheet("说明");
            readme.createRow(0).createCell(0).setCellValue("主数据 Excel 模板：每个工作表第一行为表头，从第二行起填写数据。");
            readme.createRow(1).createCell(0).setCellValue("系统ID 留空表示新增；填写已有 ID 表示更新。日期格式 yyyy-MM-dd，布尔填 是/否 或 true/false。");
            readme.createRow(2).createCell(0)
                    .setCellValue("「物料」「产品工艺」的 Custom 列由字段目录动态生成；导入时仅更新 Excel 中出现的 Custom 列，其余扩展属性保留。");

            for (MasterDataExcelSheet def : MasterDataExcelSheet.values()) {
                List<ColumnDef> columns = columnLayout.resolve(def);
                Sheet sheet = wb.createSheet(def.sheetName);
                writeHeader(sheet, columns);
                if (withData) {
                    writeDataRows(sheet, def, columns);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    private void writeHeader(Sheet sheet, List<ColumnDef> columns) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            header.createCell(i).setCellValue(columns.get(i).header());
        }
    }

    private void writeDataRows(Sheet sheet, MasterDataExcelSheet def, List<ColumnDef> columns) {
        int rowIdx = 1;
        switch (def) {
            case MATERIALS -> {
                for (MaterialDto dto : masterDataResource.listMaterials()) {
                    Map<String, Object> values = row(
                            "id", dto.id(),
                            "siteCode", dto.siteCode(),
                            "materialCode", dto.materialCode(),
                            "materialName", dto.materialName(),
                            "uomCode", dto.uomCode(),
                            "materialType", dto.materialType());
                    putExtensionValues(values, columns, dto.extensions());
                    writeRow(sheet, rowIdx++, columns, values);
                }
            }
            case BOM -> {
                for (BomDto dto : masterDataResource.listBoms()) {
                    writeRow(sheet, rowIdx++, columns, row(
                            "id", dto.id(),
                            "finishedProductCode", dto.finishedProductCode(),
                            "bomId", dto.bomId(),
                            "bomVersion", dto.bomVersion(),
                            "parentProductCode", dto.parentProductCode(),
                            "componentProductCode", dto.componentProductCode(),
                            "componentQty", dto.componentQty(),
                            "isCriticalComponent", dto.isCriticalComponent(),
                            "bomEffectiveFrom", dto.bomEffectiveFrom(),
                            "bomEffectiveTo", dto.bomEffectiveTo(),
                            "componentEffectiveFrom", dto.componentEffectiveFrom(),
                            "componentEffectiveTo", dto.componentEffectiveTo(),
                            "scrapRate", dto.scrapRate(),
                            "lotSize", dto.lotSize(),
                            "lotSizeMultiple", dto.lotSizeMultiple()));
                }
            }
            case RESOURCES -> {
                for (ResourceDto dto : masterDataResource.listResources()) {
                    writeRow(sheet, rowIdx++, columns, row(
                            "id", dto.id(),
                            "resourceId", dto.resourceId(),
                            "resourceGroup", dto.resourceGroup(),
                            "areaId", dto.areaId(),
                            "bottleneck", dto.bottleneck(),
                            "runRatePerHour", dto.runRatePerHour()));
                }
            }
            case PRODUCT_RESOURCES -> {
                for (ProductResourceDto dto : masterDataResource.listProductResources()) {
                    Map<String, Object> values = row(
                            "id", dto.id(),
                            "productCode", dto.productCode(),
                            "sequenceNo", dto.sequenceNo(),
                            "resourcePriority", dto.resourcePriority(),
                            "operationName", dto.operationName(),
                            "resourceId", dto.resourceId(),
                            "setupTimeMinutes", dto.setupTimeMinutes(),
                            "processTimeSeconds", dto.processTimeSeconds());
                    putExtensionValues(values, columns, dto.extensions());
                    writeRow(sheet, rowIdx++, columns, values);
                }
            }
            case LINES -> {
                for (ProductionLineDto dto : masterDataResource.listLines()) {
                    writeRow(sheet, rowIdx++, columns, row(
                            "id", dto.id(),
                            "lineId", dto.lineId(),
                            "areaId", dto.areaId(),
                            "resourceId", dto.resourceId(),
                            "lineMinHeadcount", dto.lineMinHeadcount(),
                            "lineCapacityPerShift", dto.lineCapacityPerShift()));
                }
            }
            case CALENDAR -> {
                for (ResourceCalendarDto dto : masterDataResource.listCalendar()) {
                    writeRow(sheet, rowIdx++, columns, row(
                            "id", dto.id(),
                            "resourceId", dto.resourceId(),
                            "calendarDate", dto.calendarDate(),
                            "shiftId", dto.shiftId(),
                            "availableCapacityMinutes", dto.availableCapacityMinutes(),
                            "unavailableCapacityMinutes", dto.unavailableCapacityMinutes()));
                }
            }
            case SHIFT_HEADCOUNT -> {
                for (ShiftHeadcountDto dto : masterDataResource.listShiftHeadcount()) {
                    writeRow(sheet, rowIdx++, columns, row(
                            "id", dto.id(),
                            "areaId", dto.areaId(),
                            "calendarDate", dto.calendarDate(),
                            "shiftId", dto.shiftId(),
                            "availableHeadcount", dto.availableHeadcount()));
                }
            }
        }
    }

    /** Map.of 不允许 null 值；导出时部分字段可为空 */
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private void writeRow(Sheet sheet, int rowIdx, List<ColumnDef> columns, Map<String, Object> values) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < columns.size(); c++) {
            String field = columns.get(c).field();
            Object v = values.get(field);
            Cell cell = row.createCell(c);
            if (v == null) {
                cell.setBlank();
            } else if (v instanceof Boolean b) {
                cell.setCellValue(b ? "是" : "否");
            } else if (v instanceof LocalDate d) {
                cell.setCellValue(d.format(DATE_FMT));
            } else if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(String.valueOf(v));
            }
        }
    }

    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 3600)
    public MasterDataImportResult importWorkbook(InputStream input) {
        return importWorkbook(input, false);
    }

    @Transactional
    @io.quarkus.narayana.jta.runtime.TransactionConfiguration(timeout = 3600)
    public MasterDataImportResult importWorkbook(InputStream input, boolean replaceExisting) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook wb = WorkbookFactory.create(input)) {
            // 支持外部模板：三个独立 Excel（物料主数据 / 物料BOM / 工艺BOM）
            MasterDataImportResult external = tryImportExternalTemplates(wb, replaceExisting);
            if (external != null) {
                return external;
            }
            for (MasterDataExcelSheet def : MasterDataExcelSheet.values()) {
                Sheet sheet = wb.getSheet(def.sheetName);
                if (sheet == null) {
                    continue;
                }
                List<ColumnDef> columns = columnLayout.resolve(def);
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row, columns)) {
                        continue;
                    }
                    try {
                        importRow(def, columns, row);
                        imported++;
                    } catch (Exception ex) {
                        errors.add(def.sheetName + " 第" + (r + 1) + "行: " + ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 Excel 失败", e);
        }
        // 全局一致性校验（失败则回滚，不落库）
        MasterDataValidationDtos.ValidationReport report = validationService.validateAll();
        for (ValidationIssue issue : report.errors()) {
            errors.add("[规则 " + issue.ruleId() + "] " + issue.entityType() + " " + issue.entityKey() + ": " + issue.reason());
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

    /**
     * 识别并导入外部模板（单文件单 sheet，表头为中文字段）。
     * 若识别成功返回结果；否则返回 null 走系统内置模板逻辑。
     */
    private MasterDataImportResult tryImportExternalTemplates(Workbook wb, boolean replaceExisting) {
        if (wb.getNumberOfSheets() == 0) {
            return null;
        }
        Sheet sheet = wb.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) {
            return null;
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < Math.min(60, header.getLastCellNum()); c++) {
            headers.add(cellString(header.getCell(c)));
        }
        // 物料主数据模板
        if (headers.contains("物料类型") && headers.contains("产品代码") && headers.contains("主计量单位代码")) {
            if (replaceExisting) {
                MaterialEntity.delete("workspaceId", MaterialEntity.ws());
            }
            return importExternalMaterialMaster(sheet);
        }
        // 物料 BOM 模板
        if (headers.contains("成品料号") && headers.contains("组件代码") && headers.contains("组件数量")) {
            if (replaceExisting) {
                BomComponentEntity.delete("workspaceId", BomComponentEntity.ws());
            }
            return importExternalMaterialBom(sheet);
        }
        // 工艺 BOM 模板
        if (headers.contains("工序代码") && headers.contains("工序编号") && headers.contains("设备组")) {
            if (replaceExisting) {
                ProductResourceEntity.delete("workspaceId", ProductResourceEntity.ws());
                ProductionResourceEntity.delete("workspaceId", ProductionResourceEntity.ws());
            }
            return importExternalRoutingBom(sheet);
        }
        return null;
    }

    private MasterDataImportResult importExternalMaterialMaster(Sheet sheet) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        Map<String, Integer> idx = headerIndex(sheet.getRow(0));
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String materialCode = cellString(row.getCell(idx.getOrDefault("产品代码", -1)));
            if (materialCode.isBlank()) continue;
            try {
                MaterialEntity existing = MaterialEntity.findByCode(materialCode);
                Map<String, Object> extensions = existing != null
                        ? MasterDataExtensionService.readMaterialExtensions(existing)
                        : Map.of();
                masterDataResource.upsertMaterial(new MaterialDto(
                        existing != null ? existing.id : null,
                        cellString(row.getCell(idx.getOrDefault("基地代码", -1))),
                        materialCode,
                        cellString(row.getCell(idx.getOrDefault("产品名称", -1))),
                        cellString(row.getCell(idx.getOrDefault("主计量单位代码", -1))),
                        cellString(row.getCell(idx.getOrDefault("物料类型", -1))),
                        extensions.isEmpty() ? null : extensions));
                imported++;
            } catch (Exception ex) {
                errors.add("物料主数据 第" + (r + 1) + "行: " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            MasterDataImportResult result = new MasterDataImportResult(imported, errors);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(result).build());
        }
        return new MasterDataImportResult(imported, List.of());
    }

    private MasterDataImportResult importExternalMaterialBom(Sheet sheet) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        Map<String, Integer> idx = headerIndex(sheet.getRow(0));
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String finished = cellString(row.getCell(idx.getOrDefault("成品料号", -1)));
            String parent = cellString(row.getCell(idx.getOrDefault("产品代码", -1)));
            String component = cellString(row.getCell(idx.getOrDefault("组件代码", -1)));
            if (finished.isBlank() && parent.isBlank() && component.isBlank()) continue;
            if (parent.isBlank()) {
                errors.add("物料BOM 第" + (r + 1) + "行: 缺少产品代码(父项)");
                continue;
            }
            if (component.isBlank()) {
                errors.add("物料BOM 第" + (r + 1) + "行: 缺少组件代码");
                continue;
            }
            try {
                boolean ignoreKitting = parseBoolean(cellString(row.getCell(idx.getOrDefault("不计算齐套率", -1))), false);
                String finishedCode = finished.isBlank() ? parent : finished;
                String bomVersion = cellString(row.getCell(idx.getOrDefault("BOM版本", -1)));
                if (bomVersion.isBlank()) {
                    bomVersion = "V1";
                }
                BomComponentEntity e = new BomComponentEntity();
                e.finishedProductCode = emptyToNull(finished) != null ? finished : finishedCode;
                e.bomId = finishedCode;
                e.bomVersion = bomVersion;
                e.parentProductCode = parent;
                e.componentProductCode = component;
                e.componentQty = parseDecimal(cellString(row.getCell(idx.getOrDefault("组件数量", -1))), BigDecimal.ONE);
                e.isCriticalComponent = !ignoreKitting;
                e.bomEffectiveFrom = parseDateOrNull(cellString(row.getCell(idx.getOrDefault("BOM生效时间", -1))));
                e.bomEffectiveTo = parseDateOrNull(cellString(row.getCell(idx.getOrDefault("BOM失效时间", -1))));
                e.componentEffectiveFrom = parseDateOrNull(cellString(row.getCell(idx.getOrDefault("组件生效时间", -1))));
                e.componentEffectiveTo = parseDateOrNull(cellString(row.getCell(idx.getOrDefault("组件失效时间", -1))));
                e.scrapRate = parseDecimalOrNull(cellString(row.getCell(idx.getOrDefault("组件损耗率", -1))));
                e.lotSize = parseDecimalOrNull(cellString(row.getCell(idx.getOrDefault("批量", -1))));
                e.lotSizeMultiple = parseDecimalOrNull(cellString(row.getCell(idx.getOrDefault("批量倍数", -1))));
                e.ensureWorkspace();
                e.persist();
                imported++;
                if (imported % 500 == 0) {
                    BomComponentEntity.flush();
                }
            } catch (Exception ex) {
                errors.add("物料BOM 第" + (r + 1) + "行: " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            MasterDataImportResult result = new MasterDataImportResult(imported, errors);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(result).build());
        }
        return new MasterDataImportResult(imported, List.of());
    }

    private MasterDataImportResult importExternalRoutingBom(Sheet sheet) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        Map<String, Integer> idx = headerIndex(sheet.getRow(0));
        // 新 workspace 下导入：直接落库（避免经过 REST 资源层的重复校验/查询），并缓存资源去重
        Map<String, ProductionResourceEntity> resources = new LinkedHashMap<>();
        java.util.Set<String> routeKeys = new java.util.HashSet<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String material = cellString(row.getCell(idx.getOrDefault("料号", -1)));
            if (material.isBlank()) {
                material = cellString(row.getCell(idx.getOrDefault("成品料号", -1)));
            }
            if (material.isBlank()) continue;
            try {
                String resourceId = cellString(row.getCell(idx.getOrDefault("设备组", -1)));
                if (resourceId.isBlank()) {
                    resourceId = cellString(row.getCell(idx.getOrDefault("工序线体", -1)));
                }
                if (resourceId.isBlank()) {
                    throw new IllegalArgumentException("缺少设备组/工序线体");
                }

                String routeKey = material + "|" + resourceId;
                if (!routeKeys.add(routeKey)) {
                    continue; // 同一物料-资源重复行，直接跳过
                }

                ProductionResourceEntity pr = resources.get(resourceId);
                if (pr == null) {
                    pr = new ProductionResourceEntity();
                    pr.resourceId = resourceId;
                    pr.resourceGroup = emptyToNull(cellString(row.getCell(idx.getOrDefault("设备组", -1))));
                    String area = emptyToNull(cellString(row.getCell(idx.getOrDefault("厂区代码", -1))));
                    pr.areaId = area != null ? area : "A1";
                    pr.bottleneck = false;
                    pr.runRatePerHour = BigDecimal.valueOf(60);
                    pr.ensureWorkspace();
                    pr.persist();
                    resources.put(resourceId, pr);
                }

                ProductResourceEntity route = new ProductResourceEntity();
                route.productCode = material;
                route.resourceId = resourceId;
                route.setupTimeMinutes = 0;
                route.sequenceNo = parseInteger(cellString(row.getCell(idx.getOrDefault("工序编号", -1))));
                route.resourcePriority = ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY;
                route.operationName = ProductResourceOperationNames.normalize(
                        emptyToNull(cellString(row.getCell(idx.getOrDefault("工序名称", -1)))),
                        resourceId,
                        route.sequenceNo);
                route.processTimeSeconds = parseDecimalOrNull(cellString(row.getCell(idx.getOrDefault("制造CT", -1))));
                route.bomLevel = emptyToNull(firstNonBlank(row, idx, "A/B料", "A/B 料", "AB料"));
                route.wireMaterial = emptyToNull(cellString(row.getCell(idx.getOrDefault("线材", -1))));
                route.keyMaterial = emptyToNull(cellString(row.getCell(idx.getOrDefault("关键物料", -1))));
                route.maleFemaleEnd = emptyToNull(cellString(row.getCell(idx.getOrDefault("公母端", -1))));
                route.totalBranch = emptyToNull(cellString(row.getCell(idx.getOrDefault("总成分支", -1))));
                route.standardLabor = parseDecimalOrNull(cellString(row.getCell(idx.getOrDefault("制造人力", -1))));
                MasterDataExtensionService.backfillProductResourceExtensions(route);
                route.ensureWorkspace();
                route.persist();
                imported++;
            } catch (Exception ex) {
                errors.add("工艺BOM 第" + (r + 1) + "行: " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            MasterDataImportResult result = new MasterDataImportResult(imported, errors);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(result).build());
        }
        return new MasterDataImportResult(imported, List.of());
    }

    private static Map<String, Integer> headerIndex(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) {
            return map;
        }
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String h = cellString(headerRow.getCell(c));
            if (!h.isBlank()) {
                map.put(h, c);
            }
        }
        return map;
    }

    private void importRow(MasterDataExcelSheet def, List<ColumnDef> columns, Row row) {
        Map<String, String> cells = readRow(row, columns);
        switch (def) {
            case MATERIALS -> {
                Long id = parseLong(cells.get("id"));
                String materialCode = required(cells, "materialCode");
                MaterialEntity existing = resolveMaterialEntity(id, materialCode);
                Map<String, Object> mergedExtensions = MasterDataExtensionService.mergeExtensionMaps(
                        existing != null ? MasterDataExtensionService.readMaterialExtensions(existing) : Map.of(),
                        parseCustomExtensions(cells, columns));
                masterDataResource.upsertMaterial(new MaterialDto(
                        id != null ? id : (existing != null ? existing.id : null),
                        emptyToNull(cells.get("siteCode")),
                        materialCode,
                        emptyToNull(cells.get("materialName")),
                        emptyToNull(cells.get("uomCode")),
                        emptyToNull(cells.get("materialType")),
                        mergedExtensions.isEmpty() ? null : mergedExtensions));
            }
            case BOM -> masterDataResource.upsertBom(new BomDto(
                    parseLong(cells.get("id")),
                    emptyToNull(cells.get("finishedProductCode")),
                    required(cells, "bomId"),
                    cells.getOrDefault("bomVersion", "V1"),
                    required(cells, "parentProductCode"),
                    required(cells, "componentProductCode"),
                    parseDecimal(cells.get("componentQty"), BigDecimal.ONE),
                    parseBoolean(cells.get("isCriticalComponent"), true),
                    parseDateOrNull(cells.get("bomEffectiveFrom")),
                    parseDateOrNull(cells.get("bomEffectiveTo")),
                    parseDateOrNull(cells.get("componentEffectiveFrom")),
                    parseDateOrNull(cells.get("componentEffectiveTo")),
                    parseDecimalOrNull(cells.get("scrapRate")),
                    parseDecimalOrNull(cells.get("lotSize")),
                    parseDecimalOrNull(cells.get("lotSizeMultiple"))));
            case RESOURCES -> masterDataResource.upsertResource(new ResourceDto(
                    parseLong(cells.get("id")),
                    required(cells, "resourceId"),
                    emptyToNull(cells.get("resourceGroup")),
                    required(cells, "areaId"),
                    parseBoolean(cells.get("bottleneck"), false),
                    parseDecimal(cells.get("runRatePerHour"), BigDecimal.valueOf(60))));
            case PRODUCT_RESOURCES -> {
                Long id = parseLong(cells.get("id"));
                String productCode = required(cells, "productCode");
                String resourceId = required(cells, "resourceId");
                ProductResourceEntity existing = resolveProductResourceEntity(id, productCode, resourceId);
                Map<String, Object> mergedExtensions = MasterDataExtensionService.mergeExtensionMaps(
                        existing != null
                                ? MasterDataExtensionService.readProductResourceExtensions(existing)
                                : Map.of(),
                        parseCustomExtensions(cells, columns));
                masterDataResource.upsertProductResource(new ProductResourceDto(
                        id != null ? id : (existing != null ? existing.id : null),
                        productCode,
                        resourceId,
                        parseInt(cells.get("setupTimeMinutes"), 30),
                        parseInteger(cells.get("sequenceNo")),
                        parseIntegerOrDefault(cells.get("resourcePriority"), ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY),
                        emptyToNull(cells.get("operationName")),
                        parseDecimalOrNull(cells.get("processTimeSeconds")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mergedExtensions.isEmpty() ? null : mergedExtensions));
            }
            case LINES -> masterDataResource.upsertLine(new ProductionLineDto(
                    parseLong(cells.get("id")),
                    required(cells, "lineId"),
                    required(cells, "areaId"),
                    required(cells, "resourceId"),
                    parseInt(cells.get("lineMinHeadcount"), 2),
                    parseInt(cells.get("lineCapacityPerShift"), 480)));
            case CALENDAR -> masterDataResource.upsertCalendar(new ResourceCalendarDto(
                    parseLong(cells.get("id")),
                    required(cells, "resourceId"),
                    required(cells, "shiftId"),
                    parseDate(required(cells, "calendarDate")),
                    parseInt(cells.get("availableCapacityMinutes"), 480),
                    parseInt(cells.get("unavailableCapacityMinutes"), 0)));
            case SHIFT_HEADCOUNT -> masterDataResource.upsertShiftHeadcount(new ShiftHeadcountDto(
                    parseLong(cells.get("id")),
                    required(cells, "areaId"),
                    required(cells, "shiftId"),
                    parseDate(required(cells, "calendarDate")),
                    parseInt(cells.get("availableHeadcount"), 8)));
        }
    }

    private Map<String, String> readRow(Row row, List<ColumnDef> columns) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            map.put(columns.get(i).field(), cellString(row.getCell(i)));
        }
        return map;
    }

    private boolean isRowEmpty(Row row, List<ColumnDef> columns) {
        for (int i = 0; i < columns.size(); i++) {
            String field = columns.get(i).field();
            if ("id".equals(field)) {
                continue;
            }
            if (!cellString(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void putExtensionValues(
            Map<String, Object> values,
            List<ColumnDef> columns,
            Map<String, Object> extensions) {
        if (extensions == null) {
            return;
        }
        for (ColumnDef col : columns) {
            if (col.custom()) {
                values.put(col.field(), extensions.get(col.field()));
            }
        }
    }

    private static Map<String, Object> parseCustomExtensions(Map<String, String> cells, List<ColumnDef> columns) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (ColumnDef col : columns) {
            if (!col.custom()) {
                continue;
            }
            String raw = cells.get(col.field());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            parsed.put(col.field(), MasterDataExtensionService.parseExtensionCell(raw, col.dataType()));
        }
        return parsed;
    }

    private static MaterialEntity resolveMaterialEntity(Long id, String materialCode) {
        if (id != null) {
            MaterialEntity byId = MaterialEntity.findById(id);
            if (byId != null) {
                return byId;
            }
        }
        return MaterialEntity.findByCode(materialCode);
    }

    private static ProductResourceEntity resolveProductResourceEntity(
            Long id,
            String productCode,
            String resourceId) {
        if (id != null) {
            ProductResourceEntity byId = ProductResourceEntity.findById(id);
            if (byId != null) {
                return byId;
            }
        }
        return ProductResourceEntity.findByProductAndResource(productCode, resourceId);
    }

    private static String cellString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return CELL_FORMAT.formatCellValue(cell).trim();
    }

    private static String required(Map<String, String> cells, String key) {
        String v = cells.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("缺少必填列: " + key);
        }
        return v.trim();
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return Long.parseLong(v.trim());
    }

    private static Integer parseInteger(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return (int) Double.parseDouble(v.trim());
    }

    private static Integer parseIntegerOrDefault(String v, int defaultValue) {
        Integer parsed = parseInteger(v);
        return parsed != null ? parsed : defaultValue;
    }

    private static int parseInt(String v, int defaultValue) {
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return (int) Double.parseDouble(v.trim());
    }

    private static BigDecimal parseDecimal(String v, BigDecimal defaultValue) {
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return new BigDecimal(v.trim());
    }

    private static BigDecimal parseDecimalOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return new BigDecimal(v.trim());
    }

    private static boolean parseBoolean(String v, boolean defaultValue) {
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String s = v.trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("是") || s.equals("yes") || s.equals("y");
    }

    private static LocalDate parseDate(String v) {
        String s = v.trim();
        for (DateTimeFormatter fmt : List.of(
                DATE_FMT,
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yy"))) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd: " + v);
    }

    private static LocalDate parseDateOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return parseDate(v);
    }

    private static String firstNonBlank(Row row, Map<String, Integer> idx, String... headerNames) {
        for (String name : headerNames) {
            String v = cellString(row.getCell(idx.getOrDefault(name, -1)));
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

}
