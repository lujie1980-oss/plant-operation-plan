package com.plantops.iam.dto;

import java.util.List;

public record UpdateMemberPermissionsRequest(List<PermissionEntry> permissions) {
    public record PermissionEntry(String moduleId, String accessLevel) {}
}
