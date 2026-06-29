package com.plantops.iam.dto;

import java.util.List;

public record UpdateModulesRequest(List<ModuleToggleRequest> modules) {
    public record ModuleToggleRequest(String moduleId, boolean enabled) {}
}
