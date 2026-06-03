package com.plantops.api;

import com.plantops.api.dto.masterdata.MasterDataDtos.BomDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ChangeoverDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ContinuousProductionRuleDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.OperationPostProcessingRuleDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.OperationTransferTimeRuleDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ParallelOperationRuleDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ULinePairDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.InventoryDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MaterialDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MaterialLeadTimeRuleDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionCreateDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionUpdateDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ProductResourceDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ProductionLineDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ResourceCalendarDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ResourceDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.SalesOrderDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.ShiftHeadcountDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.BusinessRuleScopeDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.SystemParameterDto;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos;
import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.masterdata.MasterDataExtensionService;
import com.plantops.masterdata.MasterFieldDefinitionService;
import com.plantops.masterdata.MasterDataValidationService;
import com.plantops.sample.SampleDataLoader;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.ContinuousProductionRuleEntity;
import com.plantops.persistence.entity.OperationPostProcessingRuleEntity;
import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.MaterialLeadTimeRuleEntity;
import com.plantops.masterdata.ProductResourceOperationNames;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.ShiftHeadcountEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * ?????????? / ?? / ?? / ???? * ?????????????DTO ??id ??null ?????? */
@Path("/api/v1/master-data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MasterDataResource {

    @Inject
    ParameterRegistry parameterRegistry;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    com.plantops.masterdata.FactoryCalendarService factoryCalendarService;

    @Inject
    MasterDataValidationService validationService;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    MasterFieldDefinitionService masterFieldDefinitionService;

    // -------------------------- 字段目录 --------------------------

    @GET
    @Path("/field-schema/{entityType}")
    public List<MasterFieldDefinitionDto> fieldSchema(@PathParam("entityType") String entityType) {
        return masterFieldDefinitionService.listSchema(entityType);
    }

    @POST
    @Path("/field-definitions")
    @Transactional
    public MasterFieldDefinitionDto createFieldDefinition(MasterFieldDefinitionCreateDto dto) {
        return masterFieldDefinitionService.createCustom(dto);
    }

    @PUT
    @Path("/field-definitions/{id}")
    @Transactional
    public MasterFieldDefinitionDto updateFieldDefinition(
            @PathParam("id") Long id,
            MasterFieldDefinitionUpdateDto dto) {
        return masterFieldDefinitionService.update(id, dto);
    }

    @DELETE
    @Path("/field-definitions/{id}")
    @Transactional
    public Response deleteFieldDefinition(@PathParam("id") Long id) {
        masterFieldDefinitionService.delete(id);
        return Response.noContent().build();
    }

    // -------------------------- ?????--------------------------

    @GET
    @Path("/sales-orders")
    public List<SalesOrderDto> listSalesOrders() {
        return SalesOrderLineEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((SalesOrderLineEntity e) -> e.salesOrderNo)
                        .thenComparingInt(e -> e.salesOrderLineNo))
                .map(MasterDataResource::toSalesOrderDto)
                .toList();
    }

    // -------------------------- ?????????--------------------------

    @GET
    @Path("/validation")
    public MasterDataValidationDtos.ValidationReport validate() {
        return validationService.validateAll();
    }

    @POST
    @Path("/sales-orders")
    @Transactional
    public SalesOrderDto upsertSalesOrder(SalesOrderDto dto) {
        if (dto == null) {
            badRequest("body ????");
        }
        if (dto.salesOrderNo() == null || dto.salesOrderNo().isBlank()) {
            badRequest("[?? " + MasterDataValidationService.SO_PRODUCT_EMPTY + "] salesOrderNo ????");
        }
        if (dto.salesOrderLineNo() <= 0) {
            badRequest("salesOrderLineNo ??????");
        }
        if (dto.productCode() == null || dto.productCode().isBlank()) {
            badRequest("[?? " + MasterDataValidationService.SO_PRODUCT_EMPTY + "] productCode ????");
        }
        if (dto.orderQty() == null || dto.orderQty().compareTo(BigDecimal.ZERO) <= 0) {
            badRequest("[?? " + MasterDataValidationService.SO_QTY_NONPOSITIVE + "] orderQty ?? > 0");
        }
        if (dto.dueDate() == null) {
            badRequest("[?? " + MasterDataValidationService.SO_DUEDATE_EMPTY + "] dueDate ????");
        }

        SalesOrderLineEntity existing = SalesOrderLineEntity.findByKey(dto.salesOrderNo(), dto.salesOrderLineNo());
        if (dto.id() == null && existing != null) {
            badRequest("[?? " + MasterDataValidationService.SO_LINE_DUP + "] ????????? "
                    + dto.salesOrderNo() + "-" + dto.salesOrderLineNo());
        }
        if (dto.id() != null && existing != null && !existing.id.equals(dto.id())) {
            badRequest("[?? " + MasterDataValidationService.SO_LINE_DUP + "] ??????????? "
                    + dto.salesOrderNo() + "-" + dto.salesOrderLineNo());
        }

        SalesOrderLineEntity e = dto.id() != null
                ? findRequired(SalesOrderLineEntity.findById(dto.id()), "????")
                : new SalesOrderLineEntity();
        e.salesOrderNo = dto.salesOrderNo();
        e.salesOrderLineNo = dto.salesOrderLineNo();
        e.customerCode = dto.customerCode();
        e.productCode = dto.productCode();
        e.orderQty = dto.orderQty();
        e.uom = dto.uom();
        e.promiseDate = dto.promiseDate();
        e.dueDate = dto.dueDate();
        e.priority = dto.priority();
        e.expediteLevel = dto.expediteLevel();
        e.status = dto.status() != null ? dto.status() : "OPEN";
        e.scheduleLockFlag = dto.scheduleLockFlag();
        e.lastModifiedTs = LocalDateTime.now();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toSalesOrderDto(e);
    }

    @DELETE
    @Path("/sales-orders/{id}")
    @Transactional
    public Response deleteSalesOrder(@PathParam("id") Long id) {
        boolean deleted = SalesOrderLineEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static SalesOrderDto toSalesOrderDto(SalesOrderLineEntity e) {
        return new SalesOrderDto(
                e.id,
                e.salesOrderNo,
                e.salesOrderLineNo,
                e.customerCode,
                e.productCode,
                e.orderQty,
                e.uom,
                e.promiseDate,
                e.dueDate,
                e.priority,
                e.expediteLevel,
                e.status,
                e.scheduleLockFlag);
    }

    // -------------------------- BOM --------------------------

    @GET
    @Path("/boms")
    public List<BomDto> listBoms() {
        return BomComponentEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((BomComponentEntity e) -> e.parentProductCode)
                        .thenComparing(e -> e.componentProductCode))
                .map(MasterDataResource::toBomDto)
                .toList();
    }

    @POST
    @Path("/boms")
    @Transactional
    public BomDto upsertBom(BomDto dto) {
        BomComponentEntity e = dto.id() != null
                ? findRequired(BomComponentEntity.findById(dto.id()), "BOM ??")
                : new BomComponentEntity();
        e.finishedProductCode = dto.finishedProductCode();
        e.bomId = dto.bomId();
        e.bomVersion = dto.bomVersion();
        e.parentProductCode = dto.parentProductCode();
        e.componentProductCode = dto.componentProductCode();
        e.componentQty = dto.componentQty();
        e.isCriticalComponent = dto.isCriticalComponent();
        e.bomEffectiveFrom = dto.bomEffectiveFrom();
        e.bomEffectiveTo = dto.bomEffectiveTo();
        e.componentEffectiveFrom = dto.componentEffectiveFrom();
        e.componentEffectiveTo = dto.componentEffectiveTo();
        e.scrapRate = dto.scrapRate();
        e.lotSize = dto.lotSize();
        e.lotSizeMultiple = dto.lotSizeMultiple();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toBomDto(e);
    }

    @DELETE
    @Path("/boms/{id}")
    @Transactional
    public Response deleteBom(@PathParam("id") Long id) {
        boolean deleted = BomComponentEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static BomDto toBomDto(BomComponentEntity e) {
        return new BomDto(
                e.id,
                e.finishedProductCode,
                e.bomId,
                e.bomVersion,
                e.parentProductCode,
                e.componentProductCode,
                e.componentQty,
                e.isCriticalComponent,
                e.bomEffectiveFrom,
                e.bomEffectiveTo,
                e.componentEffectiveFrom,
                e.componentEffectiveTo,
                e.scrapRate,
                e.lotSize,
                e.lotSizeMultiple);
    }

    // -------------------------- 物料主数据 --------------------------

    @GET
    @Path("/materials")
    public List<MaterialDto> listMaterials() {
        return MaterialEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing((MaterialEntity e) -> e.materialCode))
                .map(MasterDataResource::toMaterialDto)
                .toList();
    }

    @POST
    @Path("/materials")
    @Transactional
    public MaterialDto upsertMaterial(MaterialDto dto) {
        if (dto == null) {
            badRequest("body 不能为空");
        }
        if (dto.materialCode() == null || dto.materialCode().isBlank()) {
            badRequest("materialCode 不能为空");
        }
        MaterialEntity existing = MaterialEntity.findByCode(dto.materialCode().trim());
        if (dto.id() == null && existing != null) {
            badRequest("物料已存在: " + dto.materialCode());
        }
        if (dto.id() != null && existing != null && !existing.id.equals(dto.id())) {
            badRequest("物料代码冲突: " + dto.materialCode());
        }
        MaterialEntity e = dto.id() != null
                ? findRequired(MaterialEntity.findById(dto.id()), "物料")
                : new MaterialEntity();
        e.siteCode = dto.siteCode();
        e.materialCode = dto.materialCode().trim();
        e.materialName = dto.materialName();
        e.uomCode = dto.uomCode();
        e.materialType = dto.materialType();
        MasterDataExtensionService.applyMaterialExtensions(e, dto.extensions());
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toMaterialDto(e);
    }

    @DELETE
    @Path("/materials/{id}")
    @Transactional
    public Response deleteMaterial(@PathParam("id") Long id) {
        boolean deleted = MaterialEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static MaterialDto toMaterialDto(MaterialEntity e) {
        return new MaterialDto(
                e.id,
                e.siteCode,
                e.materialCode,
                e.materialName,
                e.uomCode,
                e.materialType,
                MasterDataExtensionService.readMaterialExtensions(e));
    }

    // -------------------------- ?? --------------------------

    @GET
    @Path("/inventory")
    public List<InventoryDto> listInventory() {
        return InventoryEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((InventoryEntity e) -> e.productCode)
                        .thenComparing(e -> e.stockingPointCode))
                .map(MasterDataResource::toInventoryDto)
                .toList();
    }

    @POST
    @Path("/inventory")
    @Transactional
    public InventoryDto upsertInventory(InventoryDto dto) {
        InventoryEntity e = dto.id() != null
                ? findRequired(InventoryEntity.findById(dto.id()), "??")
                : new InventoryEntity();
        e.stockingPointCode = dto.stockingPointCode();
        e.productCode = dto.productCode();
        e.onhandQty = nz(dto.onhandQty());
        e.reservedQty = nz(dto.reservedQty());
        e.qualityHoldQty = nz(dto.qualityHoldQty());
        e.inTransitQty = nz(dto.inTransitQty());
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toInventoryDto(e);
    }

    @DELETE
    @Path("/inventory/{id}")
    @Transactional
    public Response deleteInventory(@PathParam("id") Long id) {
        boolean deleted = InventoryEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static InventoryDto toInventoryDto(InventoryEntity e) {
        return new InventoryDto(
                e.id,
                e.stockingPointCode,
                e.productCode,
                e.onhandQty,
                e.reservedQty,
                e.qualityHoldQty,
                e.inTransitQty);
    }

    // -------------------------- ???? --------------------------

    @GET
    @Path("/resources")
    public List<ResourceDto> listResources() {
        return ProductionResourceEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing((ProductionResourceEntity e) -> e.resourceId))
                .map(MasterDataResource::toResourceDto)
                .toList();
    }

    @POST
    @Path("/resources")
    @Transactional
    public ResourceDto upsertResource(ResourceDto dto) {
        if (dto == null) {
            badRequest("body ????");
        }
        if (dto.resourceId() == null || dto.resourceId().isBlank()) {
            badRequest("resourceId ????");
        }
        if (dto.areaId() == null || dto.areaId().isBlank()) {
            badRequest("areaId ????");
        }
        ProductionResourceEntity byKey = ProductionResourceEntity.findByResourceId(dto.resourceId());
        if (dto.id() == null && byKey != null) {
            badRequest("???????? " + dto.resourceId());
        }
        if (dto.id() != null && byKey != null && !byKey.id.equals(dto.id())) {
            badRequest("???? resourceId ??: " + dto.resourceId());
        }
        ProductionResourceEntity e = dto.id() != null
                ? findRequired(ProductionResourceEntity.findById(dto.id()), "????")
                : new ProductionResourceEntity();
        e.resourceId = dto.resourceId();
        e.resourceGroup = dto.resourceGroup();
        e.areaId = dto.areaId();
        e.bottleneck = dto.bottleneck();
        e.runRatePerHour = nz(dto.runRatePerHour());
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toResourceDto(e);
    }

    @DELETE
    @Path("/resources/{id}")
    @Transactional
    public Response deleteResource(@PathParam("id") Long id) {
        boolean deleted = ProductionResourceEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ResourceDto toResourceDto(ProductionResourceEntity e) {
        return new ResourceDto(
                e.id,
                e.resourceId,
                e.resourceGroup,
                e.areaId,
                e.bottleneck,
                e.runRatePerHour);
    }

    // -------------------------- ?????? --------------------------

    @GET
    @Path("/product-resources")
    public List<ProductResourceDto> listProductResources() {
        return ProductResourceEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ProductResourceEntity e) -> e.productCode)
                        .thenComparing(e -> e.sequenceNo != null ? e.sequenceNo : Integer.MAX_VALUE)
                        .thenComparing(e -> e.resourcePriority != null ? e.resourcePriority : Integer.MAX_VALUE)
                        .thenComparing(e -> e.resourceId))
                .map(MasterDataResource::toProductResourceDto)
                .toList();
    }

    @POST
    @Path("/product-resources")
    @Transactional
    public ProductResourceDto upsertProductResource(ProductResourceDto dto) {
        if (dto == null) {
            badRequest("body ????");
        }
        if (dto.productCode() == null || dto.productCode().isBlank()) {
            badRequest("productCode ????");
        }
        if (dto.resourceId() == null || dto.resourceId().isBlank()) {
            badRequest("resourceId ????");
        }
        if (ProductionResourceEntity.findByResourceId(dto.resourceId()) == null) {
            badRequest("[?? " + MasterDataValidationService.PR_RESOURCE_MISSING + "] ?????? " + dto.resourceId());
        }
        ProductResourceEntity existing = ProductResourceEntity.findByProductAndResource(
                dto.productCode(), dto.resourceId());
        if (dto.id() == null && existing != null) {
            badRequest("[?? " + MasterDataValidationService.PR_DUP + "] ???????? "
                    + dto.productCode() + " + " + dto.resourceId());
        }
        if (dto.id() != null && existing != null && !existing.id.equals(dto.id())) {
            badRequest("[?? " + MasterDataValidationService.PR_DUP + "] ?????????? "
                    + dto.productCode() + " + " + dto.resourceId());
        }
        ProductResourceEntity e = dto.id() != null
                ? findRequired(ProductResourceEntity.findById(dto.id()), "????")
                : new ProductResourceEntity();
        e.productCode = dto.productCode();
        e.resourceId = dto.resourceId();
        e.setupTimeMinutes = dto.setupTimeMinutes();
        e.sequenceNo = dto.sequenceNo();
        e.resourcePriority = dto.resourcePriority() != null
                ? dto.resourcePriority()
                : ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY;
        e.operationName = ProductResourceOperationNames.normalize(
                dto.operationName(), dto.resourceId(), dto.sequenceNo());
        e.processTimeSeconds = dto.processTimeSeconds();
        MasterDataExtensionService.applyProductResourceCustomFields(
                e,
                dto.extensions(),
                dto.bomLevel(),
                dto.wireMaterial(),
                dto.keyMaterial(),
                dto.maleFemaleEnd(),
                dto.totalBranch(),
                dto.standardLabor());
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toProductResourceDto(e);
    }

    @DELETE
    @Path("/product-resources/{id}")
    @Transactional
    public Response deleteProductResource(@PathParam("id") Long id) {
        boolean deleted = ProductResourceEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/product-resources/repair-operation-names")
    @Transactional
    public java.util.Map<String, Integer> repairProductResourceOperationNames() {
        int updated = 0;
        for (ProductResourceEntity row : ProductResourceEntity.listInWorkspace()) {
            String normalized = ProductResourceOperationNames.normalize(
                    row.operationName, row.resourceId, row.sequenceNo);
            if (normalized != null && !normalized.equals(row.operationName)) {
                row.operationName = normalized;
                updated++;
            }
        }
        return java.util.Map.of("updated", updated);
    }

    private static ProductResourceDto toProductResourceDto(ProductResourceEntity e) {
        var extensions = MasterDataExtensionService.readProductResourceExtensions(e);
        return new ProductResourceDto(
                e.id,
                e.productCode,
                e.resourceId,
                e.setupTimeMinutes,
                e.sequenceNo,
                e.resourcePriority != null ? e.resourcePriority : ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY,
                e.operationName,
                e.processTimeSeconds,
                MasterDataExtensionService.stringValue(extensions.get("bomLevel")),
                MasterDataExtensionService.stringValue(extensions.get("wireMaterial")),
                MasterDataExtensionService.stringValue(extensions.get("keyMaterial")),
                MasterDataExtensionService.stringValue(extensions.get("maleFemaleEnd")),
                MasterDataExtensionService.stringValue(extensions.get("totalBranch")),
                MasterDataExtensionService.decimalValue(extensions.get("standardLabor")),
                extensions);
    }

    // -------------------------- ?? --------------------------

    @GET
    @Path("/lines")
    public List<ProductionLineDto> listLines() {
        return ProductionLineEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ProductionLineEntity e) -> e.areaId)
                        .thenComparing(e -> e.lineId))
                .map(MasterDataResource::toLineDto)
                .toList();
    }

    @POST
    @Path("/lines")
    @Transactional
    public ProductionLineDto upsertLine(ProductionLineDto dto) {
        if (dto == null) {
            badRequest("body ????");
        }
        if (dto.lineId() == null || dto.lineId().isBlank()) {
            badRequest("lineId ????");
        }
        if (dto.areaId() == null || dto.areaId().isBlank()) {
            badRequest("areaId ????");
        }
        if (dto.resourceId() == null || dto.resourceId().isBlank()) {
            badRequest("resourceId ????");
        }
        if (ProductionResourceEntity.findByResourceId(dto.resourceId()) == null) {
            badRequest("?????????? " + dto.resourceId());
        }
        ProductionLineEntity e = dto.id() != null
                ? findRequired(ProductionLineEntity.findById(dto.id()), "??")
                : new ProductionLineEntity();
        e.lineId = dto.lineId();
        e.areaId = dto.areaId();
        e.resourceId = dto.resourceId();
        e.lineMinHeadcount = dto.lineMinHeadcount();
        e.lineCapacityPerShift = dto.lineCapacityPerShift();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toLineDto(e);
    }

    @DELETE
    @Path("/lines/{id}")
    @Transactional
    public Response deleteLine(@PathParam("id") Long id) {
        boolean deleted = ProductionLineEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ProductionLineDto toLineDto(ProductionLineEntity e) {
        return new ProductionLineDto(
                e.id, e.lineId, e.areaId, e.resourceId, e.lineMinHeadcount, e.lineCapacityPerShift);
    }

    // -------------------------- ???? --------------------------

    @GET
    @Path("/calendar")
    public List<ResourceCalendarDto> listCalendar() {
        return ResourceCalendarEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ResourceCalendarEntity e) -> e.resourceId)
                        .thenComparing(e -> e.calendarDate)
                        .thenComparing(e -> e.shiftId))
                .map(MasterDataResource::toCalendarDto)
                .toList();
    }

    @POST
    @Path("/calendar")
    @Transactional
    public ResourceCalendarDto upsertCalendar(ResourceCalendarDto dto) {
        ResourceCalendarEntity e = dto.id() != null
                ? findRequired(ResourceCalendarEntity.findById(dto.id()), "????")
                : new ResourceCalendarEntity();
        e.resourceId = dto.resourceId();
        e.shiftId = dto.shiftId();
        e.calendarDate = dto.calendarDate();
        e.availableCapacityMinutes = dto.availableCapacityMinutes();
        e.unavailableCapacityMinutes = dto.unavailableCapacityMinutes();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toCalendarDto(e);
    }

    @DELETE
    @Path("/calendar/{id}")
    @Transactional
    public Response deleteCalendar(@PathParam("id") Long id) {
        boolean deleted = ResourceCalendarEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ResourceCalendarDto toCalendarDto(ResourceCalendarEntity e) {
        return new ResourceCalendarDto(
                e.id, e.resourceId, e.shiftId, e.calendarDate,
                e.availableCapacityMinutes, e.unavailableCapacityMinutes);
    }

    // -------------------------- ???? --------------------------

    @GET
    @Path("/shift-headcount")
    public List<ShiftHeadcountDto> listShiftHeadcount() {
        return ShiftHeadcountEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ShiftHeadcountEntity e) -> e.areaId)
                        .thenComparing(e -> e.calendarDate)
                        .thenComparing(e -> e.shiftId))
                .map(MasterDataResource::toShiftHeadcountDto)
                .toList();
    }

    @POST
    @Path("/shift-headcount")
    @Transactional
    public ShiftHeadcountDto upsertShiftHeadcount(ShiftHeadcountDto dto) {
        ShiftHeadcountEntity e = dto.id() != null
                ? findRequired(ShiftHeadcountEntity.findById(dto.id()), "????")
                : new ShiftHeadcountEntity();
        e.areaId = dto.areaId();
        e.shiftId = dto.shiftId();
        e.calendarDate = dto.calendarDate();
        e.availableHeadcount = dto.availableHeadcount();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        return toShiftHeadcountDto(e);
    }

    @DELETE
    @Path("/shift-headcount/{id}")
    @Transactional
    public Response deleteShiftHeadcount(@PathParam("id") Long id) {
        boolean deleted = ShiftHeadcountEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ShiftHeadcountDto toShiftHeadcountDto(ShiftHeadcountEntity e) {
        return new ShiftHeadcountDto(
                e.id, e.areaId, e.shiftId, e.calendarDate, e.availableHeadcount);
    }

    // -------------------------- ???? --------------------------

    @GET
    @Path("/changeover")
    public List<ChangeoverDto> listChangeover() {
        return ChangeoverMatrixEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ChangeoverMatrixEntity e) -> e.operationName)
                        .thenComparing(e -> e.attributeKey)
                        .thenComparing(e -> e.fromAttributeValue)
                        .thenComparing(e -> e.toAttributeValue))
                .map(MasterDataResource::toChangeoverDto)
                .toList();
    }

    @POST
    @Path("/changeover")
    @Transactional
    public ChangeoverDto upsertChangeover(ChangeoverDto dto) {
        String operationName = requiredText(dto.operationName(), "工序");
        String attributeKey = com.plantops.scenario.ChangeoverAttributeKey.normalizeCode(
                requiredText(dto.attributeKey(), "属性"));
        String fromValue = com.plantops.scenario.ChangeoverAttributeKey.normalizeValue(dto.fromAttributeValue());
        String toValue = com.plantops.scenario.ChangeoverAttributeKey.normalizeValue(dto.toAttributeValue());
        ChangeoverMatrixEntity e = dto.id() != null
                ? findRequired(ChangeoverMatrixEntity.findById(dto.id()), "换型规则不存在")
                : ChangeoverMatrixEntity.findEntry(operationName, attributeKey, fromValue, toValue);
        if (e == null) {
            e = new ChangeoverMatrixEntity();
            e.ensureWorkspace();
        }
        e.operationName = operationName;
        e.attributeKey = attributeKey;
        e.fromAttributeValue = fromValue;
        e.toAttributeValue = toValue;
        e.setupMinutes = dto.setupMinutes();
        if (dto.id() == null && e.id == null) {
            e.persist();
        }
        return toChangeoverDto(e);
    }

    @DELETE
    @Path("/changeover/{id}")
    @Transactional
    public Response deleteChangeover(@PathParam("id") Long id) {
        boolean deleted = ChangeoverMatrixEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ChangeoverDto toChangeoverDto(ChangeoverMatrixEntity e) {
        return new ChangeoverDto(
                e.id,
                e.operationName,
                e.attributeKey,
                e.fromAttributeValue,
                e.toAttributeValue,
                e.setupMinutes);
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new jakarta.ws.rs.BadRequestException(label + "不能为空");
        }
        return value.trim();
    }

    // -------------------------- 并行工序规则 --------------------------

    @GET
    @Path("/parallel-operations")
    public List<ParallelOperationRuleDto> listParallelOperations() {
        return ParallelOperationRuleEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ParallelOperationRuleEntity e) -> e.lineId)
                        .thenComparing(e -> e.firstProductCode)
                        .thenComparing(e -> e.secondProductCode))
                .map(MasterDataResource::toParallelOperationDto)
                .toList();
    }

    @POST
    @Path("/parallel-operations")
    @Transactional
    public ParallelOperationRuleDto upsertParallelOperation(ParallelOperationRuleDto dto) {
        String lineId = requiredText(dto.lineId(), "产线");
        String first = requiredText(dto.firstProductCode(), "第一头料号");
        String second = requiredText(dto.secondProductCode(), "第二头料号");
        ParallelOperationRuleEntity e = dto.id() != null
                ? findRequired(ParallelOperationRuleEntity.findById(dto.id()), "并行工序规则不存在")
                : ParallelOperationRuleEntity.findEntry(lineId, first, second);
        if (e == null) {
            e = new ParallelOperationRuleEntity();
            e.ensureWorkspace();
        }
        e.lineId = lineId;
        e.firstProductCode = first;
        e.secondProductCode = second;
        if (dto.id() == null && e.id == null) {
            e.persist();
        }
        return toParallelOperationDto(e);
    }

    @DELETE
    @Path("/parallel-operations/{id}")
    @Transactional
    public Response deleteParallelOperation(@PathParam("id") Long id) {
        boolean deleted = ParallelOperationRuleEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ParallelOperationRuleDto toParallelOperationDto(ParallelOperationRuleEntity e) {
        return new ParallelOperationRuleDto(
                e.id, e.lineId, e.firstProductCode, e.secondProductCode);
    }

    // -------------------------- 工序流转时间 --------------------------

    @GET
    @Path("/operation-transfer-time")
    public List<OperationTransferTimeRuleDto> listOperationTransferTime() {
        return OperationTransferTimeRuleEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((OperationTransferTimeRuleEntity e) -> e.productCode)
                        .thenComparing(e -> e.fromOperationName)
                        .thenComparing(e -> e.toOperationName))
                .map(MasterDataResource::toOperationTransferTimeDto)
                .toList();
    }

    @POST
    @Path("/operation-transfer-time")
    @Transactional
    public OperationTransferTimeRuleDto upsertOperationTransferTime(OperationTransferTimeRuleDto dto) {
        String productCode = requiredText(dto.productCode(), "产品");
        String fromOp = requiredText(dto.fromOperationName(), "前工序");
        String toOp = requiredText(dto.toOperationName(), "后工序");
        if (fromOp.equals(toOp)) {
            throw new IllegalArgumentException("前工序与后工序不能相同");
        }
        if (dto.minTransferMinutes() < 0 || dto.maxTransferMinutes() < 0) {
            throw new IllegalArgumentException("流转时间不能为负");
        }
        int maxMinutes = dto.maxTransferMinutes() > 0 ? dto.maxTransferMinutes() : dto.transferMinutes();
        if (maxMinutes > 0 && maxMinutes <= dto.minTransferMinutes()) {
            throw new IllegalArgumentException("最大流转时间必须大于最小流转时间");
        }
        String linkMode = dto.linkMode() != null && !dto.linkMode().isBlank()
                ? dto.linkMode().trim().toUpperCase()
                : "STANDARD";
        com.plantops.scenario.OperationLinkMode.fromDb(linkMode);
        if ("DELAYED_START".equals(linkMode) && dto.delayStartMinutes() < 0) {
            throw new IllegalArgumentException("延后开始时间不能为负");
        }
        OperationTransferTimeRuleEntity e = dto.id() != null
                ? findRequired(OperationTransferTimeRuleEntity.findById(dto.id()), "工序流转时间规则不存在")
                : OperationTransferTimeRuleEntity.findEntry(productCode, fromOp, toOp);
        if (e == null) {
            e = new OperationTransferTimeRuleEntity();
            e.ensureWorkspace();
        }
        e.productCode = productCode;
        e.fromOperationName = fromOp;
        e.toOperationName = toOp;
        e.transferMinutes = maxMinutes;
        e.minTransferMinutes = dto.minTransferMinutes();
        e.maxTransferMinutes = maxMinutes;
        e.linkMode = linkMode;
        e.delayStartMinutes = Math.max(0, dto.delayStartMinutes());
        if (dto.id() == null && e.id == null) {
            e.persist();
        }
        return toOperationTransferTimeDto(e);
    }

    @DELETE
    @Path("/operation-transfer-time/{id}")
    @Transactional
    public Response deleteOperationTransferTime(@PathParam("id") Long id) {
        boolean deleted = OperationTransferTimeRuleEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static OperationTransferTimeRuleDto toOperationTransferTimeDto(OperationTransferTimeRuleEntity e) {
        int max = e.maxTransferMinutes > 0 ? e.maxTransferMinutes : e.transferMinutes;
        return new OperationTransferTimeRuleDto(
                e.id,
                e.productCode,
                e.fromOperationName,
                e.toOperationName,
                max,
                e.minTransferMinutes,
                max,
                e.linkMode != null ? e.linkMode : "STANDARD",
                e.delayStartMinutes);
    }

    // -------------------------- 工序后处理时间 --------------------------

    @GET
    @Path("/operation-post-processing")
    public List<OperationPostProcessingRuleDto> listOperationPostProcessing() {
        return OperationPostProcessingRuleEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((OperationPostProcessingRuleEntity e) -> e.productCode)
                        .thenComparing(e -> e.operationName))
                .map(MasterDataResource::toOperationPostProcessingDto)
                .toList();
    }

    @POST
    @Path("/operation-post-processing")
    @Transactional
    public OperationPostProcessingRuleDto upsertOperationPostProcessing(OperationPostProcessingRuleDto dto) {
        String productCode = requiredText(dto.productCode(), "产品");
        String operationName = dto.operationName() != null && !dto.operationName().isBlank()
                ? dto.operationName().trim()
                : "*";
        if (dto.postProcessingMinutes() < 0) {
            throw new IllegalArgumentException("后处理时间不能为负");
        }
        OperationPostProcessingRuleEntity e = dto.id() != null
                ? findRequired(OperationPostProcessingRuleEntity.findById(dto.id()), "工序后处理规则不存在")
                : OperationPostProcessingRuleEntity.findEntry(productCode, operationName);
        if (e == null) {
            e = new OperationPostProcessingRuleEntity();
            e.ensureWorkspace();
        }
        e.productCode = productCode;
        e.operationName = operationName;
        e.postProcessingMinutes = dto.postProcessingMinutes();
        e.persist();
        return toOperationPostProcessingDto(e);
    }

    @DELETE
    @Path("/operation-post-processing/{id}")
    @Transactional
    public Response deleteOperationPostProcessing(@PathParam("id") Long id) {
        boolean deleted = OperationPostProcessingRuleEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static OperationPostProcessingRuleDto toOperationPostProcessingDto(OperationPostProcessingRuleEntity e) {
        return new OperationPostProcessingRuleDto(
                e.id,
                e.productCode,
                e.operationName,
                e.postProcessingMinutes);
    }

    // -------------------------- 采购提前期 --------------------------

    @GET
    @Path("/material-lead-time")
    public List<MaterialLeadTimeRuleDto> listMaterialLeadTime() {
        return MaterialLeadTimeRuleEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing((MaterialLeadTimeRuleEntity e) -> e.productCode))
                .map(MasterDataResource::toMaterialLeadTimeDto)
                .toList();
    }

    @POST
    @Path("/material-lead-time")
    @Transactional
    public MaterialLeadTimeRuleDto upsertMaterialLeadTime(MaterialLeadTimeRuleDto dto) {
        String productCode = requiredText(dto.productCode(), "物料");
        if (dto.leadTimeDays() < 0) {
            throw new IllegalArgumentException("采购提前期不能为负");
        }
        MaterialLeadTimeRuleEntity e = dto.id() != null
                ? findRequired(MaterialLeadTimeRuleEntity.findById(dto.id()), "采购提前期规则不存在")
                : MaterialLeadTimeRuleEntity.findByProduct(productCode);
        if (e == null) {
            e = new MaterialLeadTimeRuleEntity();
            e.ensureWorkspace();
        }
        e.productCode = productCode;
        e.leadTimeDays = dto.leadTimeDays();
        e.persist();
        return toMaterialLeadTimeDto(e);
    }

    @DELETE
    @Path("/material-lead-time/{id}")
    @Transactional
    public Response deleteMaterialLeadTime(@PathParam("id") Long id) {
        boolean deleted = MaterialLeadTimeRuleEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static MaterialLeadTimeRuleDto toMaterialLeadTimeDto(MaterialLeadTimeRuleEntity e) {
        return new MaterialLeadTimeRuleDto(e.id, e.productCode, e.leadTimeDays);
    }

    // -------------------------- 连续生产 --------------------------

    @GET
    @Path("/continuous-production")
    public List<ContinuousProductionRuleDto> listContinuousProduction() {
        return ContinuousProductionRuleEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparing((ContinuousProductionRuleEntity e) -> e.lineId)
                        .thenComparing(e -> e.firstProductCode)
                        .thenComparing(e -> e.secondProductCode)
                        .thenComparing(e -> e.finishedProductCode))
                .map(MasterDataResource::toContinuousProductionDto)
                .toList();
    }

    @POST
    @Path("/continuous-production")
    @Transactional
    public ContinuousProductionRuleDto upsertContinuousProduction(ContinuousProductionRuleDto dto) {
        String lineId = requiredText(dto.lineId(), "机台");
        String first = ContinuousProductionRuleEntity.normalizeCode(dto.firstProductCode());
        String second = ContinuousProductionRuleEntity.normalizeCode(dto.secondProductCode());
        String finished = ContinuousProductionRuleEntity.normalizeCode(dto.finishedProductCode());
        if (first.isBlank() && second.isBlank() && finished.isBlank()) {
            throw new IllegalArgumentException("至少填写一个料号（第一头/第二头/成品）");
        }
        ContinuousProductionRuleEntity e = dto.id() != null
                ? findRequired(ContinuousProductionRuleEntity.findById(dto.id()), "连续生产规则不存在")
                : ContinuousProductionRuleEntity.findEntry(lineId, first, second, finished);
        if (e == null) {
            e = new ContinuousProductionRuleEntity();
            e.ensureWorkspace();
        }
        e.lineId = lineId;
        e.firstProductCode = first;
        e.secondProductCode = second;
        e.finishedProductCode = finished;
        if (dto.id() == null && e.id == null) {
            e.persist();
        }
        return toContinuousProductionDto(e);
    }

    @DELETE
    @Path("/continuous-production/{id}")
    @Transactional
    public Response deleteContinuousProduction(@PathParam("id") Long id) {
        boolean deleted = ContinuousProductionRuleEntity.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private static ContinuousProductionRuleDto toContinuousProductionDto(ContinuousProductionRuleEntity e) {
        return new ContinuousProductionRuleDto(
                e.id,
                e.lineId,
                e.firstProductCode,
                e.secondProductCode,
                e.finishedProductCode);
    }

    // ---------- 兼容旧版 /u-line-pairs ----------

    @GET
    @Path("/u-line-pairs")
    public List<ULinePairDto> listULinePairs() {
        return listParallelOperations().stream().map(ULinePairDto::from).toList();
    }

    @POST
    @Path("/u-line-pairs")
    @Transactional
    public ULinePairDto upsertULinePair(ULinePairDto dto) {
        return ULinePairDto.from(upsertParallelOperation(dto.toParallelOperationRuleDto()));
    }

    @DELETE
    @Path("/u-line-pairs/{id}")
    @Transactional
    public Response deleteULinePair(@PathParam("id") Long id) {
        return deleteParallelOperation(id);
    }

    // -------------------------- 系统参数 --------------------------

    @GET
    @Path("/parameters")
    public List<SystemParameterDto> listParameters() {
        return SystemParameterEntity.listInWorkspace().stream()
                .sorted(Comparator.comparing((SystemParameterEntity e) -> e.paramId))
                .map(MasterDataResource::toParameterDto)
                .toList();
    }

    @POST
    @Path("/parameters")
    @Transactional
    public SystemParameterDto upsertParameter(SystemParameterDto dto) {
        SystemParameterEntity e = dto.id() != null
                ? findRequired(SystemParameterEntity.findById(dto.id()), "????")
                : new SystemParameterEntity();
        e.paramId = dto.paramId();
        e.paramValue = dto.paramValue();
        e.description = dto.description();
        if (dto.id() == null) {
            e.ensureWorkspace();
            e.persist();
        }
        parameterRegistry.invalidate(dto.paramId());
        syncCalendarsIfHorizonChanged(dto.paramId());
        return toParameterDto(e);
    }

    @DELETE
    @Path("/parameters/{id}")
    @Transactional
    public Response deleteParameter(@PathParam("id") Long id) {
        SystemParameterEntity entity = SystemParameterEntity.findById(id);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String paramId = entity.paramId;
        entity.delete();
        parameterRegistry.invalidate(paramId);
        return Response.noContent().build();
    }

    private static SystemParameterDto toParameterDto(SystemParameterEntity e) {
        return new SystemParameterDto(e.id, e.paramId, e.paramValue, e.description);
    }

    // -------------------------- ???? --------------------------

    @PUT
    @Path("/parameters/{paramId}/value")
    @Transactional
    public SystemParameterDto setParameterValue(@PathParam("paramId") String paramId, ParameterValuePayload payload) {
        SystemParameterEntity e = SystemParameterEntity.findByParamId(paramId);
        if (e == null) {
            e = new SystemParameterEntity();
            e.paramId = paramId;
            e.paramValue = payload.value();
            e.description = payload.description();
            e.ensureWorkspace();
            e.persist();
        } else {
            e.paramValue = payload.value();
            if (payload.description() != null) {
                e.description = payload.description();
            }
        }
        parameterRegistry.invalidate(paramId);
        syncCalendarsIfHorizonChanged(paramId);
        return toParameterDto(e);
    }

    private void syncCalendarsIfHorizonChanged(String paramId) {
        if (paramId == null) {
            return;
        }
        if ("planning_horizon_days".equals(paramId)
                || "timeslot_granularity_mode".equals(paramId)
                || "timeslot_daily_days".equals(paramId)
                || "timeslot_weekly_buckets".equals(paramId)) {
            sampleDataLoader.extendCalendarsToHorizon();
            factoryCalendarService.syncResourceCalendarsToHorizon();
        }
    }

    // -------------------------- 规则项目启用范围 --------------------------

    @GET
    @Path("/business-rule-scopes")
    public List<BusinessRuleScopeDto> listBusinessRuleScopes() {
        return businessRuleScopeService.listAll();
    }

    @PUT
    @Path("/business-rule-scopes/{ruleTypeId}")
    @Transactional
    public BusinessRuleScopeDto upsertBusinessRuleScope(
            @PathParam("ruleTypeId") String ruleTypeId,
            BusinessRuleScopeDto dto) {
        return businessRuleScopeService.upsert(new BusinessRuleScopeDto(
                ruleTypeId,
                dto.label() != null ? dto.label() : BusinessRuleTypeIds.labelOf(ruleTypeId),
                dto.enableMasterPlan(),
                dto.enableDetailSchedule(),
                dto.description()));
    }

    public record ParameterValuePayload(String value, String description) {
    }

    private static <T> T findRequired(T entity, String label) {
        if (entity == null) {
            throw new WebApplicationException(label + " 不存在", Response.Status.NOT_FOUND);
        }
        return entity;
    }

    private static void badRequest(String message) {
        throw new WebApplicationException(message, Response.Status.BAD_REQUEST);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
