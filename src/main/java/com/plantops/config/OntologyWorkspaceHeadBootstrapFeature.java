package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When true, seeds {@code ont_revision_head(WORKSPACE)} from {@link com.plantops.ontology.OntologyLoader}
 * if missing (TODO-12 P4 migration bootstrap).
 */
@ApplicationScoped
public class OntologyWorkspaceHeadBootstrapFeature {

    @ConfigProperty(name = "plantops.ontology.persistence.bootstrap-head-enabled", defaultValue = "false")
    boolean bootstrapHeadEnabled;

    public boolean enabled() {
        return bootstrapHeadEnabled;
    }
}
