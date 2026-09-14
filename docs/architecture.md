# Architecture

## Flow

S01 Demand → S02 Kitting → S03 Capacity → S04 Master Plan (Timefold 2.0) → S05 Detail Schedule (Timefold 2.0 + Session simulation) → S06 Dispatch/Events → S07 KPI

**Plan run:** user picks a **master plan strategy** (capacity mode + objective weights) → pipeline produces a **scenario** (`planVersionId`) → result pages share selection via `PlanContext` / `ScenarioSelector`.

`PlanningOrchestrator.runFullPipeline()` defaults to a **master-plan run**: it prepares demand/material/capacity context, solves S04, records the scenario, and stops before S05. Pass `includeDetailSchedule=true` only when the same run should also build and solve S05; production-task release is the Session `confirm` path.

**Planning layer (推演 vs 选优):** see [aps-planning-layer.md](./aps-planning-layer.md) — `com.plantops.scenario.planning` builds `*PlanningContext` (deterministic P0–P4), then `*ProblemMapper` projects to Timefold only for S04/S05 optimization.

## Solvers

- `MasterPlanSchedule` / `OrderAllocation` — **operation-level** slot assignment on `resourceId` (from `product_resource` routing); MRP material feasibility + BOM upstream order; soft timing/due-date objectives
- `DetailSchedule` / `ScheduleLine.assignedOperations` — line-level list-variable sequencing with kitting gate, master-plan contract soft bounds, changeover/setup; `OperationAssignment.line` is inverse shadow and `startMinute` is computed by `OperationStartTimeCalculator` / `DetailScheduleTimingKernel`

## Strategy & scenarios

- `MasterPlanStrategyConfigService` — CRUD strategies in `system_parameter`; resolves capacity + weights for each solve
- `ScenarioComparisonService` — list/compare scenarios; enriches with `strategyName` from plan version or pipeline run
- `CapacityService.analyzeForMasterPlan(versionId)` — capacity view bound to selected scenario
- `ScheduleSessionResource` — creates S05 working copies, accepts patches, runs full/incremental simulation, optional Timefold optimize, and confirms released production tasks
- `SimulationProfileResource` — CRUD for S05 simulation rule profiles such as timing, incremental closure, and confirm-blocking validation behavior

## Integration

- `ErpPort` / `MockErpAdapter` — order import
- `MesPort` / `MockMesAdapter` — dispatch & feedback events
