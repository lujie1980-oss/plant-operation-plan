package com.plantops.iam.dto;

public record RegisterRequest(String userId, String loginName, String displayName, String password) {}
