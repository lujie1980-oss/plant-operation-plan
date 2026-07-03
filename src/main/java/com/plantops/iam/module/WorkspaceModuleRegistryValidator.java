package com.plantops.iam.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates {@code workspace-modules.yaml} / {@code integration-adapters.yaml} against
 * {@link WorkspaceModuleCatalog} (§19 · AC-IAM-06 · MOD-EXT / ADP-EXT).
 */
@ApplicationScoped
public class WorkspaceModuleRegistryValidator {

    @Inject
    WorkspaceModuleRegistryLoader loader;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        WorkspaceModuleRegistryModels.WorkspaceModulesDocument modulesDoc = loader.loadModules();
        WorkspaceModuleRegistryModels.IntegrationAdaptersDocument adaptersDoc = loader.loadAdapters();

        if (modulesDoc.modules == null || modulesDoc.modules.isEmpty()) {
            errors.add("workspace-modules.yaml: modules list is empty");
            return errors;
        }

        Map<String, WorkspaceModuleRegistryModels.ModuleEntry> yamlModules = new LinkedHashMap<>();
        for (WorkspaceModuleRegistryModels.ModuleEntry entry : modulesDoc.modules) {
            if (entry.id == null || entry.id.isBlank()) {
                errors.add("workspace-modules.yaml: module missing id");
                continue;
            }
            yamlModules.put(entry.id, entry);
        }

        for (WorkspaceModuleCatalog.ModuleDef def : WorkspaceModuleCatalog.MODULES) {
            WorkspaceModuleRegistryModels.ModuleEntry yaml = yamlModules.get(def.id());
            if (yaml == null) {
                errors.add("workspace-modules.yaml missing module " + def.id()
                        + " present in WorkspaceModuleCatalog");
                continue;
            }
            if (!def.name().equals(yaml.name)) {
                errors.add("module " + def.id() + " name mismatch: catalog="
                        + def.name() + " yaml=" + yaml.name);
            }
            if (!def.categoryId().equals(yaml.category)) {
                errors.add("module " + def.id() + " category mismatch: catalog="
                        + def.categoryId() + " yaml=" + yaml.category);
            }
            if (yaml.default_enabled != null && def.defaultEnabled() != yaml.default_enabled) {
                errors.add("module " + def.id() + " default_enabled mismatch: catalog="
                        + def.defaultEnabled() + " yaml=" + yaml.default_enabled);
            }
        }

        for (String yamlId : yamlModules.keySet()) {
            if (!WorkspaceModuleCatalog.KNOWN_MODULE_IDS.contains(yamlId)) {
                errors.add("workspace-modules.yaml defines unknown module " + yamlId
                        + " — register in WorkspaceModuleCatalog first (MOD-EXT)");
            }
        }

        Set<String> yamlAdapterIds = adaptersDoc.adapters == null
                ? Set.of()
                : adaptersDoc.adapters.stream()
                        .filter(a -> a.id != null && !"planned".equalsIgnoreCase(a.status))
                        .map(a -> a.id)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        for (WorkspaceModuleCatalog.AdapterDef def : WorkspaceModuleCatalog.ADAPTERS) {
            if (!yamlAdapterIds.contains(def.id())) {
                errors.add("integration-adapters.yaml missing active adapter " + def.id());
            }
        }
        for (String adapterId : yamlAdapterIds) {
            if (!WorkspaceModuleCatalog.KNOWN_ADAPTER_IDS.contains(adapterId)) {
                errors.add("integration-adapters.yaml defines unknown adapter " + adapterId
                        + " — register in WorkspaceModuleCatalog first (ADP-EXT)");
            }
        }

        Map<String, Set<String>> catalogPrefixesByModule = catalogApiPrefixesByModule();
        for (WorkspaceModuleRegistryModels.ModuleEntry module : modulesDoc.modules) {
            if (module.api_path_prefixes == null) {
                continue;
            }
            Set<String> catalogPrefixes = catalogPrefixesByModule.getOrDefault(module.id, Set.of());
            for (String yamlPrefix : module.api_path_prefixes) {
                String normalized = normalizeApiPrefix(yamlPrefix);
                if (!isCoveredByCatalog(normalized, catalogPrefixes)) {
                    errors.add("module " + module.id + " api_path_prefix " + yamlPrefix
                            + " has no matching WorkspaceModuleCatalog PathRule");
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : catalogPrefixesByModule.entrySet()) {
            WorkspaceModuleRegistryModels.ModuleEntry yaml = yamlModules.get(entry.getKey());
            if (yaml == null || yaml.api_path_prefixes == null) {
                continue;
            }
            Set<String> yamlPrefixes = yaml.api_path_prefixes.stream()
                    .map(WorkspaceModuleRegistryValidator::normalizeApiPrefix)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (String catalogPrefix : entry.getValue()) {
                if (!isCoveredByYaml(catalogPrefix, yamlPrefixes)) {
                    errors.add("WorkspaceModuleCatalog rule " + catalogPrefix
                            + " for " + entry.getKey() + " missing from workspace-modules.yaml api_path_prefixes");
                }
            }
        }

        return List.copyOf(errors);
    }

    private static Map<String, Set<String>> catalogApiPrefixesByModule() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (WorkspaceModuleCatalog.PathRule rule : WorkspaceModuleCatalog.RULES) {
            for (String moduleId : rule.moduleIds()) {
                out.computeIfAbsent(moduleId, ignored -> new LinkedHashSet<>()).add(rule.pathPrefix());
            }
        }
        return out;
    }

    private static boolean isCoveredByCatalog(String yamlPrefix, Set<String> catalogPrefixes) {
        for (String catalogPrefix : catalogPrefixes) {
            if (yamlPrefix.equals(catalogPrefix)
                    || yamlPrefix.startsWith(catalogPrefix + "/")
                    || catalogPrefix.startsWith(yamlPrefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoveredByYaml(String catalogPrefix, Set<String> yamlPrefixes) {
        for (String yamlPrefix : yamlPrefixes) {
            if (catalogPrefix.equals(yamlPrefix)
                    || catalogPrefix.startsWith(yamlPrefix + "/")
                    || yamlPrefix.startsWith(catalogPrefix + "/")) {
                return true;
            }
        }
        return false;
    }

    static String normalizeApiPrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
