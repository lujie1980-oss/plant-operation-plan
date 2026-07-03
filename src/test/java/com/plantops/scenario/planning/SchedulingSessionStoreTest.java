package com.plantops.scenario.planning;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.workspace.WorkspaceConstants;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulingSessionStoreTest {

    SchedulingSessionStore store;

    @BeforeEach
    void setUp() {
        store = new SchedulingSessionStore();
    }

    @Test
    void requireRejectsWrongWorkspace() {
        SchedulingSession session = new SchedulingSession(
                "SS-TEST",
                WorkspaceConstants.DEFAULT_ID,
                "MPV-1",
                LocalDate.now(),
                new DetailSchedule(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                false,
                null,
                null,
                null);
        store.put(session);
        assertThrows(NotFoundException.class, () -> store.require("SS-TEST", "other-workspace"));
    }
}
