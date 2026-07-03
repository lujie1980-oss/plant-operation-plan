package com.plantops.api.dto;

import java.time.LocalDateTime;

public record WorkspaceDto(
        String workspaceId,
        String name,
        String description,
        LocalDateTime createdAt,
        boolean isDefault,
        String ownerUserId,
        String workspaceType,
        String industryId,
        String knowledgePackVersion) {
}
