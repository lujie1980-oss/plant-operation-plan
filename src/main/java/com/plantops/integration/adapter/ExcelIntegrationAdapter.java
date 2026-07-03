package com.plantops.integration.adapter;

import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;
import com.plantops.api.dto.integration.IntegrationDtos.RoutingBundleImport;
import com.plantops.api.dto.integration.IntegrationDtos.StockingPointRow;
import com.plantops.integration.IntegrationAdapterPort;
import com.plantops.integration.excel.IntegrationExcelImportService;
import com.plantops.masterdata.external.MasterDataExternalImportService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.List;

/** ADP-EXCEL：Excel → external_*（API-INT-07）。 */
@ApplicationScoped
public class ExcelIntegrationAdapter implements IntegrationAdapterPort {

    @Inject
    IntegrationExcelImportService excelImportService;

    @Inject
    MasterDataExternalImportService masterDataImportService;

    private InputStream pendingUpload;
    private boolean validateOnly;

    public void setUpload(InputStream stream, boolean validateOnly) {
        this.pendingUpload = stream;
        this.validateOnly = validateOnly;
    }

    @Override
    public String adapterId() {
        return "ADP-EXCEL";
    }

    @Override
    public String sourceSystemCode() {
        return "EXCEL_IMPORT";
    }

    @Override
    public IntegrationAdapterRunResultDto run(boolean validateOnlyFlag) {
        if (pendingUpload == null) {
            return new IntegrationAdapterRunResultDto(null, "FAILED", "未提供 Excel 文件");
        }
        try (InputStream in = pendingUpload) {
            RoutingBundleImport bundle = excelImportService.parseStockingPoints(in);
            if (validateOnly || validateOnlyFlag) {
                return new IntegrationAdapterRunResultDto(
                        null, "SUCCESS", "校验通过：" + bundle.stockingPoints().size() + " 行库存点");
            }
            ImportBatchResult result = masterDataImportService.importRoutingBundle(bundle);
            return new IntegrationAdapterRunResultDto(
                    result.importBatchId(), "SUCCESS", "导入 " + result.rowCount() + " 行");
        } catch (Exception e) {
            return new IntegrationAdapterRunResultDto(null, "FAILED", e.getMessage());
        } finally {
            pendingUpload = null;
            validateOnly = false;
        }
    }

    public IntegrationAdapterRunResultDto importStockingPoints(List<StockingPointRow> rows, boolean validateOnly) {
        RoutingBundleImport bundle = new RoutingBundleImport(
                sourceSystemCode(),
                rows,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        if (validateOnly) {
            return new IntegrationAdapterRunResultDto(
                    null, "SUCCESS", "校验通过：" + rows.size() + " 行库存点");
        }
        ImportBatchResult result = masterDataImportService.importRoutingBundle(bundle);
        return new IntegrationAdapterRunResultDto(result.importBatchId(), "SUCCESS", "导入 " + result.rowCount() + " 行");
    }
}
