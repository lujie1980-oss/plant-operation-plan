package com.plantops.openapi.sdd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds OpenAPI 3.0.3 skeleton maps from parsed §6 operations (TODO-02). */
public final class OpenApiSkeletonGenerator {

    private static final Pattern DTO_NAME = Pattern.compile("\\b([A-Z][A-Za-z0-9]+Dto)\\b");
    private static final Pattern QUERY_PARAM =
            Pattern.compile("([a-zA-Z][a-zA-Z0-9_?]*)(?:（[^）]*）)?");

    private OpenApiSkeletonGenerator() {}

    public static Map<String, Object> generate(List<SddApiOperation> operations) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.0.3");
        doc.put("info", info());
        doc.put("servers", List.of(server()));
        doc.put("tags", tags(operations));
        doc.put("paths", paths(operations));
        doc.put("components", components(operations));
        doc.put("security", List.of(Map.of("workspaceHeader", List.of())));
        return doc;
    }

    private static Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Plant Operation Plan API");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put(
                "description",
                "Generated skeleton from docs/sdd/core/06-api-contracts.md (§6). "
                        + "Regenerate via com.plantops.openapi.sdd.GenerateOpenApiFromSdd — do not edit by hand.");
        return info;
    }

    private static Map<String, Object> server() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "/");
        server.put("description", "Application root (paths include /api/v1)");
        return server;
    }

    private static List<Map<String, Object>> tags(List<SddApiOperation> operations) {
        Set<String> tagNames = new LinkedHashSet<>();
        for (SddApiOperation op : operations) {
            tagNames.add(op.tag());
        }
        List<Map<String, Object>> tags = new ArrayList<>();
        for (String name : tagNames) {
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("name", name);
            tag.put("description", "§6 " + name);
            tags.add(tag);
        }
        return tags;
    }

    private static Map<String, Object> paths(List<SddApiOperation> operations) {
        Map<String, Object> paths = new LinkedHashMap<>();
        Set<String> usedOperationIds = new LinkedHashSet<>();

        for (SddApiOperation op : operations) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem =
                    (Map<String, Object>) paths.computeIfAbsent(op.path(), k -> new LinkedHashMap<>());
            String methodKey = op.httpMethod().toLowerCase(Locale.ROOT);
            if (pathItem.containsKey(methodKey)) {
                continue;
            }
            pathItem.put(methodKey, operation(op, usedOperationIds));
        }
        return paths;
    }

    private static Map<String, Object> operation(SddApiOperation op, Set<String> usedOperationIds) {
        Map<String, Object> operation = new LinkedHashMap<>();
        String operationId = uniqueOperationId(op, usedOperationIds);
        operation.put("operationId", operationId);
        operation.put("tags", List.of(op.tag()));
        operation.put("summary", op.summary());
        operation.put("description", buildDescription(op));
        operation.put("x-api-id", op.apiId());
        if (op.deprecated()) {
            operation.put("deprecated", true);
        }
        List<Map<String, Object>> parameters = parameters(op);
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        if (op.body() != null && !op.body().isBlank()) {
            operation.put("requestBody", requestBody(op.body()));
        }
        operation.put("responses", responses(op.response(), op.errors()));
        return operation;
    }

    private static String uniqueOperationId(SddApiOperation op, Set<String> usedOperationIds) {
        String base = op.operationId();
        String candidate = base;
        int i = 2;
        while (!usedOperationIds.add(candidate)) {
            candidate = base + "_" + i++;
        }
        return candidate;
    }

    private static String buildDescription(SddApiOperation op) {
        StringBuilder sb = new StringBuilder();
        sb.append("Spec ").append(op.apiId());
        if (op.scenario() != null && !op.scenario().isBlank()) {
            sb.append(" · ").append(op.scenario());
        }
        if (op.permission() != null && !op.permission().isBlank()) {
            sb.append(" · ").append(op.permission());
        }
        return sb.toString();
    }

    private static List<Map<String, Object>> parameters(SddApiOperation op) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String segment : op.path().split("/")) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String name = segment.substring(1, segment.length() - 1);
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("name", name);
                param.put("in", "path");
                param.put("required", true);
                param.put("schema", Map.of("type", "string"));
                parameters.add(param);
            }
        }
        if (op.query() != null && !op.query().isBlank()) {
            for (String token : op.query().split("[,，、]")) {
                Matcher matcher = QUERY_PARAM.matcher(token.trim());
                if (matcher.find()) {
                    String name = matcher.group(1).replace("?", "");
                    Map<String, Object> param = new LinkedHashMap<>();
                    param.put("name", name);
                    param.put("in", "query");
                    param.put("required", false);
                    param.put("schema", Map.of("type", "string"));
                    parameters.add(param);
                }
            }
        }
        return parameters;
    }

    private static Map<String, Object> requestBody(String body) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", true);
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schema", schemaRefOrPlaceholder(body));
        content.put("application/json", json);
        requestBody.put("content", content);
        return requestBody;
    }

    private static Map<String, Object> responses(String response, String errors) {
        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", response != null ? response : "Success");
        if (response != null) {
            Map<String, Object> content = new LinkedHashMap<>();
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("schema", schemaRefOrPlaceholder(response));
            content.put("application/json", json);
            ok.put("content", content);
        }
        responses.put("200", ok);
        if (errors != null && errors.contains("404")) {
            responses.put("404", Map.of("description", errors));
        }
        return responses;
    }

    private static Map<String, Object> schemaRefOrPlaceholder(String text) {
        Matcher matcher = DTO_NAME.matcher(text);
        if (matcher.find()) {
            return Map.of("$ref", "#/components/schemas/" + matcher.group(1));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", text.trim());
        return schema;
    }

    private static Map<String, Object> components(List<SddApiOperation> operations) {
        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        Map<String, Object> workspaceHeader = new LinkedHashMap<>();
        workspaceHeader.put("type", "apiKey");
        workspaceHeader.put("in", "header");
        workspaceHeader.put("name", "X-Workspace-Id");
        workspaceHeader.put("description", "Required for business APIs (§6)");
        securitySchemes.put("workspaceHeader", workspaceHeader);
        components.put("securitySchemes", securitySchemes);

        Map<String, Object> schemas = new LinkedHashMap<>();
        for (SddApiOperation op : operations) {
            collectSchemaNames(op.body(), schemas);
            collectSchemaNames(op.response(), schemas);
        }
        components.put("schemas", schemas);
        return components;
    }

    private static void collectSchemaNames(String text, Map<String, Object> schemas) {
        if (text == null) {
            return;
        }
        Matcher matcher = DTO_NAME.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            schemas.computeIfAbsent(name, OpenApiSkeletonGenerator::placeholderSchema);
        }
    }

    private static Map<String, Object> placeholderSchema(String name) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", "§6 placeholder — implement in com.plantops.api.dto");
        return schema;
    }
}
