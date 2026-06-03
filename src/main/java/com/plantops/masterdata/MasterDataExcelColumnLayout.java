package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionDto;
import com.plantops.masterdata.MasterDataExcelSheet.ColumnDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * 主数据 Excel：General 固定列 + 字段目录 Custom 列（按 workspace）。
 */
@ApplicationScoped
public class MasterDataExcelColumnLayout {

    @Inject
    MasterFieldDefinitionService fieldDefinitionService;

    public List<ColumnDef> resolve(MasterDataExcelSheet sheet) {
        List<ColumnDef> columns = new ArrayList<>(sheet.baseColumns());
        if (sheet.extensionEntityType() == null) {
            return columns;
        }
        String entityType = sheet.extensionEntityType().code();
        fieldDefinitionService.ensureDefaultsForCurrentWorkspace(entityType);
        for (MasterFieldDefinitionDto field : fieldDefinitionService.listSchema(entityType)) {
            if (!MasterFieldCategory.CUSTOM.code().equals(field.fieldCategory())) {
                continue;
            }
            if (!field.visibleInGrid()) {
                continue;
            }
            columns.add(new ColumnDef(field.fieldKey(), field.labelZh(), field.dataType(), true));
        }
        return columns;
    }
}
