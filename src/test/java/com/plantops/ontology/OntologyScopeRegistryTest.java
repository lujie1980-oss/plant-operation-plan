package com.plantops.ontology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyScopeRegistryTest {

    @Test
    void ocpInOntologySchAndSltLegacy() {
        assertTrue(OntologyScopeRegistry.isInOntologyGraph(OntologyScopeRegistry.PlanningModule.MOD_OCP));
        assertFalse(OntologyScopeRegistry.isInOntologyGraph(OntologyScopeRegistry.PlanningModule.MOD_SCH));
        assertFalse(OntologyScopeRegistry.isInOntologyGraph(OntologyScopeRegistry.PlanningModule.MOD_SLT));
        assertTrue(OntologyScopeRegistry.forModule(OntologyScopeRegistry.PlanningModule.MOD_SCH)
                .legacyPersistence().contains("detail_schedule_operation"));
    }
}
