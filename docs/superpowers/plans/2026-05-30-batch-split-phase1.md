# 批次拆解 Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在排程域实现已下发工单的固定量/手工/取消拆批、批次计划页，并使 S05 以批次为最小排程单位（MRP 不改）。

**Architecture:** 独立 `production_batch` 表关联父 `work_order`；`ProductionBatchSplitService` 负责拆/取消与数量守恒；`DetailSchedulePlanningContextBuilder` 候选集改为 ACTIVE 批次；未拆批工单整单兼容。

**Tech Stack:** Quarkus/Hibernate, Flyway V42, JUnit 5, React/TS

**Spec:** `docs/superpowers/specs/2026-05-30-batch-split-design.md`

---

## File Map

| File | Responsibility |
|------|----------------|
| `db/migration/V42__production_batch.sql` | 批次表 + 工单 batch_split_status |
| `persistence/entity/ProductionBatchEntity.java` | JPA 实体 |
| `scenario/batch/BatchSplitMode.java` 等 | 枚举 |
| `config/BatchSplitConfigService.java` | 读 system_parameter |
| `config/ParameterRegistry.java` | 默认参数 |
| `scenario/batch/ProductionBatchSplitService.java` | 拆/取消/手工 |
| `api/SchedulingBatchResource.java` | REST |
| `api/dto/batch/*.java` | DTO |
| `scenario/planning/DetailScheduleAssignmentBuilder.java` | buildForBatch |
| `solver/detailschedule/OperationAssignment.java` | batchNo/qty |
| `scenario/planning/DetailSchedulePlanningContextBuilder.java` | 批次候选 |
| `frontend/.../BatchPlanPage.tsx` | 批次计划页 |
| `frontend/.../batchSplitParameterGroups.ts` | 参数页签 |

---

## Phase 1 Tasks

### Task 1: Schema + Entity
- [ ] V42 migration
- [ ] ProductionBatchEntity + WorkOrderEntity.batchSplitStatus

### Task 2: Split service + unit tests
- [ ] Fixed qty + remainder modes
- [ ] Manual create / cancel / quantity conservation

### Task 3: Parameters + REST API
- [ ] ParameterRegistry defaults
- [ ] SchedulingBatchResource

### Task 4: S05 batch scheduling
- [ ] OperationAssignment fields
- [ ] DetailScheduleAssignmentBuilder.buildForBatch
- [ ] DetailSchedulePlanningContextBuilder batch scan

### Task 5: Frontend
- [ ] Batch split params in SchedulingPlanParametersPage
- [ ] BatchPlanPage + routing + API client

### Task 6: Verify
- [ ] `mvn test` + `npm run build`

**Deferred to Phase 2:** KITTING split, `batch_kitting_create_short_batch`  
## Phase 3（已完成）

- [x] `batch_min_qty` / `batch_max_qty` 参数
- [x] `BatchAutoSplitPlanner`：交期紧迫缩小批量、产能超 85% 班次则缩小、min/max 夹紧
- [x] `batch_split_mode=AUTO` 自动拆批 + 齐套评估（KITTED/SHORT）

---

## Phase 2（已完成）

- [x] `batch_kitting_create_short_batch` 参数
- [x] `BatchKittingQuantityCalculator` + 齐套拆批（KITTING 模式）
- [x] `POST /refresh-kitting` 刷新批次齐套状态
- [x] 下发后 `batch_auto_on_dispatch` 自动拆批
- [x] 机台甘特显示 `batchNo`
- [x] 批次计划页：齐套标签、刷新齐套按钮
