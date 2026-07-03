package com.plantops.scenario.planning;

import com.plantops.scenario.planning.sandbox.OntologySandbox;
import com.plantops.scenario.planning.sandbox.OntologySandboxStore;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OntologySandboxStoreTest {

    private record StubSandbox(String sessionId, String workspaceId, LocalDateTime expiresAt)
            implements OntologySandbox {
    }

    private static final class StubStore extends OntologySandboxStore<StubSandbox> {
        @Override
        protected String notFoundMessage(String sessionId) {
            return "Stub session not found: " + sessionId;
        }

        @Override
        protected String expiredMessage(String sessionId) {
            return "Stub session expired: " + sessionId;
        }
    }

    @Test
    void putAndRequireRoundTrip() {
        StubStore store = new StubStore();
        StubSandbox s = new StubSandbox("S-1", "ws-a", LocalDateTime.now().plusHours(1));
        store.put(s);
        assertSame(s, store.require("S-1", "ws-a"));
    }

    @Test
    void requireUnknownIdThrowsNotFound() {
        StubStore store = new StubStore();
        assertThrows(NotFoundException.class, () -> store.require("missing", "ws-a"));
    }

    @Test
    void requireWrongWorkspaceThrowsNotFound() {
        StubStore store = new StubStore();
        store.put(new StubSandbox("S-1", "ws-a", LocalDateTime.now().plusHours(1)));
        assertThrows(NotFoundException.class, () -> store.require("S-1", "ws-b"));
    }

    @Test
    void requireExpiredThrowsAndEvicts() {
        StubStore store = new StubStore();
        store.put(new StubSandbox("S-1", "ws-a", LocalDateTime.now().minusMinutes(1)));
        assertThrows(NotFoundException.class, () -> store.require("S-1", "ws-a"));
        assertEquals(0, store.size());
    }

    @Test
    void defaultTtlIsEightHours() {
        StubStore store = new StubStore();
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 10, 8, 0);
        assertEquals(createdAt.plusHours(8), store.defaultExpiresAt(createdAt));
    }
}
