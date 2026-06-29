package com.plantops.iam.dto;

public record PatchAdminUserRequest(
        String displayName,
        String status,
        Boolean isSuperAdmin,
        String password) {}
