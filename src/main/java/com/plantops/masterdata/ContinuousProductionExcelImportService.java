package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.persistence.entity.ContinuousProductionRuleEntity;
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
 * 导入连续生产料号清单：半品第一头 PN / 半品第二头 PN / 成品 / 机台。
 */
@ApplicationScoped
public class ContinuousProductionExcelImportService {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    @Transactional
    public MasterDataImportResult importWorkbook(InputStream input, boolean replaceExisting) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (replaceExisting) {
                ContinuousProductionRuleEntity.delete("workspaceId", ContinuousProductionRuleEntity.ws());
            }
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if ("说明".equals(sheet.getSheetName())) {
                    continue;
                }
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
        Map<String, Integer> header = readHeader(sheet.getRow(0));
        int firstCol = column(header, "半品第一头PN", "firstProductCode", "第一头");
        int secondCol = column(header, "半品第二头PN", "secondProductCode", "第二头");
        int finishedCol = column(header, "成品", "finishedProductCode", "productCode");
        int lineCol = column(header, "机台", "lineId", "产线", "线体");
        if (firstCol < 0 || secondCol < 0 || finishedCol < 0 || lineCol < 0) {
            errors.add("Sheet「" + sheet.getSheetName() + "」缺少必需列（半品第一头PN / 半品第二头PN / 成品 / 机台）");
            return 0;
        }
        int imported = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String first = cell(row, firstCol);
            String second = cell(row, secondCol);
            String finished = cell(row, finishedCol);
            String lineId = cell(row, lineCol);
            if (first.isBlank() && second.isBlank() && finished.isBlank() && lineId.isBlank()) {
                continue;
            }
            try {
                if (lineId.isBlank()) {
                    throw new IllegalArgumentException("机台不能为空");
                }
                if (first.isBlank() && second.isBlank() && finished.isBlank()) {
                    throw new IllegalArgumentException("至少填写一个料号（第一头/第二头/成品）");
                }
                String firstCode = ContinuousProductionRuleEntity.normalizeCode(first);
                String secondCode = ContinuousProductionRuleEntity.normalizeCode(second);
                String finishedCode = ContinuousProductionRuleEntity.normalizeCode(finished);
                ContinuousProductionRuleEntity e = ContinuousProductionRuleEntity.findEntry(
                        lineId.trim(), firstCode, secondCode, finishedCode);
                if (e == null) {
                    e = new ContinuousProductionRuleEntity();
                    e.ensureWorkspace();
                }
                e.lineId = lineId.trim();
                e.firstProductCode = firstCode;
                e.secondProductCode = secondCode;
                e.finishedProductCode = finishedCode;
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
