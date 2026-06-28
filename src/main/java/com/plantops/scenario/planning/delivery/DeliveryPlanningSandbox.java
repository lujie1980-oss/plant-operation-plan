package com.plantops.scenario.planning.delivery;

import com.plantops.ontology.OntologyGraph;
import com.plantops.rol.RolEngine;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.sandbox.OntologySandbox;

import java.time.LocalDateTime;

public final class DeliveryPlanningSandbox implements OntologySandbox {

    private final String sandboxId;
    private final String workspaceId;
    private final String deliveryId;
    private final String baselinePlanVersionId;
    private final OntologyGraph graph;
    private final RolEngine rolEngine;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final int trialRevision;
    private final OptimizerResult lastOptimizerResult;

    public DeliveryPlanningSandbox(
            String sandboxId,
            String workspaceId,
            String deliveryId,
            String baselinePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this(sandboxId, workspaceId, deliveryId, baselinePlanVersionId, graph, rolEngine,
                createdAt, expiresAt, 0, null);
    }

    public DeliveryPlanningSandbox(
            String sandboxId,
            String workspaceId,
            String deliveryId,
            String baselinePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            int trialRevision,
            OptimizerResult lastOptimizerResult) {
        this.sandboxId = sandboxId;
        this.workspaceId = workspaceId;
        this.deliveryId = deliveryId;
        this.baselinePlanVersionId = baselinePlanVersionId;
        this.graph = graph;
        this.rolEngine = rolEngine;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.trialRevision = trialRevision;
        this.lastOptimizerResult = lastOptimizerResult;
    }

    @Override
    public String sessionId() {
        return sandboxId;
    }

    @Override
    public String workspaceId() {
        return workspaceId;
    }

    @Override
    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public String sandboxId() {
        return sandboxId;
    }

    public String deliveryId() {
        return deliveryId;
    }

    public String baselinePlanVersionId() {
        return baselinePlanVersionId;
    }

    public OntologyGraph graph() {
        return graph;
    }

    public RolEngine rolEngine() {
        return rolEngine;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public int trialRevision() {
        return trialRevision;
    }

    public OptimizerResult lastOptimizerResult() {
        return lastOptimizerResult;
    }

    public DeliveryPlanningSandbox withTrialResult(int revision, OptimizerResult result) {
        return new DeliveryPlanningSandbox(
                sandboxId,
                workspaceId,
                deliveryId,
                baselinePlanVersionId,
                graph,
                rolEngine,
                createdAt,
                expiresAt,
                revision,
                result);
    }
}
