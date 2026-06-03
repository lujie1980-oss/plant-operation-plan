package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataImportResult;
import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;
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
 * 导入工序流转时间规则 Excel：产品 / 前工序 / 后工序 / 流转时间 / 最小流转时间。
 */
@ApplicationScoped
public class OperationTransferTimeExcelImportService {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    @Transactional
    public MasterDataImportResult importWorkbook(InputStream input, boolean replaceExisting) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (replaceExisting) {
                OperationTransferTimeRuleEntity.delete("workspaceId", OperationTransferTimeRuleEntity.ws());
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
        Map<String, Integer> header = readHeader(sheet.getRow(0), sheet.getRow(1));
        int productCol = column(header, "产品", "productCode", "productId");
        int fromCol = column(header, "前工序", "fromOperationName", "previousOperationId");
        int toCol = column(header, "后工序", "toOperationName", "nextOperationId");
        int transferCol = column(header, "流转时间", "transferMinutes", "transferDuration");
        int minCol = column(header, "最小流转时间", "minTransferMinutes", "minTransferDuration");
        if (productCol < 0 || fromCol < 0 || toCol < 0 || transferCol < 0 || minCol < 0) {
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
            String productCode = cell(row, productCol);
            String fromOp = cell(row, fromCol);
            String toOp = cell(row, toCol);
            if (productCode.isBlank() && fromOp.isBlank() && toOp.isBlank()) {
                continue;
            }
            try {
                if (productCode.isBlank() || fromOp.isBlank() || toOp.isBlank()) {
                    throw new IllegalArgumentException("产品、前工序、后工序均不能为空");
                }
                int transferMinutes = parseDurationMinutes(cell(row, transferCol), "流转时间");
                int minTransferMinutes = parseDurationMinutes(cell(row, minCol), "最小流转时间");
                if (minTransferMinutes > transferMinutes) {
                    throw new IllegalArgumentException("最小流转时间不能大于流转时间");
                }
                OperationTransferTimeRuleEntity e = OperationTransferTimeRuleEntity.findEntry(
                        productCode.trim(), fromOp.trim(), toOp.trim());
                if (e == null) {
                    e = new OperationTransferTimeRuleEntity();
                    e.ensureWorkspace();
                }
                e.productCode = productCode.trim();
                e.fromOperationName = fromOp.trim();
                e.toOperationName = toOp.trim();
                e.transferMinutes = transferMinutes;
                e.minTransferMinutes = minTransferMinutes;
                e.maxTransferMinutes = transferMinutes;
                e.linkMode = "STANDARD";
                e.delayStartMinutes = 0;
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

    private static int parseDurationMinutes(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return ChangeoverExcelImportService.parseDurationMinutes(raw);
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
        return joined.contains("product") || joined.contains("Operation");
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
