package com.plantops.masterdata.external;

import com.plantops.api.dto.integration.IntegrationDtos.ExternalTableInfoDto;
import com.plantops.persistence.entity.ExternalPhysicalResourceEntity;
import com.plantops.persistence.entity.ExternalProductInStockingPointEntity;
import com.plantops.persistence.entity.ExternalResourceGroupEntity;
import com.plantops.persistence.entity.ExternalRoutingEntity;
import com.plantops.persistence.entity.ExternalRoutingStepEntity;
import com.plantops.persistence.entity.ExternalRoutingStepImEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOmEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOsrEntity;
import com.plantops.persistence.entity.ExternalStandardResourceEntity;
import com.plantops.persistence.entity.ExternalStockingPointEntity;

import java.util.List;
import java.util.function.Supplier;

/** §11 external_* 表清单（API-INT-02）。 */
public final class ExternalMasterDataCatalog {

    private ExternalMasterDataCatalog() {}

    private record TableDef(String tableName, String label, Supplier<Long> rowCounter) {}

    private static final List<TableDef> MASTER_TABLES = List.of(
            new TableDef("external_stocking_point", "库存点", () -> (long) ExternalStockingPointEntity.listInWorkspace().size()),
            new TableDef(
                    "external_product_in_stocking_point",
                    "PISP 物料",
                    () -> (long) ExternalProductInStockingPointEntity.listInWorkspace().size()),
            new TableDef("external_routing", "工艺路线", () -> (long) ExternalRoutingEntity.listInWorkspace().size()),
            new TableDef("external_routing_step", "工艺步骤", () -> (long) ExternalRoutingStepEntity.listInWorkspace().size()),
            new TableDef(
                    "external_routing_step_on_standard_resource",
                    "步骤资源",
                    () -> (long) ExternalRoutingStepOsrEntity.listInWorkspace().size()),
            new TableDef(
                    "external_routing_step_input_material",
                    "步骤投料",
                    () -> (long) ExternalRoutingStepImEntity.listInWorkspace().size()),
            new TableDef(
                    "external_routing_step_output_material",
                    "步骤产出",
                    () -> (long) ExternalRoutingStepOmEntity.listInWorkspace().size()),
            new TableDef("external_resource_group", "资源组", () -> (long) ExternalResourceGroupEntity.listInWorkspace().size()),
            new TableDef(
                    "external_standard_resource",
                    "标准资源",
                    () -> (long) ExternalStandardResourceEntity.listInWorkspace().size()),
            new TableDef(
                    "external_physical_resource",
                    "物理资源",
                    () -> (long) ExternalPhysicalResourceEntity.listInWorkspace().size()));

    public static List<ExternalTableInfoDto> masterTables() {
        return MASTER_TABLES.stream()
                .map(def -> new ExternalTableInfoDto(def.tableName(), def.label(), def.rowCounter().get()))
                .toList();
    }

    public static List<ExternalTableInfoDto> transactionalTables() {
        return com.plantops.transactional.external.ExternalTransactionalCatalog.transactionalTables();
    }
}
