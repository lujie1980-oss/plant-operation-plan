package com.plantops.masterdata;

import java.util.List;

enum MasterDataExcelSheet {
    MATERIALS(
            "物料",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("siteCode", "基地代码"),
                    col("materialCode", "物料编码"),
                    col("materialName", "物料名称"),
                    col("uomCode", "主计量单位代码"),
                    col("materialType", "物料类型")),
            MasterFieldEntityType.MATERIAL),
    BOM(
            "BOM",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("finishedProductCode", "成品料号"),
                    col("bomId", "BOM ID"),
                    col("bomVersion", "版本"),
                    col("parentProductCode", "父产品(产品代码)"),
                    col("componentProductCode", "组件产品"),
                    col("componentQty", "用量"),
                    col("isCriticalComponent", "关键件(是/否)"),
                    col("bomEffectiveFrom", "BOM生效(yyyy-MM-dd)"),
                    col("bomEffectiveTo", "BOM失效(yyyy-MM-dd)"),
                    col("componentEffectiveFrom", "组件生效(yyyy-MM-dd)"),
                    col("componentEffectiveTo", "组件失效(yyyy-MM-dd)"),
                    col("scrapRate", "组件损耗率"),
                    col("lotSize", "批量"),
                    col("lotSizeMultiple", "批量倍数")),
            null),
    RESOURCES(
            "生产资源",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("resourceId", "资源 ID"),
                    col("resourceGroup", "资源组"),
                    col("areaId", "区域"),
                    col("bottleneck", "瓶颈(是/否)"),
                    col("runRatePerHour", "小时产能")),
            null),
    PRODUCT_RESOURCES(
            "产品工艺",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("productCode", "产品"),
                    col("sequenceNo", "工序号"),
                    col("resourcePriority", "资源优先级(小=高)"),
                    col("operationName", "工序名称"),
                    col("resourceId", "资源"),
                    col("setupTimeMinutes", "换型(分钟)"),
                    col("processTimeSeconds", "单件加工(秒)")),
            MasterFieldEntityType.PRODUCT_RESOURCE),
    LINES(
            "产线",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("lineId", "产线 ID"),
                    col("areaId", "区域"),
                    col("resourceId", "关联资源"),
                    col("lineMinHeadcount", "最小人数"),
                    col("lineCapacityPerShift", "每班产能(分钟)")),
            null),
    CALENDAR(
            "资源日历",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("resourceId", "资源"),
                    col("calendarDate", "日期(yyyy-MM-dd)"),
                    col("shiftId", "班次"),
                    col("availableCapacityMinutes", "可用(分钟)"),
                    col("unavailableCapacityMinutes", "不可用(分钟)")),
            null),
    SHIFT_HEADCOUNT(
            "班次人员",
            List.of(
                    col("id", "系统ID(留空=新增)"),
                    col("areaId", "区域"),
                    col("calendarDate", "日期(yyyy-MM-dd)"),
                    col("shiftId", "班次"),
                    col("availableHeadcount", "可用人数")),
            null);

    final String sheetName;
    final List<ColumnDef> baseColumns;
    final MasterFieldEntityType extensionEntityType;

    MasterDataExcelSheet(
            String sheetName,
            List<ColumnDef> baseColumns,
            MasterFieldEntityType extensionEntityType) {
        this.sheetName = sheetName;
        this.baseColumns = baseColumns;
        this.extensionEntityType = extensionEntityType;
    }

    /** @deprecated 使用 {@link #baseColumns()}；保留兼容旧引用 */
    @Deprecated
    List<ColumnDef> columns() {
        return baseColumns;
    }

    List<ColumnDef> baseColumns() {
        return baseColumns;
    }

    MasterFieldEntityType extensionEntityType() {
        return extensionEntityType;
    }

    private static ColumnDef col(String field, String header) {
        return new ColumnDef(field, header, null, false);
    }

    record ColumnDef(String field, String header, String dataType, boolean custom) {
        ColumnDef(String field, String header) {
            this(field, header, null, false);
        }
    }
}
