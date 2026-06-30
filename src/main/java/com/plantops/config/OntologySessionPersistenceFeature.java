package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When true, Master Plan Session simulate/optimize/confirm writes DRAFT revisions to ont_* (TODO-12 P2/P3).
 * Enabled on {@code postgres} profile; default H2 dev keeps in-memory sandbox only.
 */
@ApplicationScoped
public class OntologySessionPersistenceFeature {

    @ConfigProperty(name = "plantops.ontology.persistence.session-enabled", defaultValue = "false")
    boolean sessionEnabled;

    public boolean enabled() {
        return sessionEnabled;
    }
}
