# Architecture

## Flow

Workspace-scoped data → S01 Demand → S02 Kitting/MRP → S03 Capacity → S04 Master Plan (Timefold) → S05 Batch + Detail Schedule Session (simulation/Timefold) → S06 Production Tasks/Feedback → S07 KPI

**Plan run:** user picks a **master plan strategy** (capacity mode + objective weights) → pipeline produces a **scenario** (`planVersionId`) → result pages share selection via `PlanContext` / `ScenarioSelector`.

**Planning layer (推演 vs 选优):** see [aps-planning-layer.md](./aps-planning-layer.md) — `com.plantops.scenario.planning` builds `*PlanningContext` (deterministic P0–P4), then `*ProblemMapper` projects to Timefold only for S04/S05 optimization.

**Detail schedule Session workflow:** see [detail-schedule-simulation-layer.md](./detail-schedule-simulation-layer.md) — `ScheduleSessionResource` exposes create/get/patch/simulate/optimize/confirm. Simulation applies patches and rules (`SimulationProfile`, rule overrides, `feedbackCutoff`); confirm persists the schedule and publishes `production_task` records.

## Solvers

- `MasterPlanSchedule` / `OrderAllocation` — **operation-level** slot assignment on `resourceId` (from `product_resource` routing); MRP material feasibility + BOM upstream order; soft timing/due-date objectives
- `DetailSchedule` / `OperationAssignment` — line-level sequencing with kitting gate, production batches, master-plan contract soft bounds, changeover/setup

## Strategy & scenarios

- `MasterPlanStrategyConfigService` — CRUD strategies in `system_parameter`; resolves capacity + weights for each solve
- `ScenarioComparisonService` — list/compare scenarios; enriches with `strategyName` from plan version or pipeline run
- `CapacityService.analyzeForMasterPlan(versionId)` — capacity view bound to selected scenario
- `WorkspaceService` — isolates demo datasets and drives the UI workspace selector
- `ProductionBatchSplitService` / `ProductionBatchKittingService` — split work orders into active batches and compute batch-level kitting
- `DetailScheduleSessionService` / `SimulationPipeline` — stage local schedule changes before Timefold optimization or confirm
- `ProductionTaskService` / `ScheduleFeedbackService` — publish confirmed schedule steps and feed execution-state freezes back into simulation

## Integration

- `ErpPort` / `MockErpAdapter` — order import
- `MesPort` / `MockMesAdapter` — dispatch & feedback events
