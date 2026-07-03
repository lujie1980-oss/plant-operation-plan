package com.plantops.iam.module;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * API path → MOD-* mapping — mirrors knowledge/standard/modules/workspace-modules.yaml.
 * Longest prefix wins; {@link MatchMode#ANY} requires at least one listed module enabled.
 */
public final class WorkspaceModuleCatalog {

    public enum MatchMode {
        /** All listed module ids must be enabled. */
        ALL,
        /** At least one listed module id must be enabled. */
        ANY
    }

    public record PathRule(String pathPrefix, List<String> moduleIds, MatchMode mode) {}

    public record ModuleDef(String id, String name, String categoryId, boolean defaultEnabled) {}

    public record AdapterDef(String id, String name, String type) {}

    public static final List<PathRule> RULES = List.of(
            new PathRule("api/v1/work-orders", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/knowledge", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/production-tasks", List.of("MOD-SCH"), MatchMode.ALL),
            new PathRule("api/v1/planning/schedule-sessions", List.of("MOD-SCH"), MatchMode.ALL),
            new PathRule("api/v1/slitting", List.of("MOD-SLT"), MatchMode.ALL),
            new PathRule("api/v1/planning/detail-schedule", List.of("MOD-SCH"), MatchMode.ALL),
            new PathRule("api/v1/planning/schedule-feedback", List.of("MOD-SCH"), MatchMode.ALL),
            new PathRule("api/v1/scheduling", List.of("MOD-SCH"), MatchMode.ALL),
            new PathRule("api/v1/factory-calendar", List.of("MOD-CAL"), MatchMode.ALL),
            new PathRule("api/v1/integration", List.of("MOD-DI"), MatchMode.ALL),
            new PathRule("api/v1/external", List.of("MOD-DI"), MatchMode.ALL),
            new PathRule("api/v1/master-data", List.of("MOD-DI"), MatchMode.ALL),
            new PathRule("api/v1/business-data", List.of("MOD-DI"), MatchMode.ALL),
            new PathRule("api/v1/transactional-data", List.of("MOD-DI"), MatchMode.ALL),
            new PathRule("api/v1/master-plan", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/ontology", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/demand", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/material-requirements", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/planning", List.of("MOD-OCP"), MatchMode.ALL),
            new PathRule("api/v1/business-rules", List.of("MOD-OCP", "MOD-SCH"), MatchMode.ANY)
    );

    private static final List<PathRule> SORTED_RULES = RULES.stream()
            .sorted(Comparator.comparingInt((PathRule r) -> r.pathPrefix().length()).reversed())
            .toList();

    public static final List<ModuleDef> MODULES = List.of(
            new ModuleDef("MOD-DI", "数据集成", "CAT-INTEGRATION", true),
            new ModuleDef("MOD-CAL", "工厂日历", "CAT-INTEGRATION", true),
            new ModuleDef("MOD-OCP", "订单协同计划", "CAT-PLANNING", true),
            new ModuleDef("MOD-SCH", "作业排程", "CAT-PLANNING", true),
            new ModuleDef("MOD-SLT", "分切排样", "CAT-PLANNING", false)
    );

    public static final List<AdapterDef> ADAPTERS = List.of(
            new AdapterDef("ADP-ERP-SAP", "ERP 适配器（SAP）", "ERP"),
            new AdapterDef("ADP-MES", "MES 适配器", "MES"),
            new AdapterDef("ADP-EXCEL", "Excel 数据适配器", "FILE")
    );

    public static final Set<String> KNOWN_MODULE_IDS = Set.copyOf(
            MODULES.stream().map(ModuleDef::id).toList());

    public static final Set<String> KNOWN_ADAPTER_IDS = Set.copyOf(
            ADAPTERS.stream().map(AdapterDef::id).toList());

    private WorkspaceModuleCatalog() {}

    public static Optional<PathRule> matchRule(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return Optional.empty();
        }
        String normalized = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        for (PathRule rule : SORTED_RULES) {
            if (normalized.equals(rule.pathPrefix()) || normalized.startsWith(rule.pathPrefix() + "/")) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public static boolean isExemptPath(String requestPath) {
        if (requestPath == null) {
            return true;
        }
        String normalized = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        return normalized.startsWith("api/v1/iam/")
                || normalized.startsWith("api/v1/auth/")
                || normalized.startsWith("api/v1/admin/")
                || normalized.startsWith("api/v1/workspaces")
                || normalized.startsWith("api/v1/dashboard");
    }
}
