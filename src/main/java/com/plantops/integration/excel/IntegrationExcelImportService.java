package com.plantops.integration.excel;

import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.StockingPointRow;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Excel 解析（external_stocking_point 模板 · API-INT-07）。 */
@ApplicationScoped
public class IntegrationExcelImportService {

    public RoutingBundleImport parseStockingPoints(InputStream inputStream) throws Exception {
        List<StockingPointRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet("stocking_point");
            if (sheet == null && workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 无可用工作表");
            }
            int startRow = 0;
            Row header = sheet.getRow(0);
            if (header != null) {
                String first = formatter.formatCellValue(header.getCell(0));
                if (first != null && (first.equalsIgnoreCase("code") || first.contains("库存点"))) {
                    startRow = 1;
                }
            }
            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String code = formatter.formatCellValue(row.getCell(0)).trim();
                if (code.isBlank()) {
                    continue;
                }
                String name = formatter.formatCellValue(row.getCell(1)).trim();
                String site = formatter.formatCellValue(row.getCell(2)).trim();
                rows.add(new StockingPointRow(code, name.isBlank() ? code : name, site.isBlank() ? "SITE-1" : site));
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("未解析到库存点行（列：code, name, site_code）");
        }
        return new RoutingBundleImport(
                "EXCEL_IMPORT", rows, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
