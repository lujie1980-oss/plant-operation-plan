package com.plantops.iam.module;

import com.plantops.iam.entity.WorkspaceEnabledAdapterEntity;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.module.WorkspaceModuleCatalog.MatchMode;
import com.plantops.iam.module.WorkspaceModuleCatalog.PathRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ModuleAuthorizationService {

    public boolean isModuleEnabled(String workspaceId, String moduleId) {
        WorkspaceEnabledModuleEntity row = WorkspaceEnabledModuleEntity.find(
                "workspaceId = ?1 and moduleId = ?2", workspaceId, moduleId).firstResult();
        if (row != null) {
            return row.enabled;
        }
        return WorkspaceModuleCatalog.MODULES.stream()
                .filter(m -> m.id().equals(moduleId))
                .findFirst()
                .map(WorkspaceModuleCatalog.ModuleDef::defaultEnabled)
                .orElse(false);
    }

    public Map<String, Boolean> enabledModuleMap(String workspaceId) {
        Map<String, Boolean> map = new HashMap<>();
        for (WorkspaceModuleCatalog.ModuleDef def : WorkspaceModuleCatalog.MODULES) {
            map.put(def.id(), isModuleEnabled(workspaceId, def.id()));
        }
        return map;
    }

    public boolean isAdapterEnabled(String workspaceId, String adapterId) {
        WorkspaceEnabledAdapterEntity row = WorkspaceEnabledAdapterEntity.find(
                "workspaceId = ?1 and adapterId = ?2", workspaceId, adapterId).firstResult();
        if (row != null) {
            return row.enabled;
        }
        return "ADP-EXCEL".equals(adapterId);
    }

    public Map<String, Boolean> enabledAdapterMap(String workspaceId) {
        Map<String, Boolean> map = new HashMap<>();
        for (WorkspaceModuleCatalog.AdapterDef def : WorkspaceModuleCatalog.ADAPTERS) {
            map.put(def.id(), isAdapterEnabled(workspaceId, def.id()));
        }
        return map;
    }

    public Optional<String> moduleDisabledReason(String workspaceId, String requestPath) {
        if (WorkspaceModuleCatalog.isExemptPath(requestPath)) {
            return Optional.empty();
        }
        Optional<PathRule> rule = WorkspaceModuleCatalog.matchRule(requestPath);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        PathRule pathRule = rule.get();
        if (pathRule.mode() == MatchMode.ANY) {
            boolean any = pathRule.moduleIds().stream().anyMatch(id -> isModuleEnabled(workspaceId, id));
            if (!any) {
                return Optional.of("MODULE_DISABLED");
            }
            return Optional.empty();
        }
        for (String moduleId : pathRule.moduleIds()) {
            if (!isModuleEnabled(workspaceId, moduleId)) {
                return Optional.of("MODULE_DISABLED");
            }
        }
        return Optional.empty();
    }

    public Optional<String> adapterDisabledReason(String workspaceId, String requestPath) {
        if (!requestPath.contains("/integration/adapters/")) {
            return Optional.empty();
        }
        if (!isModuleEnabled(workspaceId, "MOD-DI")) {
            return Optional.of("MODULE_DISABLED");
        }
        String adapterId = extractAdapterId(requestPath);
        if (adapterId != null && !isAdapterEnabled(workspaceId, adapterId)) {
            return Optional.of("ADAPTER_DISABLED");
        }
        return Optional.empty();
    }

    private static String extractAdapterId(String requestPath) {
        String normalized = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        int idx = normalized.indexOf("/integration/adapters/");
        if (idx < 0) {
            return null;
        }
        String tail = normalized.substring(idx + "/integration/adapters/".length());
        if (tail.isBlank()) {
            return null;
        }
        String slug = tail.contains("/") ? tail.substring(0, tail.indexOf('/')) : tail;
        return switch (slug) {
            case "erp-sap" -> "ADP-ERP-SAP";
            case "mes" -> "ADP-MES";
            case "excel" -> "ADP-EXCEL";
            default -> null;
        };
    }
}
