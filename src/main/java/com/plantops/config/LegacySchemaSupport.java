package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When {@code plantops.legacy-schema.enabled=false} (PostgreSQL ontology-only profile),
 * legacy H2 tables (workspace, work_order, …) are absent and startup hooks must skip.
 */
@ApplicationScoped
public class LegacySchemaSupport {

    @ConfigProperty(name = "plantops.legacy-schema.enabled", defaultValue = "true")
    boolean legacySchemaEnabled;

    public boolean isLegacySchemaEnabled() {
        return legacySchemaEnabled;
    }
}
