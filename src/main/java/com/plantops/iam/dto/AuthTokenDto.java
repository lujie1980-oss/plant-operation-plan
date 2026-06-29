package com.plantops.iam.dto;

public record AuthTokenDto(
        String accessToken,
        String tokenType,
        long expiresInHours,
        String userId,
        String displayName,
        boolean isSuperAdmin) {}
