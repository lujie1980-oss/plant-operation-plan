package com.plantops.iam.module;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Jackson DTOs for knowledge/standard/modules/*.yaml (§19). */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WorkspaceModuleRegistryModels {

    private WorkspaceModuleRegistryModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkspaceModulesDocument {
        public List<ModuleEntry> modules;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IntegrationAdaptersDocument {
        public List<AdapterEntry> adapters;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModuleEntry {
        public String id;
        public String name;
        public String category;
        public Boolean default_enabled;
        public List<String> api_path_prefixes;
        public List<String> adapters;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdapterEntry {
        public String id;
        public String name;
        public String type;
        public String status;
    }
}
