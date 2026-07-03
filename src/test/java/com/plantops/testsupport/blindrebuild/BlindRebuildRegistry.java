package com.plantops.testsupport.blindrebuild;

import java.util.List;

/** Registered modules eligible for TODO-05 blind rebuild exercises. */
public final class BlindRebuildRegistry {

  /** Global gates every rebuild must pass (contract + traceability). */
  public static final List<String> GLOBAL_GATE_TESTS =
      List.of(
          "com.plantops.testsupport.SpecRefCoverageTest",
          "com.plantops.openapi.sdd.OpenApiSpecCoverageTest");

  public static final List<BlindRebuildModulePack> MODULE_PACKS =
      List.of(
          new BlindRebuildModulePack(
              "sch-p0-projection",
              "MOD-SCH · ENT-OP-SCH / ENT-RCA-SCH legacy projection (SCH-P0)",
              "01-sch-p0-projection.md",
              List.of(
                  "core/05-domain-model.md#522-mod-sch-细排实体sch-p0",
                  "core/05-domain-model-appendix-fields.md",
                  "core/08-acceptance.md"),
              List.of("AC-SCH-P0-01"),
              List.of("com.plantops.ontology.scheduling.DetailScheduleLegacyProjectorTest")),
          new BlindRebuildModulePack(
              "scenario-comparison",
              "VAL-06 · multi ENT-PV scenario comparison (SCN-06b)",
              "02-scenario-comparison.md",
              List.of(
                  "core/01-value-goals.md",
                  "core/03-scenarios.md",
                  "volumes/knowledge/15-16-planning-knowledge.md",
                  "volumes/platform/17-ui-ux.md",
                  "core/08-acceptance.md"),
              List.of("AC-VAL-06-01"),
              List.of("com.plantops.scenario.ScenarioComparisonServiceTest")),
          new BlindRebuildModulePack(
              "workspace-module-registry",
              "§19 workspace module catalog ↔ YAML sync (AC-IAM-06)",
              "03-workspace-module-registry.md",
              List.of(
                  "volumes/platform/18-19-workspace-platform.md",
                  "core/08-acceptance.md"),
              List.of("AC-IAM-06"),
              List.of("com.plantops.iam.module.WorkspaceModuleCatalogSyncTest")));

  private BlindRebuildRegistry() {}
}
