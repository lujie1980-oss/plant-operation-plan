package com.plantops.iam.dto;

import java.time.LocalDateTime;

public record AdminUserDto(
        String userId,
        String loginName,
        String displayName,
        boolean isSuperAdmin,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {}
