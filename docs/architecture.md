# Architecture

## Flow

S01 Demand → S02 Kitting → S03 Capacity → S04 Master Plan (Timefold) → S05 Detail Schedule (Timefold + Session simulation) → S06 Dispatch/Events → S07 KPI

**Plan run:** user picks a **master plan strategy** (capacity mode + objective weights) → pipeline produces a **scenario** (`planVersionId`) → result pages share selection via `PlanContext` / `ScenarioSelector`.

`POST /api/v1/planning/run-full-pipeline` is master-plan-first: `includeDetailSchedule` defaults to `false`. Pass `includeDetailSchedule=true` to continue into S05 solving; production tasks are published only when a schedule session is confirmed.

**Planning layer (推演 vs 选优):** see [aps-planning-layer.md](./aps-planning-layer.md) — `com.plantops.scenario.planning` builds `*PlanningContext` (deterministic P0–P4), then `*ProblemMapper` projects to Timefold only for S04/S05 optimization.

## Solvers

- `MasterPlanSchedule` / `OrderAllocation` — **operation-level** slot assignment on `resourceId` (from `product_resource` routing); MRP material feasibility + BOM upstream order; soft timing/due-date objectives
- `DetailSchedule` / `ScheduleLine.assignedOperations` / `OperationAssignment` — Timefold 2.0 list-variable line queues with shadow start times, kitting gate, master-plan contract soft bounds, changeover/setup

## Detail scheduling sessions

S05 uses a working-copy loop for interactive scheduling: `ScheduleSessionResource` creates a session from a master-plan version, `PATCH /steps` and `POST /simulate` run incremental/full simulation through the shared timing kernel, `POST /optimize` runs Timefold against the working copy, and `POST /confirm` persists the schedule version and releases production tasks. See [detail-schedule-simulation-layer.md](./detail-schedule-simulation-layer.md).

## Strategy & scenarios

- `MasterPlanStrategyConfigService` — CRUD strategies in `system_parameter`; resolves capacity + weights for each solve
- `ScenarioComparisonService` — list/compare scenarios; enriches with `strategyName` from plan version or pipeline run
- `CapacityService.analyzeForMasterPlan(versionId)` — capacity view bound to selected scenario

## Integration

- `ErpPort` / `MockErpAdapter` — order import
- `MesPort` / `MockMesAdapter` — dispatch & feedback events
