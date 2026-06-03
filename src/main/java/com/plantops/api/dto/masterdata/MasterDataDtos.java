package com.plantops.api.dto.masterdata;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 计划主数据维护所需的 DTO 集合。
 * 所有 DTO 携带可空 id：null 视为新增，非空视为更新。
 */
public final class MasterDataDtos {

    private MasterDataDtos() {
    }

    public record SalesOrderDto(
            Long id,
            String salesOrderNo,
            int salesOrderLineNo,
            String customerCode,
            String productCode,
            BigDecimal orderQty,
            String uom,
            LocalDate promiseDate,
            LocalDate dueDate,
            int priority,
            int expediteLevel,
            String status,
            boolean scheduleLockFlag
    ) {
    }

    public record BomDto(
            Long id,
            String finishedProductCode,
            String bomId,
            String bomVersion,
            String parentProductCode,
            String componentProductCode,
            BigDecimal componentQty,
            boolean isCriticalComponent,
            LocalDate bomEffectiveFrom,
            LocalDate bomEffectiveTo,
            LocalDate componentEffectiveFrom,
            LocalDate componentEffectiveTo,
            BigDecimal scrapRate,
            BigDecimal lotSize,
            BigDecimal lotSizeMultiple
    ) {
    }

    public record MaterialDto(
            Long id,
            String siteCode,
            String materialCode,
            String materialName,
            String uomCode,
            String materialType,
            Map<String, Object> extensions
    ) {
    }

    public record InventoryDto(
            Long id,
            String stockingPointCode,
            String productCode,
            BigDecimal onhandQty,
            BigDecimal reservedQty,
            BigDecimal qualityHoldQty,
            BigDecimal inTransitQty
    ) {
    }

    public record ResourceDto(
            Long id,
            String resourceId,
            String resourceGroup,
            String areaId,
            boolean bottleneck,
            BigDecimal runRatePerHour
    ) {
    }

    public record ProductResourceDto(
            Long id,
            String productCode,
            String resourceId,
            int setupTimeMinutes,
            Integer sequenceNo,
            Integer resourcePriority,
            String operationName,
            BigDecimal processTimeSeconds,
            String bomLevel,
            String wireMaterial,
            String keyMaterial,
            String maleFemaleEnd,
            String totalBranch,
            BigDecimal standardLabor,
            Map<String, Object> extensions
    ) {
    }

    /** 主数据字段目录项（General / Custom） */
    public record MasterFieldDefinitionDto(
            Long id,
            String entityType,
            String fieldKey,
            String fieldCategory,
            String dataType,
            String labelZh,
            boolean required,
            boolean visibleInGrid,
            boolean usedInRules,
            int displayOrder,
            String source
    ) {
    }

    /** 新增 workspace 级 Custom 字段 */
    public record MasterFieldDefinitionCreateDto(
            String entityType,
            String fieldKey,
            String dataType,
            String labelZh,
            boolean required,
            boolean visibleInGrid,
            boolean usedInRules,
            int displayOrder
    ) {
    }

    /** 更新字段目录（不可改 fieldKey / entityType） */
    public record MasterFieldDefinitionUpdateDto(
            String dataType,
            String labelZh,
            boolean required,
            boolean visibleInGrid,
            boolean usedInRules,
            int displayOrder
    ) {
    }

    public record ProductionLineDto(
            Long id,
            String lineId,
            String areaId,
            String resourceId,
            int lineMinHeadcount,
            int lineCapacityPerShift
    ) {
    }

    public record ResourceCalendarDto(
            Long id,
            String resourceId,
            String shiftId,
            LocalDate calendarDate,
            int availableCapacityMinutes,
            int unavailableCapacityMinutes
    ) {
    }

    public record ShiftHeadcountDto(
            Long id,
            String areaId,
            String shiftId,
            LocalDate calendarDate,
            int availableHeadcount
    ) {
    }

    public record ChangeoverDto(
            Long id,
            String operationName,
            String attributeKey,
            String fromAttributeValue,
            String toAttributeValue,
            int setupMinutes
    ) {
    }

    public record ParallelOperationRuleDto(
            Long id,
            String lineId,
            String firstProductCode,
            String secondProductCode
    ) {
    }

    public record OperationTransferTimeRuleDto(
            Long id,
            String productCode,
            String fromOperationName,
            String toOperationName,
            int transferMinutes,
            int minTransferMinutes,
            int maxTransferMinutes,
            String linkMode,
            int delayStartMinutes
    ) {
    }

    public record OperationPostProcessingRuleDto(
            Long id,
            String productCode,
            String operationName,
            int postProcessingMinutes
    ) {
    }

    public record MaterialLeadTimeRuleDto(
            Long id,
            String productCode,
            int leadTimeDays
    ) {
    }

    public record ContinuousProductionRuleDto(
            Long id,
            String lineId,
            String firstProductCode,
            String secondProductCode,
            String finishedProductCode
    ) {
    }

    /**
     * 旧版 /u-line-pairs 请求与响应体；{@code resourceId} 与 {@code lineId} 均表示产线 ID。
     */
    public record ULinePairDto(
            Long id,
            @JsonAlias("lineId") String resourceId,
            String firstProductCode,
            String secondProductCode
    ) {
        public ParallelOperationRuleDto toParallelOperationRuleDto() {
            return new ParallelOperationRuleDto(id, resourceId, firstProductCode, secondProductCode);
        }

        public static ULinePairDto from(ParallelOperationRuleDto dto) {
            return new ULinePairDto(dto.id(), dto.lineId(), dto.firstProductCode(), dto.secondProductCode());
        }
    }

    public record SystemParameterDto(
            Long id,
            String paramId,
            String paramValue,
            String description
    ) {
    }

    /** 规则项目级启用范围（主计划 / 详细排程） */
    public record BusinessRuleScopeDto(
            String ruleTypeId,
            String label,
            boolean enableMasterPlan,
            boolean enableDetailSchedule,
            String description
    ) {
    }
}
