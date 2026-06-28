package com.plantops.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Workspace 级开关：本体 Session 是否走直驱 Timefold 求解（路线 B）。 */
@ApplicationScoped
public class OntologyDirectSolveFeature {

    public static final String PARAM_ID = "ontology_direct_solve_enabled";

    @Inject
    ParameterRegistry parameters;

    public boolean enabled() {
        return parameters.getBoolean(PARAM_ID, false);
    }
}
