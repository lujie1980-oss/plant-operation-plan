package com.plantops.integration.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterConfigDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterRunResultDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationAdapterStatusDto;
import com.plantops.api.dto.integration.IntegrationDtos.IntegrationExcelUploadResultDto;
import com.plantops.iam.module.ModuleAuthorizationService;
import com.plantops.iam.module.WorkspaceModuleCatalog;
import com.plantops.integration.IntegrationAdapterPort;
import com.plantops.integration.excel.IntegrationExcelImportService;
import com.plantops.masterdata.external.MasterDataExternalImportService;
import com.plantops.persistence.entity.WorkspaceAdapterConfigEntity;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** API-INT-04/05/06 适配器编排。 */
@ApplicationScoped
public class IntegrationAdapterService {

    @Inject
    Instance<IntegrationAdapterPort> adapters;

    @Inject
    ModuleAuthorizationService moduleAuthorizationService;

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    IntegrationExcelImportService excelImportService;

    @Inject
    MasterDataExternalImportService masterDataImportService;

    @Inject
    ObjectMapper objectMapper;

    public List<IntegrationAdapterStatusDto> listAdapters() {
        String workspaceId = workspaceContext.getWorkspaceId();
        List<IntegrationAdapterStatusDto> result = new ArrayList<>();
        for (WorkspaceModuleCatalog.AdapterDef def : WorkspaceModuleCatalog.ADAPTERS) {
            WorkspaceAdapterConfigEntity config = WorkspaceAdapterConfigEntity.findByKey(workspaceId, def.id());
            result.add(new IntegrationAdapterStatusDto(
                    def.id(),
                    def.name(),
                    moduleAuthorizationService.isAdapterEnabled(workspaceId, def.id()),
                    config != null && config.configured,
                    config != null ? config.lastRunAt : null,
                    config != null ? config.lastStatus : null,
                    config != null ? config.lastMessage : null));
        }
        return result;
    }

    @Transactional
    public IntegrationAdapterRunResultDto runAdapter(String adapterId) {
        String resolved = resolveAdapterId(adapterId);
        IntegrationAdapterPort adapter = requireAdapter(resolved);
        IntegrationAdapterRunResultDto result = adapter.run(false);
        recordRun(resolved, result);
        return result;
    }

    @Transactional
    public IntegrationExcelUploadResultDto uploadExcel(InputStream inputStream, boolean validateOnly) {
        try {
            var bundle = excelImportService.parseStockingPoints(inputStream);
            int rowCount = bundle.stockingPoints().size();
            if (validateOnly) {
                IntegrationAdapterRunResultDto result =
                        new IntegrationAdapterRunResultDto(null, "SUCCESS", "校验通过：" + rowCount + " 行库存点");
                recordRun("ADP-EXCEL", result);
                return new IntegrationExcelUploadResultDto(null, rowCount, "SUCCESS", result.message());
            }
            var importResult = masterDataImportService.importRoutingBundle(bundle);
            IntegrationAdapterRunResultDto result = new IntegrationAdapterRunResultDto(
                    importResult.importBatchId(), "SUCCESS", "导入 " + importResult.rowCount() + " 行");
            recordRun("ADP-EXCEL", result);
            return new IntegrationExcelUploadResultDto(
                    importResult.importBatchId(), importResult.rowCount(), "SUCCESS", result.message());
        } catch (Exception e) {
            IntegrationAdapterRunResultDto result =
                    new IntegrationAdapterRunResultDto(null, "FAILED", e.getMessage());
            recordRun("ADP-EXCEL", result);
            throw new BadRequestException(e.getMessage());
        }
    }

    public IntegrationAdapterConfigDto getConfig(String adapterId) {
        WorkspaceAdapterConfigEntity config =
                WorkspaceAdapterConfigEntity.findByKey(workspaceContext.getWorkspaceId(), adapterId);
        if (config == null || config.configJson == null) {
            return new IntegrationAdapterConfigDto(Map.of());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(config.configJson, Map.class);
            return new IntegrationAdapterConfigDto(map);
        } catch (JsonProcessingException e) {
            return new IntegrationAdapterConfigDto(Map.of());
        }
    }

    @Transactional
    public IntegrationAdapterConfigDto saveConfig(String adapterId, IntegrationAdapterConfigDto dto) {
        String resolved = resolveAdapterId(adapterId);
        if (!WorkspaceModuleCatalog.KNOWN_ADAPTER_IDS.contains(resolved)) {
            throw new NotFoundException("Unknown adapter: " + adapterId);
        }
        Map<String, String> config = dto.config() != null ? new HashMap<>(dto.config()) : new HashMap<>();
        config.remove("password");
        config.remove("secret");
        WorkspaceAdapterConfigEntity entity =
                WorkspaceAdapterConfigEntity.findByKey(workspaceContext.getWorkspaceId(), resolved);
        if (entity == null) {
            entity = new WorkspaceAdapterConfigEntity();
            entity.workspaceId = workspaceContext.getWorkspaceId();
            entity.adapterId = resolved;
        }
        try {
            entity.configJson = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("config 无效");
        }
        entity.configured = isConfigured(resolved, config);
        entity.persist();
        return new IntegrationAdapterConfigDto(config);
    }

    private void recordRun(String adapterId, IntegrationAdapterRunResultDto result) {
        WorkspaceAdapterConfigEntity entity =
                WorkspaceAdapterConfigEntity.findByKey(workspaceContext.getWorkspaceId(), adapterId);
        if (entity == null) {
            entity = new WorkspaceAdapterConfigEntity();
            entity.workspaceId = workspaceContext.getWorkspaceId();
            entity.adapterId = adapterId;
            entity.configured = "ADP-EXCEL".equals(adapterId);
        }
        entity.lastRunAt = LocalDateTime.now();
        entity.lastStatus = result.status();
        entity.lastMessage = result.message();
        entity.persist();
    }

    private IntegrationAdapterPort requireAdapter(String adapterId) {
        String resolved = resolveAdapterId(adapterId);
        for (IntegrationAdapterPort adapter : adapters) {
            if (adapter.adapterId().equals(resolved)) {
                return adapter;
            }
        }
        throw new NotFoundException("Unknown adapter: " + adapterId);
    }

    static String resolveAdapterId(String adapterIdOrSlug) {
        return switch (adapterIdOrSlug) {
            case "erp-sap", "ADP-ERP-SAP" -> "ADP-ERP-SAP";
            case "mes", "ADP-MES" -> "ADP-MES";
            case "excel", "ADP-EXCEL" -> "ADP-EXCEL";
            default -> adapterIdOrSlug;
        };
    }

    private static boolean isConfigured(String adapterId, Map<String, String> config) {
        return switch (adapterId) {
            case "ADP-EXCEL" -> true;
            case "ADP-ERP-SAP" -> config.containsKey("connectionUrl") && config.containsKey("credentialRef");
            case "ADP-MES" -> config.containsKey("baseUrl") && config.containsKey("credentialRef");
            default -> !config.isEmpty();
        };
    }
}
