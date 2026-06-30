package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.entity.OntEntityPolicyEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

/**
 * Resolves STORE vs DERIVE per entity kind (§5.16 · RULE-PERS-05).
 * FULL revisions always STORE; PARTIAL uses workspace policy with PISPP DERIVE default.
 */
@ApplicationScoped
public class OntologyEntityPolicyService {

    public static final String PERSISTENCE_FULL = "FULL";
    public static final String PERSISTENCE_PARTIAL = "PARTIAL";

    public boolean shouldStore(String workspaceId, String persistenceMode, OntologyEntityKind kind) {
        if (PERSISTENCE_FULL.equals(persistenceMode)) {
            return true;
        }
        return resolveStorage(workspaceId, kind) == OntologyStorageMode.STORE;
    }

    public boolean shouldDerive(String workspaceId, String persistenceMode, OntologyEntityKind kind) {
        return !shouldStore(workspaceId, persistenceMode, kind);
    }

    public OntologyStorageMode resolveStorage(String workspaceId, OntologyEntityKind kind) {
        return OntEntityPolicyEntity.findPolicy(workspaceId, kind.name())
                .map(row -> OntologyStorageMode.valueOf(row.storage))
                .orElseGet(() -> defaultPartialStorage(kind));
    }

    @Transactional
    public void setPolicy(String workspaceId, OntologyEntityKind kind, OntologyStorageMode mode) {
        OntEntityPolicyEntity row = OntEntityPolicyEntity
                .findPolicy(workspaceId, kind.name())
                .orElseGet(() -> {
                    OntEntityPolicyEntity created = new OntEntityPolicyEntity();
                    created.workspaceId = workspaceId;
                    created.entityKind = kind.name();
                    return created;
                });
        row.storage = mode.name();
        row.updatedAt = LocalDateTime.now();
        row.persist();
    }

    @Transactional
    public void seedDefaultPartialPolicies(String workspaceId) {
        for (OntologyEntityKind kind : OntologyEntityKind.values()) {
            if (kind == OntologyEntityKind.BOM_DEPENDENCY) {
                setPolicy(workspaceId, kind, OntologyStorageMode.DERIVE);
            } else if (kind == OntologyEntityKind.PISPP) {
                setPolicy(workspaceId, kind, OntologyStorageMode.DERIVE);
            } else {
                setPolicy(workspaceId, kind, OntologyStorageMode.STORE);
            }
        }
    }

    private static OntologyStorageMode defaultPartialStorage(OntologyEntityKind kind) {
        return switch (kind) {
            case PISPP, BOM_DEPENDENCY -> OntologyStorageMode.DERIVE;
            default -> OntologyStorageMode.STORE;
        };
    }
}
