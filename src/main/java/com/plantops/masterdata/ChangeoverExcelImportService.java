package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.scenario.ChangeoverAttributeKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 导入「换型时间.xlsx」格式：工序 / 属性 / 前任务属性值 / 后任务属性值 / 换型时长。
 */
@ApplicationScoped
public class ChangeoverExcelImportService {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    @Transactional
    public MasterDataImportResult importWorkbook(InputStream input, boolean replaceExisting) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (replaceExisting) {
                ChangeoverMatrixEntity.delete("workspaceId", ChangeoverMatrixEntity.ws());
            }
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                imported += importSheet(sheet, errors);
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
        Map<String, Integer> header = readHeader(sheet.getRow(0), sheet.getRow(1));
        int opCol = column(header, "工序", "operationId", "operationName");
        int attrCol = column(header, "属性", "attribute");
        int fromCol = column(header, "前任务属性值", "previousProductId", "fromAttributeValue");
        int toCol = column(header, "后任务属性值", "nextProductId", "toAttributeValue");
        int durCol = column(header, "换型时长", "prefixDuration", "setupMinutes");
        if (opCol < 0 || attrCol < 0 || fromCol < 0 || toCol < 0 || durCol < 0) {
            errors.add("Sheet「" + sheet.getSheetName() + "」缺少必需列");
            return 0;
        }
        int imported = 0;
        int startRow = headerRowIsEnglish(sheet.getRow(1)) ? 2 : 1;
        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String operationName = cell(row, opCol);
            String attribute = cell(row, attrCol);
            if (operationName.isBlank() && attribute.isBlank()) {
                continue;
            }
            try {
                String attributeKey = ChangeoverAttributeKey.normalizeCode(required(operationName, attribute, row, r));
                String fromValue = ChangeoverAttributeKey.normalizeValue(cell(row, fromCol));
                String toValue = ChangeoverAttributeKey.normalizeValue(cell(row, toCol));
                int minutes = parseDurationMinutes(cell(row, durCol));
                ChangeoverMatrixEntity e = ChangeoverMatrixEntity.findEntry(
                        operationName.trim(), attributeKey, fromValue, toValue);
                if (e == null) {
                    e = new ChangeoverMatrixEntity();
                    e.ensureWorkspace();
                }
                e.operationName = operationName.trim();
                e.attributeKey = attributeKey;
                e.fromAttributeValue = fromValue;
                e.toAttributeValue = toValue;
                e.setupMinutes = minutes;
                if (e.id == null) {
                    e.persist();
                }
                imported++;
            } catch (Exception ex) {
                errors.add("第" + (r + 1) + "行: " + ex.getMessage());
            }
        }
        return imported;
    }

    private static String required(String operationName, String attribute, Row row, int rowIndex) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("工序不能为空");
        }
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("属性不能为空");
        }
        return attribute;
    }

    private static Map<String, Integer> readHeader(Row chinese, Row english) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Row primary = chinese != null ? chinese : english;
        if (primary == null) {
            return map;
        }
        for (int c = 0; c < primary.getLastCellNum(); c++) {
            String h1 = chinese != null ? cell(chinese, c) : "";
            String h2 = english != null ? cell(english, c) : "";
            if (!h1.isBlank()) {
                map.put(h1.trim(), c);
            }
            if (!h2.isBlank()) {
                map.put(h2.trim(), c);
            }
        }
        return map;
    }

    private static boolean headerRowIsEnglish(Row row) {
        if (row == null) {
            return false;
        }
        String joined = cell(row, 0) + cell(row, 1);
        return joined.contains("operation") || joined.contains("attribute");
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

    public static int parseDurationMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("换型时长不能为空");
        }
        String text = raw.trim();
        if (text.contains(":")) {
            String[] parts = text.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException("换型时长格式无效: " + raw);
            }
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            int total = hours * 60 + minutes + (seconds > 0 ? 1 : 0);
            return Math.max(1, total);
        }
        return Math.max(1, (int) Math.round(Double.parseDouble(text)));
    }

    public static String formatDurationMinutes(int minutes) {
        int safe = Math.max(0, minutes);
        return String.format(Locale.ROOT, "%02d:%02d:00", safe / 60, safe % 60);
    }
}
