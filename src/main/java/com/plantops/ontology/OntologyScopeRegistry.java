package com.plantops.ontology;

import java.util.List;
import java.util.Set;

/**
 * §5.21 ENT-OG scope boundary (TODO-20): MOD-OCP in ontology today;
 * MOD-SCH / MOD-SLT remain legacy JPA until phased into {@code ont_*}.
 */
public final class OntologyScopeRegistry {

    public enum PlanningModule {
        /** PROC-S04 · fully in ENT-OG + ont_* P0~P5 */
        MOD_OCP,
        /** PROC-S05 · DetailScheduleOperationEntity / schedule sessions */
        MOD_SCH,
        /** Slitting nest · slitting_plan_* tables */
        MOD_SLT
    }

    public record ScopeEntry(
            PlanningModule module,
            String procId,
            boolean inOntologyGraph,
            List<String> ontologyEntityIds,
            List<String> legacyPersistence) {}

    private static final List<ScopeEntry> ENTRIES = List.of(
            new ScopeEntry(
                    PlanningModule.MOD_OCP,
                    "PROC-S04",
                    true,
                    List.of(
                            "ENT-COLD", "ENT-COL", "ENT-DEM", "ENT-FF", "ENT-OP", "ENT-RCA",
                            "ENT-SO", "ENT-SRP", "ENT-PRP", "ENT-PISPP", "ENT-PER", "ENT-SES", "ENT-PV"),
                    List.of("master_plan_allocation", "plan_version")),
            new ScopeEntry(
                    PlanningModule.MOD_SCH,
                    "PROC-S05",
                    false,
                    List.of("ENT-OP-SCH", "ENT-RCA-SCH"),
                    List.of(
                            "detail_schedule_operation",
                            "schedule_session",
                            "production_task",
                            "schedule_feedback")),
            new ScopeEntry(
                    PlanningModule.MOD_SLT,
                    "PROC-SLT",
                    false,
                    List.of("ENT-SLT-ROLL", "ENT-SLT-ASSIGN"),
                    List.of(
                            "slitting_plan_version",
                            "slitting_plan_master_roll",
                            "slitting_plan_child_order",
                            "slitting_roll_node",
                            "slitting_assignment")));

    public static final Set<PlanningModule> ONTOLOGY_MODULES = Set.of(PlanningModule.MOD_OCP);

    private OntologyScopeRegistry() {}

    public static List<ScopeEntry> entries() {
        return ENTRIES;
    }

    public static ScopeEntry forModule(PlanningModule module) {
        return ENTRIES.stream()
                .filter(e -> e.module() == module)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown module: " + module));
    }

    public static boolean isInOntologyGraph(PlanningModule module) {
        return ONTOLOGY_MODULES.contains(module);
    }
}
