# Plant Operation Plan — Design Summary

Single-factory APS aligned with scenario cards S01–S07. Timefold handles S04 master planning and S05 detail scheduling; other scenarios use deterministic services and persistence.

**Last updated:** 2026-05-27

## Navigation (主计划)

| Type | Pages |
|------|-------|
| Config | 计划参数 · 优化目标（策略）· 业务规则 · 业务数据 · 计划运行 |
| Results | 需求满足 · 产能平衡 · 物料需求 · 生产工单 — **scenario selector in PageHeader** |
| Analysis | 场景对比 |

## Master plan strategy

Named strategy bundles stored in `system_parameter.master_plan_strategies`:

- **Capacity mode:** `UNCONSTRAINED` | `FINITE_CAPACITY`
- **Soft objectives:** lateness, priority, locked-order placement, **adjacent-slot load balancing**
- Selected at **计划运行**; persisted on `plan_version` and `planning_pipeline_run` as `strategy_id` / `strategy_name`

## Scenario model

- Each master-plan solve → one **scenario** (`planVersionId`)
- `PlanContext` holds scenario list + selection (localStorage restore)
- **Capacity** page re-analyzes on scenario change via `masterPlanVersionId`
- **Scenario comparison** lists scenarios with strategy name; multi-select KPI charts

## Solvers

- `MasterPlanSchedule` / `OrderAllocation` — slot assignment on bottleneck + routed resources; strategy-driven capacity + weighted soft constraints
- `DetailSchedule` / `OperationAssignment` — chained sequence on production lines with setup shadow times

## Integration

- `ErpPort` / `MockErpAdapter` — order import
- `MesPort` / `MockMesAdapter` — dispatch & feedback events

See `docs/PROJECT_DOCUMENTATION.md` and `docs/architecture.md` for full detail.
