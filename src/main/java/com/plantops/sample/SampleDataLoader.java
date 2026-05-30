package com.plantops.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.config.ParameterRegistry;
import com.plantops.persistence.entity.*;
import com.plantops.workspace.WorkspaceConstants;
import com.plantops.workspace.WorkspaceContext;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@ApplicationScoped
public class SampleDataLoader {

    @ConfigProperty(name = "plantops.sample-data.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "plantops.sample-data.force-reload", defaultValue = "false")
    boolean forceReload;

    @ConfigProperty(name = "plantops.sample-data.resource", defaultValue = "sample-data/factory-demo.json")
    String demoResource;

    @Inject
    ParameterRegistry parameterRegistry;

    @Inject
    com.plantops.scenario.TimeslotHorizonService timeslotHorizonService;

    @Inject
    Instance<SampleDataLoader> self;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    com.plantops.masterdata.ChangeoverExcelImportService changeoverExcelImportService;

    @Inject
    com.plantops.masterdata.ParallelOperationExcelImportService parallelOperationExcelImportService;

    @Inject
    com.plantops.masterdata.ContinuousProductionExcelImportService continuousProductionExcelImportService;

    private static final String DEFAULT_CHANGEOVER_XLSX = "/sample-data/changeover-kt-prefix-duration.xlsx";
    private static final String DEFAULT_PARALLEL_OPERATION_XLSX = "/sample-data/u-line-parallel-operation-list.xlsx";
    private static final String DEFAULT_CONTINUOUS_PRODUCTION_XLSX = "/sample-data/continuous-production-list.xlsx";

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        String prev = workspaceContext.getWorkspaceId();
        try {
            workspaceContext.setWorkspaceId(WorkspaceConstants.DEFAULT_ID);
            parameterRegistry.ensureDefaults();
            if (forceReload) {
                reloadDemo();
                return;
            }
            if (SalesOrderLineEntity.count("workspaceId", WorkspaceConstants.DEFAULT_ID) > 0) {
                self.get().extendCalendarsToHorizon();
                return;
            }
            loadDemo();
        } finally {
            workspaceContext.setWorkspaceId(prev);
        }
    }

    @Transactional
    public void reloadDemo() {
        clearAll();
        loadDemo(demoResource);
    }

    @Transactional
    public void reloadDemo(String resourcePath) {
        clearAll();
        loadDemo(resourcePath);
    }

    @Transactional
    void clearAll() {
        String ws = WorkspaceResolver.currentWorkspaceId();
        DetailScheduleOperationEntity.delete("workspaceId", ws);
        MasterPlanAllocationEntity.delete("workspaceId", ws);
        LineOpeningDecisionEntity.delete("workspaceId", ws);
        ShortageRecommendationEntity.delete("workspaceId", ws);
        PlanDispatchEntity.delete("workspaceId", ws);
        PlanningEventEntity.delete("workspaceId", ws);
        PlanVersionEntity.delete("workspaceId", ws);
        KittingResultEntity.delete("workspaceId", ws);
        WorkOrderEntity.delete("workspaceId", ws);
        ChangeoverMatrixEntity.delete("workspaceId", ws);
        ParallelOperationRuleEntity.delete("workspaceId", ws);
        ContinuousProductionRuleEntity.delete("workspaceId", ws);
        OperationTransferTimeRuleEntity.delete("workspaceId", ws);
        ProductResourceEntity.delete("workspaceId", ws);
        ProductionLineEntity.delete("workspaceId", ws);
        ResourceCalendarEntity.delete("workspaceId", ws);
        ShiftHeadcountEntity.delete("workspaceId", ws);
        InventoryEntity.delete("workspaceId", ws);
        BomComponentEntity.delete("workspaceId", ws);
        SalesOrderLineEntity.delete("workspaceId", ws);
        ProductionResourceEntity.delete("workspaceId", ws);
        PlanningPipelineRunEntity.delete("workspaceId", ws);
        SystemParameterEntity.delete("workspaceId", ws);
    }

    @Transactional
    public void loadDemo() {
        loadDemo(demoResource);
    }

    @Transactional
    public void loadDemo(String resourcePath) {
        String p = resourcePath == null ? null : resourcePath.trim();
        if (p == null || p.isBlank()) {
            throw new IllegalArgumentException("sample-data resourcePath ????");
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        try (InputStream in = getClass().getResourceAsStream(p)) {
            if (in == null) {
                throw new IllegalArgumentException("?????????? " + p);
            }
            JsonNode root = objectMapper.readTree(in);
            root.get("salesOrderLines").forEach(n -> persistOrder(n));
            root.get("bomComponents").forEach(n -> persistBom(n));
            root.get("inventory").forEach(n -> persistInventory(n));
            root.get("resources").forEach(n -> persistResource(n));
            root.get("productResources").forEach(n -> persistProductResource(n));
            root.get("lines").forEach(n -> persistLine(n));
            root.get("changeoverMatrix").forEach(n -> persistChangeover(n));
            seedDefaultChangeoverIfEmpty();
            seedDefaultParallelOperationsIfEmpty();
            seedDefaultContinuousProductionIfEmpty();
            root.get("workOrders").forEach(n -> persistWorkOrder(n));
            seedCalendars();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load sample data", e);
        }
    }

    @Transactional
    public void extendCalendarsToHorizon() {
        CalendarSeedIds ids = collectCalendarSeedIds();
        int horizonDays = timeslotHorizonService.totalCalendarDays();
        LocalDate start = LocalDate.now();
        for (int d = 0; d < horizonDays; d++) {
            LocalDate date = start.plusDays(d);
            for (String resourceId : ids.resourceIds()) {
                boolean exists = ResourceCalendarEntity.find(
                        "workspaceId = ?1 and resourceId = ?2 and calendarDate = ?3 and shiftId = ?4",
                        WorkspaceResolver.currentWorkspaceId(), resourceId, date, "DAY")
                        .firstResultOptional()
                        .isPresent();
                if (!exists) {
                    ResourceCalendarEntity cal = new ResourceCalendarEntity();
                    cal.resourceId = resourceId;
                    cal.shiftId = "DAY";
                    cal.calendarDate = date;
                    cal.availableCapacityMinutes = parameterRegistry.getInt("shift_capacity_minutes", 480);
                    cal.stampWorkspace();
                    cal.persist();
                }
            }
            for (String areaId : ids.areaIds()) {
                boolean exists = ShiftHeadcountEntity.find(
                        "workspaceId = ?1 and areaId = ?2 and calendarDate = ?3 and shiftId = ?4",
                        WorkspaceResolver.currentWorkspaceId(), areaId, date, "DAY")
                        .firstResultOptional()
                        .isPresent();
                if (!exists) {
                    ShiftHeadcountEntity hc = new ShiftHeadcountEntity();
                    hc.areaId = areaId;
                    hc.shiftId = "DAY";
                    hc.calendarDate = date;
                    hc.availableHeadcount = 8;
                    hc.stampWorkspace();
                    hc.persist();
                }
            }
        }
    }

    private void seedCalendars() {
        CalendarSeedIds ids = collectCalendarSeedIds();
        int horizonDays = timeslotHorizonService.totalCalendarDays();
        LocalDate start = LocalDate.now();
        for (int d = 0; d < horizonDays; d++) {
            LocalDate date = start.plusDays(d);
            for (String resourceId : ids.resourceIds()) {
                ResourceCalendarEntity cal = new ResourceCalendarEntity();
                cal.resourceId = resourceId;
                cal.shiftId = "DAY";
                cal.calendarDate = date;
                cal.availableCapacityMinutes = parameterRegistry.getInt("shift_capacity_minutes", 480);
                cal.stampWorkspace();
                cal.persist();
            }
            for (String areaId : ids.areaIds()) {
                ShiftHeadcountEntity hc = new ShiftHeadcountEntity();
                hc.areaId = areaId;
                hc.shiftId = "DAY";
                hc.calendarDate = date;
                hc.availableHeadcount = 8;
                hc.stampWorkspace();
                hc.persist();
            }
        }
    }

    private CalendarSeedIds collectCalendarSeedIds() {
        java.util.Set<String> resourceIds = new java.util.LinkedHashSet<>();
        java.util.Set<String> areaIds = new java.util.LinkedHashSet<>();
        for (ProductionResourceEntity res : ProductionResourceEntity.listInWorkspace()) {
            if (res.bottleneck) {
                resourceIds.add(res.resourceId);
            }
            if (res.areaId != null && !res.areaId.isBlank()) {
                areaIds.add(res.areaId);
            }
        }
        for (ProductResourceEntity pr : ProductResourceEntity.listInWorkspace()) {
            resourceIds.add(pr.resourceId);
        }
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.areaId != null && !line.areaId.isBlank()) {
                areaIds.add(line.areaId);
            }
        }
        if (areaIds.isEmpty()) {
            areaIds.add("AREA-1");
        }
        return new CalendarSeedIds(resourceIds, areaIds);
    }

    private record CalendarSeedIds(java.util.Set<String> resourceIds, java.util.Set<String> areaIds) {
    }

    private void persistOrder(JsonNode n) {
        SalesOrderLineEntity e = new SalesOrderLineEntity();
        e.salesOrderNo = n.get("salesOrderNo").asText();
        e.salesOrderLineNo = n.get("salesOrderLineNo").asInt();
        e.customerCode = textOrNull(n, "customerCode");
        e.productCode = n.get("productCode").asText();
        e.orderQty = BigDecimal.valueOf(n.get("orderQty").asDouble());
        e.promiseDate = LocalDate.parse(n.get("promiseDate").asText());
        e.dueDate = LocalDate.parse(n.get("dueDate").asText());
        e.priority = n.path("priority").asInt(5);
        e.expediteLevel = n.path("expediteLevel").asInt(0);
        e.status = n.path("status").asText("OPEN");
        e.lastModifiedTs = LocalDateTime.now();
        e.stampWorkspace();
        e.persist();
    }

    private void persistBom(JsonNode n) {
        BomComponentEntity e = new BomComponentEntity();
        e.bomId = "BOM-DEMO";
        e.bomVersion = "V1";
        e.parentProductCode = n.get("parentProductCode").asText();
        e.finishedProductCode = n.path("finishedProductCode").asText(e.parentProductCode);
        e.componentProductCode = n.get("componentProductCode").asText();
        e.componentQty = BigDecimal.valueOf(n.get("componentQty").asDouble());
        e.isCriticalComponent = n.path("isCriticalComponent").asBoolean(false);
        e.stampWorkspace();
        e.persist();
    }

    private void persistInventory(JsonNode n) {
        InventoryEntity e = new InventoryEntity();
        e.stockingPointCode = n.get("stockingPointCode").asText();
        e.productCode = n.get("productCode").asText();
        e.onhandQty = BigDecimal.valueOf(n.get("onhandQty").asDouble());
        e.reservedQty = BigDecimal.valueOf(n.path("reservedQty").asDouble(0));
        e.stampWorkspace();
        e.persist();
    }

    private void persistResource(JsonNode n) {
        ProductionResourceEntity e = new ProductionResourceEntity();
        e.resourceId = n.get("resourceId").asText();
        e.areaId = n.get("areaId").asText();
        e.bottleneck = n.path("bottleneck").asBoolean(false);
        e.runRatePerHour = BigDecimal.valueOf(n.path("runRatePerHour").asDouble(1));
        e.stampWorkspace();
        e.persist();
    }

    private void persistProductResource(JsonNode n) {
        String productCode = n.get("productCode").asText();
        String resourceId = n.get("resourceId").asText();

        // ????????????????(productCode, resourceId)?????????????????????
        ProductResourceEntity e = ProductResourceEntity
                .find("workspaceId = ?1 and productCode = ?2 and resourceId = ?3",
                        WorkspaceResolver.currentWorkspaceId(), productCode, resourceId)
                .firstResultOptional()
                .map(ProductResourceEntity.class::cast)
                .orElseGet(ProductResourceEntity::new);

        e.productCode = productCode;
        e.resourceId = resourceId;
        e.setupTimeMinutes = n.path("setupTimeMinutes").asInt(0);
        if (n.has("sequenceNo") && !n.get("sequenceNo").isNull()) {
            e.sequenceNo = n.get("sequenceNo").asInt();
        }
        e.resourcePriority = n.has("resourcePriority") && !n.get("resourcePriority").isNull()
                ? n.get("resourcePriority").asInt()
                : ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY;
        e.operationName = textOrNull(n, "operationName");
        if (n.has("processTimeSeconds") && !n.get("processTimeSeconds").isNull()) {
            e.processTimeSeconds = BigDecimal.valueOf(n.get("processTimeSeconds").asDouble());
        }

        e.ensureWorkspace();
        if (e.id == null) {
            e.persist();
        }
    }

    private void persistLine(JsonNode n) {
        ProductionLineEntity e = new ProductionLineEntity();
        e.lineId = n.get("lineId").asText();
        e.areaId = n.get("areaId").asText();
        e.resourceId = n.get("resourceId").asText();
        e.lineMinHeadcount = n.get("lineMinHeadcount").asInt();
        e.lineCapacityPerShift = n.get("lineCapacityPerShift").asInt();
        e.stampWorkspace();
        e.persist();
    }

    private void seedDefaultChangeoverIfEmpty() {
        if (ChangeoverMatrixEntity.count("workspaceId", WorkspaceResolver.currentWorkspaceId()) > 0) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_CHANGEOVER_XLSX)) {
            if (in == null) {
                return;
            }
            changeoverExcelImportService.importWorkbook(in, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load default changeover matrix", e);
        }
    }

    private void seedDefaultParallelOperationsIfEmpty() {
        if (ParallelOperationRuleEntity.count("workspaceId", WorkspaceResolver.currentWorkspaceId()) > 0) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_PARALLEL_OPERATION_XLSX)) {
            if (in == null) {
                return;
            }
            parallelOperationExcelImportService.importWorkbook(in, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load default parallel operation rules", e);
        }
    }

    private void seedDefaultContinuousProductionIfEmpty() {
        if (ContinuousProductionRuleEntity.count("workspaceId", WorkspaceResolver.currentWorkspaceId()) > 0) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_CONTINUOUS_PRODUCTION_XLSX)) {
            if (in == null) {
                return;
            }
            continuousProductionExcelImportService.importWorkbook(in, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load default continuous production rules", e);
        }
    }

    private void persistChangeover(JsonNode n) {
        ChangeoverMatrixEntity e = new ChangeoverMatrixEntity();
        if (n.has("operationName")) {
            e.operationName = n.get("operationName").asText();
        } else if (n.has("resourceId")) {
            e.operationName = n.get("resourceId").asText();
        }
        if (n.has("attributeKey")) {
            e.attributeKey = n.get("attributeKey").asText();
        } else {
            e.attributeKey = "productCode";
        }
        if (n.has("fromAttributeValue")) {
            e.fromAttributeValue = n.get("fromAttributeValue").asText("*");
        } else if (n.has("fromProductCode")) {
            e.fromAttributeValue = n.get("fromProductCode").asText("*");
        } else {
            e.fromAttributeValue = "*";
        }
        if (n.has("toAttributeValue")) {
            e.toAttributeValue = n.get("toAttributeValue").asText("*");
        } else if (n.has("toProductCode")) {
            e.toAttributeValue = n.get("toProductCode").asText("*");
        } else {
            e.toAttributeValue = "*";
        }
        e.setupMinutes = n.get("setupMinutes").asInt();
        e.stampWorkspace();
        e.persist();
    }

    private void persistWorkOrder(JsonNode n) {
        WorkOrderEntity e = new WorkOrderEntity();
        e.workOrderNo = n.get("workOrderNo").asText();
        e.salesOrderNo = n.get("salesOrderNo").asText();
        e.salesOrderLineNo = n.get("salesOrderLineNo").asInt();
        e.productCode = n.get("productCode").asText();
        e.quantity = BigDecimal.valueOf(n.get("quantity").asDouble());
        e.resourceId = n.get("resourceId").asText();
        e.sequenceNo = n.get("sequenceNo").asInt();
        if (n.has("parentWorkOrderNo") && !n.get("parentWorkOrderNo").isNull()) {
            e.parentWorkOrderNo = n.get("parentWorkOrderNo").asText();
        }
        e.stampWorkspace();
        e.persist();
    }

    private static String textOrNull(JsonNode n, String field) {
        return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null;
    }
}
