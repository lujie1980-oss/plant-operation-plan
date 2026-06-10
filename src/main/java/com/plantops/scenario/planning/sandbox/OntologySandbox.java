package com.plantops.scenario.planning.sandbox;

import java.time.LocalDateTime;

/**
 * 推演沙盘会话的统一抽象：带工作区隔离与 TTL 过期的内存工作副本。
 */
public interface OntologySandbox {

    String sessionId();

    String workspaceId();

    LocalDateTime expiresAt();

    default boolean expired(LocalDateTime now) {
        return expiresAt() != null && now.isAfter(expiresAt());
    }
}
