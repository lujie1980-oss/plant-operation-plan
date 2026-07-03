package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.rol.RolEngine;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.sandbox.OntologySandbox;
import com.plantops.solver.masterplan.MasterPlanSchedule;

import java.time.LocalDateTime;

public final class MasterPlanOntologySession implements OntologySandbox {

    private final String sessionId;
    private final String workspaceId;
    private final String basePlanVersionId;
    private final OntologyGraph graph;
    private final RolEngine rolEngine;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final MasterPlanSolveProfile solveProfile;
    private final MasterPlanSchedule lastSolution;
    private final Long lastSolveDurationMs;
    private final OptimizerResult lastOptimizerResult;

    public MasterPlanOntologySession(
            String sessionId,
            String workspaceId,
            String basePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this(sessionId, workspaceId, basePlanVersionId, graph, rolEngine, createdAt, expiresAt,
                MasterPlanSolveProfile.defaults(LocalDateTime.now().toLocalDate()), null, null, null);
    }

    public MasterPlanOntologySession(
            String sessionId,
            String workspaceId,
            String basePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            MasterPlanSolveProfile solveProfile,
            MasterPlanSchedule lastSolution,
            Long lastSolveDurationMs) {
        this(sessionId, workspaceId, basePlanVersionId, graph, rolEngine, createdAt, expiresAt,
                solveProfile, lastSolution, lastSolveDurationMs, null);
    }

    public MasterPlanOntologySession(
            String sessionId,
            String workspaceId,
            String basePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            MasterPlanSolveProfile solveProfile,
            MasterPlanSchedule lastSolution,
            Long lastSolveDurationMs,
            OptimizerResult lastOptimizerResult) {
        this.sessionId = sessionId;
        this.workspaceId = workspaceId;
        this.basePlanVersionId = basePlanVersionId;
        this.graph = graph;
        this.rolEngine = rolEngine;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.solveProfile = solveProfile != null
                ? solveProfile
                : MasterPlanSolveProfile.defaults(LocalDateTime.now().toLocalDate());
        this.lastSolution = lastSolution;
        this.lastSolveDurationMs = lastSolveDurationMs;
        this.lastOptimizerResult = lastOptimizerResult;
    }

    public String sessionId() {
        return sessionId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String basePlanVersionId() {
        return basePlanVersionId;
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

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public MasterPlanSolveProfile solveProfile() {
        return solveProfile;
    }

    public MasterPlanSchedule lastSolution() {
        return lastSolution;
    }

    public Long lastSolveDurationMs() {
        return lastSolveDurationMs;
    }

    public OptimizerResult lastOptimizerResult() {
        return lastOptimizerResult;
    }

    public MasterPlanOntologySession withLastSolution(MasterPlanSchedule solution, long solveDurationMs) {
        return new MasterPlanOntologySession(
                sessionId,
                workspaceId,
                basePlanVersionId,
                graph,
                rolEngine,
                createdAt,
                expiresAt,
                solveProfile,
                solution,
                solveDurationMs,
                lastOptimizerResult);
    }

    public MasterPlanOntologySession withLastOptimizerResult(OptimizerResult optimizerResult) {
        return new MasterPlanOntologySession(
                sessionId,
                workspaceId,
                basePlanVersionId,
                graph,
                rolEngine,
                createdAt,
                expiresAt,
                solveProfile,
                lastSolution,
                lastSolveDurationMs,
                optimizerResult);
    }

    public boolean expired() {
        return expired(LocalDateTime.now());
    }

    public boolean expired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
