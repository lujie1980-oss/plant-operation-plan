package com.plantops.openapi.sdd;

import java.util.Objects;

/** One HTTP operation extracted from §6 {@code 06-api-contracts.md}. */
public record SddApiOperation(
        String apiId,
        String summary,
        String httpMethod,
        String path,
        String scenario,
        String query,
        String body,
        String response,
        String errors,
        String permission,
        boolean deprecated) {

    public SddApiOperation {
        Objects.requireNonNull(apiId, "apiId");
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(path, "path");
    }

    public String operationId() {
        String base = apiId.replace('-', '_');
        return base + "_" + httpMethod.toLowerCase();
    }

    public String tag() {
        int dash = apiId.lastIndexOf('-');
        return dash > 0 ? apiId.substring(0, dash) : apiId;
    }
}
