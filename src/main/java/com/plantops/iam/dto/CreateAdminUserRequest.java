package com.plantops.iam.dto;

public record CreateAdminUserRequest(
        String userId,
        String loginName,
        String displayName,
        String password,
        boolean isSuperAdmin) {}
