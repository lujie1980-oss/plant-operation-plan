# Architecture

## Flow

S01 Demand → S02 Kitting → S03 Capacity → S04 Master Plan (Timefold) → S05 Detail Schedule (Timefold) → S06 Dispatch/Events → S07 KPI

**Plan run:** user picks a **master plan strategy** (capacity mode + objective weights) → pipeline produces a **scenario** (`planVersionId`) → result pages share selection via `PlanContext` / `ScenarioSelector`.

**Planning layer (推演 vs 选优):** see [aps-planning-layer.md](./aps-planning-layer.md) — `com.plantops.scenario.planning` builds `*PlanningContext` (deterministic P0–P4), then `*ProblemMapper` projects to Timefold only for S04/S05 optimization.

## Solvers

- `MasterPlanSchedule` / `OrderAllocation` — **operation-level** slot assignment on `resourceId` (from `product_resource` routing); MRP material feasibility + BOM upstream order; soft timing/due-date objectives
- `DetailSchedule` / `OperationAssignment` — line-level sequencing with kitting gate, master-plan contract soft bounds, changeover/setup

## Strategy & scenarios

- `MasterPlanStrategyConfigService` — CRUD strategies in `system_parameter`; resolves capacity + weights for each solve
- `ScenarioComparisonService` — list/compare scenarios; enriches with `strategyName` from plan version or pipeline run
- `CapacityService.analyzeForMasterPlan(versionId)` — capacity view bound to selected scenario

## Integration

- `ErpPort` / `MockErpAdapter` — order import
- `MesPort` / `MockMesAdapter` — dispatch & feedback events
