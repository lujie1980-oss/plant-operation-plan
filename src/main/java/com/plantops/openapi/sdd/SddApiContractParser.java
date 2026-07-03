package com.plantops.openapi.sdd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code docs/sdd/core/06-api-contracts.md} into structured API operations (TODO-02). */
public final class SddApiContractParser {

    private static final Pattern SECTION_HEADER =
            Pattern.compile("^#{2,3} (API-[A-Z]+-\\d+)\\s+(.+)$");
    private static final Pattern TABLE_ROW =
            Pattern.compile("^\\|\\s*\\*\\*([^*]+)\\*\\*\\s*\\|\\s*([^|]+?)\\s*\\|$");
    private static final Pattern SUPPLEMENTAL =
            Pattern.compile(
                    "\\*\\*[^*]+\\*\\*[:：]\\s*`?(GET|POST|PUT|PATCH|DELETE)\\s+([^`\\n]+)`?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKTICK_VALUE = Pattern.compile("`([^`]+)`");

    private SddApiContractParser() {}

    public static List<SddApiOperation> parse(Path contractsFile) throws IOException {
        String markdown = Files.readString(contractsFile);
        return parse(markdown);
    }

    public static List<SddApiOperation> parse(String markdown) {
        List<SddApiOperation> operations = new ArrayList<>();
        String currentApiId = null;
        String currentSummary = null;
        StringBuilder block = new StringBuilder();

        for (String line : markdown.split("\n", -1)) {
            Matcher header = SECTION_HEADER.matcher(line.trim());
            if (header.matches()) {
                if (currentApiId != null) {
                    operations.addAll(parseSection(currentApiId, currentSummary, block.toString()));
                }
                currentApiId = header.group(1);
                currentSummary = header.group(2).trim();
                block = new StringBuilder();
                continue;
            }
            if (currentApiId != null) {
                if (line.startsWith("## ") && !line.startsWith("### ")) {
                    operations.addAll(parseSection(currentApiId, currentSummary, block.toString()));
                    currentApiId = null;
                    currentSummary = null;
                    block = new StringBuilder();
                    continue;
                }
                block.append(line).append('\n');
            }
        }
        if (currentApiId != null) {
            operations.addAll(parseSection(currentApiId, currentSummary, block.toString()));
        }
        return operations;
    }

    private static List<SddApiOperation> parseSection(String defaultApiId, String defaultSummary, String block) {
        List<SddApiOperation> operations = new ArrayList<>();
        String contextApiId = defaultApiId;
        String contextSummary = defaultSummary;
        boolean deprecated = block.contains("已废弃");

        String[] tableParts = block.split("(?m)\\| 项 \\| 值 \\|");
        for (int i = 1; i < tableParts.length; i++) {
            Map<String, String> fields = parseTable(tableParts[i]);
            if (fields.containsKey("ID")) {
                contextApiId = clean(fields.get("ID"));
            }
            String methods = fields.get("方法");
            String primaryPath = fields.get("路径");
            String alternatePath = fields.get("备选路径");
            if (methods == null) {
                continue;
            }
            if (primaryPath != null) {
                operations.addAll(
                        expand(contextApiId, contextSummary, methods, primaryPath, fields, deprecated));
            }
            if (alternatePath != null) {
                operations.addAll(
                        expand(contextApiId, contextSummary, methods, alternatePath, fields, true));
            }
        }

        Matcher supplemental = SUPPLEMENTAL.matcher(block);
        while (supplemental.find()) {
            String method = supplemental.group(1).toUpperCase(Locale.ROOT);
            String path = normalizePath(supplemental.group(2).trim());
            if (path != null) {
                operations.add(new SddApiOperation(
                        contextApiId,
                        contextSummary,
                        method,
                        path,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        block.contains("遗留") || block.contains("已废弃")));
            }
        }
        return operations;
    }

    private static Map<String, String> parseTable(String chunk) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : chunk.split("\n")) {
            Matcher row = TABLE_ROW.matcher(line.trim());
            if (row.matches()) {
                fields.put(row.group(1).trim(), clean(row.group(2)));
            }
            if (line.startsWith("---") && !fields.isEmpty()) {
                break;
            }
        }
        return fields;
    }

    private static List<SddApiOperation> expand(
            String apiId,
            String summary,
            String methodsCell,
            String pathsCell,
            Map<String, String> fields,
            boolean deprecated) {
        List<String> methods = splitMethods(methodsCell);
        List<String> paths = splitPaths(pathsCell);
        List<SddApiOperation> out = new ArrayList<>();

        if (methods.size() == 1) {
            for (String path : paths) {
                out.add(build(apiId, summary, methods.getFirst(), path, fields, deprecated));
            }
        } else if (paths.size() == 1) {
            for (String method : methods) {
                out.add(build(apiId, summary, method, paths.getFirst(), fields, deprecated));
            }
        } else if (methods.size() == paths.size()) {
            for (int i = 0; i < methods.size(); i++) {
                out.add(build(apiId, summary, methods.get(i), paths.get(i), fields, deprecated));
            }
        } else {
            for (String method : methods) {
                for (String path : paths) {
                    out.add(build(apiId, summary, method, path, fields, deprecated));
                }
            }
        }
        return out;
    }

    private static SddApiOperation build(
            String apiId,
            String summary,
            String method,
            String path,
            Map<String, String> fields,
            boolean deprecated) {
        return new SddApiOperation(
                apiId,
                summary,
                method,
                path,
                fields.get("场景"),
                fields.get("Query"),
                fields.get("Body"),
                fields.get("响应"),
                fields.get("错误"),
                fields.get("权限"),
                deprecated);
    }

    private static List<String> splitMethods(String cell) {
        List<String> methods = new ArrayList<>();
        Matcher matcher = BACKTICK_VALUE.matcher(cell);
        while (matcher.find()) {
            String token = matcher.group(1).trim().toUpperCase(Locale.ROOT);
            if (token.matches("GET|POST|PUT|PATCH|DELETE")) {
                methods.add(token);
            }
        }
        if (methods.isEmpty()) {
            for (String part : cell.split("/")) {
                String token = part.replace("`", "").trim().toUpperCase(Locale.ROOT);
                if (token.matches("GET|POST|PUT|PATCH|DELETE")) {
                    methods.add(token);
                }
            }
        }
        return methods;
    }

    private static List<String> splitPaths(String cell) {
        List<String> paths = new ArrayList<>();
        Matcher matcher = BACKTICK_VALUE.matcher(cell);
        while (matcher.find()) {
            String path = normalizePath(matcher.group(1).trim());
            if (path != null) {
                paths.add(path);
            }
        }
        if (paths.isEmpty()) {
            for (String part : cell.split("·")) {
                String path = normalizePath(part.trim());
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private static String normalizePath(String raw) {
        String path = raw.replace('…', '.').trim();
        if (path.contains("...")) {
            int idx = path.indexOf("...");
            String suffix = path.substring(idx + 3);
            if (!suffix.startsWith("/")) {
                suffix = "/" + suffix;
            }
            path = "/api/v1" + suffix;
        }
        if (!path.startsWith("/api/v1/") && !path.startsWith("/api/v1")) {
            return null;
        }
        return path.replaceAll("/+", "/").replace("{so}", "{salesOrderNo}").replace("{line}", "{salesOrderLineNo}");
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        Matcher matcher = BACKTICK_VALUE.matcher(trimmed);
        if (matcher.find()) {
            String first = matcher.group(1).trim();
            if (!matcher.find()) {
                return first;
            }
        }
        return trimmed.replace('`', ' ').replaceAll("\\s+", " ").trim();
    }
}
