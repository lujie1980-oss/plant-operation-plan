package com.plantops.scenario.planning;

import com.plantops.scenario.planning.simulation.SimulationProfileSnapshot;
import com.plantops.solver.detailschedule.DetailSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 细排程推演工作副本：预览/手动改/确认发布前的内存态，不写执行表。
 */
public final class SchedulingSession {

    private final String sessionId;
    private final String workspaceId;
    private final String masterPlanVersionId;
    private final LocalDate planningAnchor;
    private final DetailSchedule schedule;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final boolean solved;
    private final Long solveDurationMs;
    private final String score;
    private final SimulationProfileSnapshot simulationProfile;

    public SchedulingSession(
            String sessionId,
            String workspaceId,
            String masterPlanVersionId,
            LocalDate planningAnchor,
            DetailSchedule schedule,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            boolean solved,
            Long solveDurationMs,
            String score) {
        this(sessionId, workspaceId, masterPlanVersionId, planningAnchor, schedule, createdAt, expiresAt, solved, solveDurationMs, score, null);
    }

    public SchedulingSession(
            String sessionId,
            String workspaceId,
            String masterPlanVersionId,
            LocalDate planningAnchor,
            DetailSchedule schedule,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            boolean solved,
            Long solveDurationMs,
            String score,
            SimulationProfileSnapshot simulationProfile) {
        this.sessionId = sessionId;
        this.workspaceId = workspaceId;
        this.masterPlanVersionId = masterPlanVersionId;
        this.planningAnchor = planningAnchor;
        this.schedule = schedule;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.solved = solved;
        this.solveDurationMs = solveDurationMs;
        this.score = score;
        this.simulationProfile = simulationProfile;
    }

    public String sessionId() {
        return sessionId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String masterPlanVersionId() {
        return masterPlanVersionId;
    }

    public LocalDate planningAnchor() {
        return planningAnchor;
    }

    public DetailSchedule schedule() {
        return schedule;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public boolean solved() {
        return solved;
    }

    public Long solveDurationMs() {
        return solveDurationMs;
    }

    public String score() {
        return score;
    }

    public SimulationProfileSnapshot simulationProfile() {
        return simulationProfile;
    }

    public boolean expired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
