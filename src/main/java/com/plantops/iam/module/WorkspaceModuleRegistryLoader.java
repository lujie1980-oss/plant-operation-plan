package com.plantops.iam.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;

/** Loads machine-readable module / adapter registries from knowledge packs. */
@ApplicationScoped
public class WorkspaceModuleRegistryLoader {

    private static final String MODULES_RESOURCE = "knowledge/standard/modules/workspace-modules.yaml";
    private static final String ADAPTERS_RESOURCE = "knowledge/standard/modules/integration-adapters.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public WorkspaceModuleRegistryModels.WorkspaceModulesDocument loadModules() {
        return read(MODULES_RESOURCE, WorkspaceModuleRegistryModels.WorkspaceModulesDocument.class);
    }

    public WorkspaceModuleRegistryModels.IntegrationAdaptersDocument loadAdapters() {
        return read(ADAPTERS_RESOURCE, WorkspaceModuleRegistryModels.IntegrationAdaptersDocument.class);
    }

    private <T> T read(String resource, Class<T> type) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + resource);
            }
            return yamlMapper.readValue(in, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + resource, ex);
        }
    }
}
