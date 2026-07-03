package com.plantops.integration.external;

import com.plantops.api.dto.integration.IntegrationDtos.ExternalRowPageDto;
import com.plantops.persistence.entity.ExternalCustomerOrderEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.ExternalCustomerOrderLineEntity;
import com.plantops.persistence.entity.ExternalInventoryEntity;
import com.plantops.persistence.entity.ExternalPhysicalResourceEntity;
import com.plantops.persistence.entity.ExternalProductInStockingPointEntity;
import com.plantops.persistence.entity.ExternalPurchaseOrderEntity;
import com.plantops.persistence.entity.ExternalResourceGroupEntity;
import com.plantops.persistence.entity.ExternalRoutingEntity;
import com.plantops.persistence.entity.ExternalRoutingStepEntity;
import com.plantops.persistence.entity.ExternalRoutingStepImEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOmEntity;
import com.plantops.persistence.entity.ExternalRoutingStepOsrEntity;
import com.plantops.persistence.entity.ExternalStagingEntity;
import com.plantops.persistence.entity.ExternalStandardResourceEntity;
import com.plantops.persistence.entity.ExternalStockingPointEntity;
import com.plantops.persistence.entity.ExternalWorkOrderEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationEntity;
import com.plantops.persistence.entity.ExternalWorkOrderOperationResourceEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** API-INT-03：external_* 行分页浏览。 */
@ApplicationScoped
public class ExternalDataBrowseService {

    private final Map<String, Supplier<List<? extends ExternalStagingEntity>>> masterTables = Map.ofEntries(
            Map.entry("external_stocking_point", ExternalStockingPointEntity::listInWorkspace),
            Map.entry("external_product_in_stocking_point", ExternalProductInStockingPointEntity::listInWorkspace),
            Map.entry("external_routing", ExternalRoutingEntity::listInWorkspace),
            Map.entry("external_routing_step", ExternalRoutingStepEntity::listInWorkspace),
            Map.entry("external_routing_step_on_standard_resource", ExternalRoutingStepOsrEntity::listInWorkspace),
            Map.entry("external_routing_step_input_material", ExternalRoutingStepImEntity::listInWorkspace),
            Map.entry("external_routing_step_output_material", ExternalRoutingStepOmEntity::listInWorkspace),
            Map.entry("external_resource_group", ExternalResourceGroupEntity::listInWorkspace),
            Map.entry("external_standard_resource", ExternalStandardResourceEntity::listInWorkspace),
            Map.entry("external_physical_resource", ExternalPhysicalResourceEntity::listInWorkspace));

    private final Map<String, Supplier<List<? extends ExternalStagingEntity>>> transactionalTables = Map.ofEntries(
            Map.entry("external_customer_order", ExternalCustomerOrderEntity::listInWorkspace),
            Map.entry("external_customer_order_line", ExternalCustomerOrderLineEntity::listInWorkspace),
            Map.entry("external_customer_order_line_delivery", ExternalCustomerOrderLineDeliveryEntity::listInWorkspace),
            Map.entry("external_work_order", ExternalWorkOrderEntity::listInWorkspace),
            Map.entry("external_work_order_operation", ExternalWorkOrderOperationEntity::listInWorkspace),
            Map.entry("external_work_order_operation_resource", ExternalWorkOrderOperationResourceEntity::listInWorkspace),
            Map.entry("external_inventory", ExternalInventoryEntity::listInWorkspace),
            Map.entry("external_purchase_order", ExternalPurchaseOrderEntity::listInWorkspace));

    public ExternalRowPageDto browse(
            String domain, String tableName, int page, int size, String importBatchId, String qualityStatus) {
        Supplier<List<? extends ExternalStagingEntity>> supplier = tablesFor(domain).get(tableName);
        if (supplier == null) {
            throw new NotFoundException("Unknown external table: " + tableName);
        }
        List<? extends ExternalStagingEntity> filtered = supplier.get().stream()
                .filter(row -> matches(row, importBatchId, qualityStatus))
                .toList();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        int from = Math.min(filtered.size(), safePage * safeSize);
        int to = Math.min(filtered.size(), from + safeSize);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExternalStagingEntity entity : filtered.subList(from, to)) {
            rows.add(toMap(entity));
        }
        return new ExternalRowPageDto(tableName, safePage, safeSize, filtered.size(), rows);
    }

    private static boolean matches(ExternalStagingEntity row, String importBatchId, String qualityStatus) {
        if (importBatchId != null && !importBatchId.isBlank() && !importBatchId.equals(row.importBatchId)) {
            return false;
        }
        if (qualityStatus != null && !qualityStatus.isBlank() && !qualityStatus.equals(row.qualityStatus)) {
            return false;
        }
        return true;
    }

    private Map<String, Supplier<List<? extends ExternalStagingEntity>>> tablesFor(String domain) {
        return switch (domain) {
            case "master" -> masterTables;
            case "transactional" -> transactionalTables;
            default -> throw new NotFoundException("Unknown external domain: " + domain);
        };
    }

    private static Map<String, Object> toMap(ExternalStagingEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : entity.getClass().getFields()) {
            try {
                map.put(field.getName(), field.get(entity));
            } catch (IllegalAccessException ignored) {
                // skip
            }
        }
        if (entity instanceof PanacheEntity panache) {
            map.put("id", panache.id);
        }
        return map;
    }
}
