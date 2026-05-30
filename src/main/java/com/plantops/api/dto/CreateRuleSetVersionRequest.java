package com.plantops.api.dto;

public record CreateRuleSetVersionRequest(String name, String copyFromRuleSetVersionId) {
}
