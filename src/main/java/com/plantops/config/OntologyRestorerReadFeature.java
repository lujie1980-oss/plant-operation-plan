package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When true, {@link com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService}
 * overlays committed P0 entities from {@code ont_*} HEAD onto the legacy loader graph (TODO-12 P4).
 */
@ApplicationScoped
public class OntologyRestorerReadFeature {

    @ConfigProperty(name = "plantops.ontology.persistence.restorer-read-enabled", defaultValue = "false")
    boolean restorerReadEnabled;

    public boolean enabled() {
        return restorerReadEnabled;
    }
}
