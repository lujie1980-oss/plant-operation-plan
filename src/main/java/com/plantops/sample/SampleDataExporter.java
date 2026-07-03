package com.plantops.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SampleDataExporter {

    @Inject
    ObjectMapper objectMapper;

    public Map<String, Object> export(String workspaceId) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workspace_id", workspaceId);
        meta.put("sales_order_count", SalesOrderLineEntity.count("workspaceId", workspaceId));
        meta.put("bom_count", BomComponentEntity.count("workspaceId", workspaceId));
        meta.put("material_count", MaterialEntity.count("workspaceId", workspaceId));
        meta.put("resource_count", ProductionResourceEntity.count("workspaceId", workspaceId));
        meta.put("product_resource_count", ProductResourceEntity.count("workspaceId", workspaceId));
        meta.put("line_count", ProductionLineEntity.count("workspaceId", workspaceId));
        meta.put("parallel_rule_count", ParallelOperationRuleEntity.count("workspaceId", workspaceId));
        root.put("_meta", meta);

        root.put("salesOrderLines", exportOrders(workspaceId));
        root.put("bomComponents", exportBoms(workspaceId));
        root.put("inventory", exportInventory(workspaceId));
        root.put("materials", exportMaterials(workspaceId));
        root.put("resources", exportResources(workspaceId));
        root.put("productResources", exportProductResources(workspaceId));
        root.put("lines", exportLines(workspaceId));
        root.put("changeoverMatrix", exportChangeover(workspaceId));
        root.put("parallelOperationRules", exportParallelRules(workspaceId));
        root.put("workOrders", exportWorkOrders(workspaceId));
        return root;
    }

    public void writeJson(String workspaceId, Path path) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), export(workspaceId));
    }

    private List<Map<String, Object>> exportOrders(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SalesOrderLineEntity e : SalesOrderLineEntity.find("workspaceId", workspaceId).<SalesOrderLineEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("salesOrderNo", e.salesOrderNo);
            row.put("salesOrderLineNo", e.salesOrderLineNo);
            row.put("customerCode", e.customerCode);
            row.put("productCode", e.productCode);
            row.put("orderQty", e.orderQty);
            row.put("promiseDate", e.promiseDate != null ? e.promiseDate.toString() : null);
            row.put("dueDate", e.dueDate != null ? e.dueDate.toString() : null);
            row.put("priority", e.priority);
            row.put("expediteLevel", e.expediteLevel);
            row.put("status", e.status);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportBoms(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BomComponentEntity e : BomComponentEntity.find("workspaceId", workspaceId).<BomComponentEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("parentProductCode", e.parentProductCode);
            row.put("finishedProductCode", e.finishedProductCode);
            row.put("componentProductCode", e.componentProductCode);
            row.put("componentQty", e.componentQty);
            row.put("isCriticalComponent", e.isCriticalComponent);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportInventory(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryEntity e : InventoryEntity.find("workspaceId", workspaceId).<InventoryEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockingPointCode", e.stockingPointCode);
            row.put("productCode", e.productCode);
            row.put("onhandQty", e.onhandQty);
            row.put("reservedQty", e.reservedQty != null ? e.reservedQty : 0);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportMaterials(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialEntity e : MaterialEntity.find("workspaceId", workspaceId).<MaterialEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", e.materialCode);
            row.put("materialName", e.materialName);
            row.put("uomCode", e.uomCode);
            row.put("materialType", e.materialType);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportResources(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductionResourceEntity e : ProductionResourceEntity.find("workspaceId", workspaceId).<ProductionResourceEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceId", e.resourceId);
            row.put("areaId", e.areaId);
            row.put("bottleneck", e.bottleneck);
            row.put("runRatePerHour", e.runRatePerHour);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportProductResources(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductResourceEntity e : ProductResourceEntity.find("workspaceId", workspaceId).<ProductResourceEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productCode", e.productCode);
            row.put("resourceId", e.resourceId);
            row.put("setupTimeMinutes", e.setupTimeMinutes);
            if (e.sequenceNo != null) {
                row.put("sequenceNo", e.sequenceNo);
            }
            if (e.resourcePriority != null) {
                row.put("resourcePriority", e.resourcePriority);
            }
            if (e.operationName != null) {
                row.put("operationName", e.operationName);
            }
            if (e.processTimeSeconds != null) {
                row.put("processTimeSeconds", e.processTimeSeconds);
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportLines(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductionLineEntity e : ProductionLineEntity.find("workspaceId", workspaceId).<ProductionLineEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lineId", e.lineId);
            row.put("areaId", e.areaId);
            row.put("resourceId", e.resourceId);
            row.put("lineMinHeadcount", e.lineMinHeadcount);
            row.put("lineCapacityPerShift", e.lineCapacityPerShift);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportChangeover(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChangeoverMatrixEntity e : ChangeoverMatrixEntity.find("workspaceId", workspaceId).<ChangeoverMatrixEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operationName", e.operationName);
            row.put("attributeKey", e.attributeKey);
            row.put("fromAttributeValue", e.fromAttributeValue);
            row.put("toAttributeValue", e.toAttributeValue);
            row.put("setupMinutes", e.setupMinutes);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportParallelRules(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParallelOperationRuleEntity e : ParallelOperationRuleEntity.find("workspaceId", workspaceId).<ParallelOperationRuleEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lineId", e.lineId);
            row.put("firstProductCode", e.firstProductCode);
            row.put("secondProductCode", e.secondProductCode);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> exportWorkOrders(String workspaceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WorkOrderEntity e : WorkOrderEntity.find("workspaceId", workspaceId).<WorkOrderEntity>list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workOrderNo", e.workOrderNo);
            row.put("salesOrderNo", e.salesOrderNo);
            row.put("salesOrderLineNo", e.salesOrderLineNo);
            row.put("productCode", e.productCode);
            row.put("quantity", e.quantity);
            row.put("resourceId", e.resourceId);
            row.put("sequenceNo", e.sequenceNo);
            if (e.parentWorkOrderNo != null) {
                row.put("parentWorkOrderNo", e.parentWorkOrderNo);
            }
            rows.add(row);
        }
        return rows;
    }
}
