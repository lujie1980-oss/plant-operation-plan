package com.plantops.transactional.external;

import com.plantops.api.dto.integration.IntegrationDtos.ExternalTableInfoDto;
import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;

import java.util.List;
import java.util.function.Supplier;

/** §12 external_* 交易表清单（API-INT-02）。 */
public final class ExternalTransactionalCatalog {

    private ExternalTransactionalCatalog() {}

    private record TableDef(String tableName, String label, Supplier<Long> rowCounter) {}

    private static final List<TableDef> TRANSACTIONAL_TABLES = List.of(
            new TableDef(
                    "external_customer_order",
                    "客户订单",
                    () -> (long) ExternalCustomerOrderEntity.listInWorkspace().size()),
            new TableDef(
                    "external_customer_order_line",
                    "订单行",
                    () -> (long) ExternalCustomerOrderLineEntity.listInWorkspace().size()),
            new TableDef(
                    "external_customer_order_line_delivery",
                    "交付批次",
                    () -> (long) ExternalCustomerOrderLineDeliveryEntity.listInWorkspace().size()),
            new TableDef("external_work_order", "工单", () -> (long) ExternalWorkOrderEntity.listInWorkspace().size()),
            new TableDef(
                    "external_work_order_operation",
                    "工单工序",
                    () -> (long) ExternalWorkOrderOperationEntity.listInWorkspace().size()),
            new TableDef(
                    "external_work_order_operation_resource",
                    "工序资源",
                    () -> (long) ExternalWorkOrderOperationResourceEntity.listInWorkspace().size()),
            new TableDef("external_inventory", "库存", () -> (long) ExternalInventoryEntity.listInWorkspace().size()),
            new TableDef(
                    "external_purchase_order",
                    "采购订单",
                    () -> (long) ExternalPurchaseOrderEntity.listInWorkspace().size()));

    public static List<ExternalTableInfoDto> transactionalTables() {
        return TRANSACTIONAL_TABLES.stream()
                .map(def -> new ExternalTableInfoDto(def.tableName(), def.label(), def.rowCounter().get()))
                .toList();
    }
}
