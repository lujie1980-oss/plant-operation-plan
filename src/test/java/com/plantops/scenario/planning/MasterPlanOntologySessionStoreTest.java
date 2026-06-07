package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.rol.RolEngine;
import com.plantops.workspace.WorkspaceConstants;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MasterPlanOntologySessionStoreTest {

    MasterPlanOntologySessionStore store;

    @BeforeEach
    void setUp() {
        store = new MasterPlanOntologySessionStore();
    }

    @Test
    void requireRejectsWrongWorkspace() {
        OntologyGraph graph = OntologyGraph.builder().build();
        MasterPlanOntologySession session = new MasterPlanOntologySession(
                "MOS-TEST",
                WorkspaceConstants.DEFAULT_ID,
                "MPV-1",
                graph,
                RolEngine.withDefaultPispRules(graph),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1));
        store.put(session);
        assertThrows(NotFoundException.class, () -> store.require("MOS-TEST", "other-workspace"));
    }
}
