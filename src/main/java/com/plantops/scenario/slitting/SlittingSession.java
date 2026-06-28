package com.plantops.scenario.slitting;

import com.plantops.solver.slitting.SlittingNestSolution;

import java.time.LocalDateTime;

/**
 * 分切排样画板推演会话：内存态局部求解，确认后写回方案。
 */
public record SlittingSession(
        String sessionId,
        String planVersionId,
        String activeParentNodeId,
        SlittingNestSolution solution,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String score,
        Long lastOptimizeMs) {

    public boolean expired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
