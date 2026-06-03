package com.plantops.masterdata;

import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionCreateDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionDto;
import com.plantops.api.dto.masterdata.MasterDataDtos.MasterFieldDefinitionUpdateDto;
import com.plantops.persistence.entity.MasterFieldDefinitionEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceConstants;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class MasterFieldDefinitionService {

    private static final Pattern FIELD_KEY = Pattern.compile("^[a-z][a-zA-Z0-9]{0,63}$");

    @Inject
    WorkspaceContext workspaceContext;

    public List<MasterFieldDefinitionDto> listSchema(String entityType) {
        String normalized = normalizeEntityType(entityType);
        ensureDefaultsForCurrentWorkspace(normalized);
        return MasterFieldDefinitionEntity.listByEntityType(normalized).stream()
                .map(MasterFieldDefinitionService::toDto)
                .toList();
    }

    @Transactional
    public MasterFieldDefinitionDto createCustom(MasterFieldDefinitionCreateDto dto) {
        if (dto == null) {
            throw new BadRequestException("body 不能为空");
        }
        String entityType = normalizeEntityType(dto.entityType());
        validateFieldKey(dto.fieldKey());
        validateDataType(dto.dataType());
        if (dto.labelZh() == null || dto.labelZh().isBlank()) {
            throw new BadRequestException("labelZh 不能为空");
        }
        if (MasterFieldDefinitionEntity.findByEntityAndKey(entityType, dto.fieldKey().trim()) != null) {
            throw new BadRequestException("字段键已存在: " + dto.fieldKey());
        }
        MasterFieldDefinitionEntity e = new MasterFieldDefinitionEntity();
        e.entityType = entityType;
        e.fieldKey = dto.fieldKey().trim();
        e.fieldCategory = MasterFieldCategory.CUSTOM.code();
        e.dataType = dto.dataType().trim().toUpperCase(Locale.ROOT);
        e.labelZh = dto.labelZh().trim();
        e.required = dto.required();
        e.visibleInGrid = dto.visibleInGrid();
        e.usedInRules = dto.usedInRules();
        e.displayOrder = dto.displayOrder();
        e.source = "WORKSPACE";
        e.ensureWorkspace();
        e.persist();
        return toDto(e);
    }

    @Transactional
    public MasterFieldDefinitionDto update(Long id, MasterFieldDefinitionUpdateDto dto) {
        if (dto == null) {
            throw new BadRequestException("body 不能为空");
        }
        MasterFieldDefinitionEntity e = findRequired(id);
        validateDataType(dto.dataType());
        if (dto.labelZh() == null || dto.labelZh().isBlank()) {
            throw new BadRequestException("labelZh 不能为空");
        }
        e.dataType = dto.dataType().trim().toUpperCase(Locale.ROOT);
        e.labelZh = dto.labelZh().trim();
        e.required = dto.required();
        e.visibleInGrid = dto.visibleInGrid();
        e.usedInRules = dto.usedInRules();
        e.displayOrder = dto.displayOrder();
        return toDto(e);
    }

    @Transactional
    public void delete(Long id) {
        MasterFieldDefinitionEntity e = findRequired(id);
        if (!"WORKSPACE".equals(e.source)) {
            throw new BadRequestException("平台预置字段不可删除");
        }
        e.delete();
    }

    @Transactional
    public void ensureDefaultsForCurrentWorkspace(String entityType) {
        if (MasterFieldEntityType.PRODUCT_RESOURCE.code().equals(entityType)
                && !MasterFieldDefinitionEntity.existsForEntity(entityType)) {
            seedProductResourceDefaults();
        }
    }

    @Transactional
    public void ensureDefaultsForAllWorkspaces() {
        for (WorkspaceEntity workspace : WorkspaceEntity.listAllOrdered()) {
            String prev = workspaceContext.getWorkspaceId();
            try {
                workspaceContext.setWorkspaceId(workspace.workspaceId);
                ensureDefaultsForCurrentWorkspace(MasterFieldEntityType.PRODUCT_RESOURCE.code());
            } finally {
                workspaceContext.setWorkspaceId(prev);
            }
        }
    }

    @Transactional
    public void cloneDefaultsFromDefaultWorkspace(String targetWorkspaceId) {
        String prev = workspaceContext.getWorkspaceId();
        try {
            workspaceContext.setWorkspaceId(WorkspaceConstants.DEFAULT_ID);
            List<String> entityTypes = MasterFieldDefinitionEntity.list("1=1").stream()
                    .map(e -> ((MasterFieldDefinitionEntity) e).entityType)
                    .distinct()
                    .toList();
            workspaceContext.setWorkspaceId(targetWorkspaceId);
            for (String entityType : entityTypes) {
                if (MasterFieldDefinitionEntity.existsForEntity(entityType)) {
                    continue;
                }
                workspaceContext.setWorkspaceId(WorkspaceConstants.DEFAULT_ID);
                List<MasterFieldDefinitionEntity> source = MasterFieldDefinitionEntity.listByEntityType(entityType);
                workspaceContext.setWorkspaceId(targetWorkspaceId);
                for (MasterFieldDefinitionEntity row : source) {
                    MasterFieldDefinitionEntity copy = new MasterFieldDefinitionEntity();
                    copy.entityType = row.entityType;
                    copy.fieldKey = row.fieldKey;
                    copy.fieldCategory = row.fieldCategory;
                    copy.dataType = row.dataType;
                    copy.labelZh = row.labelZh;
                    copy.required = row.required;
                    copy.visibleInGrid = row.visibleInGrid;
                    copy.usedInRules = row.usedInRules;
                    copy.displayOrder = row.displayOrder;
                    copy.source = row.source;
                    copy.workspaceId = targetWorkspaceId;
                    copy.persist();
                }
            }
        } finally {
            workspaceContext.setWorkspaceId(prev);
        }
    }

    private MasterFieldDefinitionEntity findRequired(Long id) {
        MasterFieldDefinitionEntity e = MasterFieldDefinitionEntity.findById(id);
        if (e == null || !workspaceContext.getWorkspaceId().equals(e.workspaceId)) {
            throw new NotFoundException("字段定义不存在");
        }
        return e;
    }

    private static String normalizeEntityType(String entityType) {
        try {
            return MasterFieldEntityType.parse(entityType).code();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("不支持的 entityType: " + entityType
                    + "；可选: " + Arrays.toString(MasterFieldEntityType.values()));
        }
    }

    private static void validateFieldKey(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new BadRequestException("fieldKey 不能为空");
        }
        if (!FIELD_KEY.matcher(fieldKey.trim()).matches()) {
            throw new BadRequestException("fieldKey 须为 camelCase，如 wireMaterial");
        }
    }

    private static void validateDataType(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            throw new BadRequestException("dataType 不能为空");
        }
        try {
            MasterFieldDataType.valueOf(dataType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("不支持的 dataType: " + dataType);
        }
    }

    private void seedProductResourceDefaults() {
        List<SeedField> seeds = List.of(
                seed("bomLevel", "A/B料", MasterFieldDataType.STRING, 10, false),
                seed("wireMaterial", "线材", MasterFieldDataType.STRING, 20, true),
                seed("keyMaterial", "关键物料", MasterFieldDataType.STRING, 30, true),
                seed("maleFemaleEnd", "公母端", MasterFieldDataType.STRING, 40, false),
                seed("totalBranch", "总成分支", MasterFieldDataType.STRING, 50, true),
                seed("standardLabor", "制造人力", MasterFieldDataType.NUMBER, 60, false));
        for (SeedField seed : seeds) {
            MasterFieldDefinitionEntity e = new MasterFieldDefinitionEntity();
            e.entityType = MasterFieldEntityType.PRODUCT_RESOURCE.code();
            e.fieldKey = seed.fieldKey;
            e.fieldCategory = MasterFieldCategory.CUSTOM.code();
            e.dataType = seed.dataType.code();
            e.labelZh = seed.labelZh;
            e.usedInRules = seed.usedInRules;
            e.displayOrder = seed.displayOrder;
            e.source = "PLATFORM";
            e.ensureWorkspace();
            e.persist();
        }
    }

    private static SeedField seed(
            String fieldKey,
            String labelZh,
            MasterFieldDataType dataType,
            int displayOrder,
            boolean usedInRules) {
        return new SeedField(fieldKey, labelZh, dataType, displayOrder, usedInRules);
    }

    private static MasterFieldDefinitionDto toDto(MasterFieldDefinitionEntity e) {
        return new MasterFieldDefinitionDto(
                e.id,
                e.entityType,
                e.fieldKey,
                e.fieldCategory,
                e.dataType,
                e.labelZh,
                e.required,
                e.visibleInGrid,
                e.usedInRules,
                e.displayOrder,
                e.source);
    }

    private record SeedField(
            String fieldKey,
            String labelZh,
            MasterFieldDataType dataType,
            int displayOrder,
            boolean usedInRules) {
    }
}
