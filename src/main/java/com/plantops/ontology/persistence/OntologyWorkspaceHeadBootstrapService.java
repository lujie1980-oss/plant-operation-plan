package com.plantops.ontology.persistence;

import com.plantops.config.LegacySchemaSupport;
import com.plantops.config.OntologyLegacyDualWriteFeature;
import com.plantops.config.OntologyWorkspaceHeadBootstrapFeature;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * P4: establish COMMITTED WORKSPACE HEAD from legacy loader when {@code ont_*} has no baseline yet.
 */
@ApplicationScoped
public class OntologyWorkspaceHeadBootstrapService {

    private static final Logger log = Logger.getLogger(OntologyWorkspaceHeadBootstrapService.class);

    @Inject
    LegacySchemaSupport legacySchemaSupport;

    @Inject
    OntologyWorkspaceHeadBootstrapFeature bootstrapFeature;

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyLegacyImporter legacyImporter;

    @Inject
    OntologyLegacyDualWriteFeature dualWriteFeature;

    @Inject
    OntologyLegacyDualWriteService dualWriteService;

    @Inject
    Instance<OntologyWorkspaceHeadBootstrapService> self;

    @ActivateRequestContext
    void onStart(@Observes StartupEvent event) {
        if (!legacySchemaSupport.isLegacySchemaEnabled() || !bootstrapFeature.enabled()) {
            return;
        }
        try {
            for (WorkspaceEntity workspace : WorkspaceEntity.listAllOrdered()) {
                self.get().ensureWorkspaceHead(workspace.workspaceId);
            }
        } catch (Exception ex) {
            log.errorf(ex, "ont WORKSPACE HEAD bootstrap failed");
        }
    }

    /**
     * Import loader graph into a new COMMITTED revision when WORKSPACE HEAD is absent.
     *
     * @return revision id (existing or newly created), or null when bootstrap disabled
     */
    @Transactional(REQUIRES_NEW)
    public String ensureWorkspaceHead(String workspaceId) {
        if (!legacySchemaSupport.isLegacySchemaEnabled() || !bootstrapFeature.enabled()) {
            return revisionService.resolveWorkspaceHeadRevisionId(workspaceId);
        }
        String existing = revisionService.resolveWorkspaceHeadRevisionId(workspaceId);
        if (existing != null) {
            return existing;
        }

        WorkspaceContext workspaceContext = Arc.container().instance(WorkspaceContext.class).get();
        String previous = workspaceContext.getWorkspaceId();
        try {
            workspaceContext.setWorkspaceId(workspaceId);
            OntologyGraph graph = ontologyLoader.loadForWorkspace(LocalDate.now());
            String revisionId = legacyImporter.importCommittedP0(workspaceId, graph);
            if (dualWriteFeature.enabled()) {
                dualWriteService.syncSupplyOrdersFromWorkOrders(workspaceId, revisionId);
            }
            log.infof("Bootstrapped ont WORKSPACE HEAD: workspace=%s revision=%s", workspaceId, revisionId);
            return revisionId;
        } finally {
            workspaceContext.setWorkspaceId(previous);
        }
    }
}
