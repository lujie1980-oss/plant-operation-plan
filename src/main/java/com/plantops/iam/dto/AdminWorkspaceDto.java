package com.plantops.iam.dto;

import java.time.LocalDateTime;

public record AdminWorkspaceDto(
        String workspaceId,
        String name,
        String ownerUserId,
        String workspaceType,
        int memberCount,
        LocalDateTime createdAt) {}
