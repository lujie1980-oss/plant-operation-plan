package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * When true, confirm promote reconciles legacy {@code work_order} → {@code ont_supply_order}
 * on the committed revision (TODO-12 P4 dual-write).
 */
@ApplicationScoped
public class OntologyLegacyDualWriteFeature {

    @ConfigProperty(name = "plantops.ontology.persistence.dual-write-enabled", defaultValue = "false")
    boolean dualWriteEnabled;

    public boolean enabled() {
        return dualWriteEnabled;
    }
}
