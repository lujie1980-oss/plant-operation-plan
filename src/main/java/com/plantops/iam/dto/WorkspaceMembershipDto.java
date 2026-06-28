package com.plantops.iam.dto;

import java.util.List;

public record WorkspaceMembershipDto(
        String workspaceId,
        String name,
        String role,
        List<String> enabledModules) {
}
