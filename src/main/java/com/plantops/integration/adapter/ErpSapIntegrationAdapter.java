package com.plantops.integration.adapter;

import com.plantops.api.dto.DemandPoolEntryDto;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineDeliveryRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderLineRow;
import com.plantops.api.dto.integration.IntegrationDtos.CustomerOrderRow;
import com.plantops.api.dto.integration.IntegrationDtos.ImportBatchResult;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;
import com.plantops.api.dto.integration.IntegrationDtos.TransactionalBundleImport;
import com.plantops.integration.IntegrationAdapterPort;
import com.plantops.integration.erp.ErpPort;
import com.plantops.transactional.external.TransactionalDataExternalImportService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** ADP-ERP-SAP：从 ErpPort 拉取订单写入 external_*（AC-INT-02）。 */
@ApplicationScoped
public class ErpSapIntegrationAdapter implements IntegrationAdapterPort {

    @Inject
    ErpPort erpPort;

    @Inject
    TransactionalDataExternalImportService transactionalImportService;

    @Override
    public String adapterId() {
        return "ADP-ERP-SAP";
    }

    @Override
    public String sourceSystemCode() {
        return "ERP_SAP";
    }

    @Override
    public IntegrationAdapterRunResultDto run(boolean validateOnly) {
        if (validateOnly) {
            return new IntegrationAdapterRunResultDto(null, "SUCCESS", "validateOnly=true，未写入 staging");
        }
        List<DemandPoolEntryDto> lines = erpPort.fetchOpenOrderLines();
        if (lines.isEmpty()) {
            return new IntegrationAdapterRunResultDto(null, "SUCCESS", "无开放订单行");
        }
        TransactionalBundleImport bundle = toBundle(lines);
        ImportBatchResult result = transactionalImportService.importBundle(bundle);
        return new IntegrationAdapterRunResultDto(
                result.importBatchId(), "SUCCESS", "导入 " + result.rowCount() + " 行");
    }

    private TransactionalBundleImport toBundle(List<DemandPoolEntryDto> lines) {
        List<CustomerOrderRow> orders = new ArrayList<>();
        List<CustomerOrderLineRow> orderLines = new ArrayList<>();
        List<CustomerOrderLineDeliveryRow> deliveries = new ArrayList<>();
        for (DemandPoolEntryDto line : lines) {
            String orderNo = line.salesOrderNo() != null ? line.salesOrderNo() : "ERP-UNKNOWN";
            int lineNo = line.salesOrderLineNo();
            orders.add(new CustomerOrderRow(orderNo, null, LocalDate.now(), "OPEN", line.priority()));
            orderLines.add(new CustomerOrderLineRow(orderNo, lineNo, line.productCode(), line.orderQty(), "EA"));
            deliveries.add(new CustomerOrderLineDeliveryRow(
                    orderNo,
                    lineNo,
                    1,
                    line.orderQty(),
                    line.dueDate() != null ? line.dueDate() : LocalDate.now().plusDays(14),
                    "OPEN"));
        }
        return new TransactionalBundleImport(
                sourceSystemCode(), orders, orderLines, deliveries, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
