package com.plantops.scenario;

import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlanningScenarioBootstrap {

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    WorkspaceContext workspaceContext;

    void onStart(@Observes StartupEvent event) {
        String previous = workspaceContext.getWorkspaceId();
        try {
            for (WorkspaceEntity ws : WorkspaceEntity.listAllOrdered()) {
                workspaceContext.setWorkspaceId(ws.workspaceId);
                ruleSetVersionService.ensureDefaults();
                planningScenarioService.ensureDefaults();
            }
        } finally {
            workspaceContext.setWorkspaceId(previous);
        }
    }
}
