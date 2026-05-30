package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 导入「设备基础信息.xlsx」：设备组 → 生产资源，线体 → 产线 ID。
 */
@ApplicationScoped
public class EquipmentLineExcelImportService {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);
    private static final String DEFAULT_AREA = "A1";
    private static final int DEFAULT_MIN_HEADCOUNT = 2;
    private static final int DEFAULT_CAPACITY = 480;

    @Transactional
    public MasterDataImportResult importWorkbook(InputStream input, boolean replaceExisting) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (replaceExisting) {
                ProductionLineEntity.delete("workspaceId", ProductionLineEntity.ws());
            }
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                imported += importSheet(workbook.getSheetAt(s), errors);
            }
        } catch (Exception ex) {
            errors.add("无法读取 Excel: " + ex.getMessage());
        }
        if (!errors.isEmpty()) {
            return new MasterDataImportResult(imported, errors);
        }
        return new MasterDataImportResult(imported, List.of());
    }

    private int importSheet(Sheet sheet, List<String> errors) {
        if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
            return 0;
        }
        Map<String, Integer> header = readHeader(sheet.getRow(0));
        int groupCol = column(header, "设备组", "resourceId", "resourceGroup");
        int lineCol = column(header, "线体", "lineId", "产线", "机台");
        if (groupCol < 0 || lineCol < 0) {
            errors.add("Sheet「" + sheet.getSheetName() + "」缺少必需列（设备组 / 线体）");
            return 0;
        }
        int imported = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String resourceId = cell(row, groupCol);
            String lineId = cell(row, lineCol);
            if (resourceId.isBlank() && lineId.isBlank()) {
                continue;
            }
            try {
                if (resourceId.isBlank() || lineId.isBlank()) {
                    throw new IllegalArgumentException("设备组与线体均不能为空");
                }
                ensureResource(resourceId);
                upsertLine(lineId, resourceId);
                imported++;
            } catch (Exception ex) {
                errors.add("第" + (r + 1) + "行: " + ex.getMessage());
            }
        }
        return imported;
    }

    private static void ensureResource(String resourceId) {
        ProductionResourceEntity existing = ProductionResourceEntity.findByResourceId(resourceId);
        if (existing != null) {
            return;
        }
        ProductionResourceEntity resource = new ProductionResourceEntity();
        resource.resourceId = resourceId;
        resource.resourceGroup = resourceId;
        resource.areaId = DEFAULT_AREA;
        resource.bottleneck = false;
        resource.runRatePerHour = BigDecimal.valueOf(60);
        resource.ensureWorkspace();
        resource.persist();
    }

    private static void upsertLine(String lineId, String resourceId) {
        ProductionLineEntity line = ProductionLineEntity.findByLineId(lineId);
        if (line == null) {
            line = new ProductionLineEntity();
            line.lineId = lineId;
            line.ensureWorkspace();
        }
        line.resourceId = resourceId;
        line.areaId = DEFAULT_AREA;
        if (line.lineMinHeadcount <= 0) {
            line.lineMinHeadcount = DEFAULT_MIN_HEADCOUNT;
        }
        if (line.lineCapacityPerShift <= 0) {
            line.lineCapacityPerShift = DEFAULT_CAPACITY;
        }
        if (line.id == null) {
            line.persist();
        }
    }

    private static Map<String, Integer> readHeader(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) {
            return map;
        }
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String h = cell(headerRow, c);
            if (!h.isBlank()) {
                map.put(h.trim(), c);
            }
        }
        return map;
    }

    private static int column(Map<String, Integer> header, String... names) {
        for (String name : names) {
            Integer idx = header.get(name);
            if (idx != null) {
                return idx;
            }
        }
        return -1;
    }

    private static String cell(Row row, int col) {
        if (row == null || col < 0) {
            return "";
        }
        return FORMATTER.formatCellValue(row.getCell(col)).trim();
    }
}
