# OTD 主计划本体 M2 — Timefold 桥接 + Confirm 持久化

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]` checkboxes.

**Goal:** 将 M1 内存本体 Session 与现有 S04 Timefold 主计划求解闭环：`optimize` → 槽位分配回写本体 → `confirm` 投影 `MasterPlanAllocationEntity`；前端可展示 PISPP 联动（最小 UI）。

**Architecture:** 保留 `MasterPlanPlanningContextBuilder` 作为 Timefold 输入真相；新增 `OntologyTimefoldBridge` 双向映射；求解结果先写 Session 内 `ResourceAssignment` 视图（内存），confirm 时批量持久化。ChangeSet 采用 M2-lite（Java record 列表，ROL 统一 apply 入口预留）。

**Tech Stack:** 现有 `MasterPlanService`, `MasterPlanProblemMapper`, `OrderAllocation`, `MasterPlanAllocationEntity`.

**Depends on:** M1 commit `61e10a1` on `feature/otd-ontology-m1`.

---

## M2 验收

1. `POST .../sessions/{id}/optimize` 调用 Timefold，返回 score + allocation 摘要  
2. optimize 后 Session 内 PISPP/SO 相关 derived 字段更新（至少 WO 对应 period 的 supply 聚合）  
3. `POST .../sessions/{id}/confirm` 写入新 `planVersionId` + `MasterPlanAllocationEntity`  
4. 集成测试：create → optimize → confirm → GET master plan 可见 allocation  
5. M1 测试仍 PASS  

---

## Epic M2-A: ChangeSet + Bridge 骨架

### Task A.1: ChangeSet 模型

**Files:**
- Create: `src/main/java/com/plantops/rol/ChangeSet.java`
- Create: `src/main/java/com/plantops/rol/Operation.java` (rename to `ChangeOperation` if conflict)
- Create: `src/main/java/com/plantops/rol/RolTransaction.java` — `apply(ChangeSet)` delegates to RolEngine + future hooks
- Test: `src/test/java/com/plantops/rol/ChangeSetApplyTest.java`

- [ ] PISPP property change via ChangeSet equals direct `applyPropertyChange`

### Task A.2: OntologyTimefoldBridge

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/OntologyTimefoldBridge.java`
- Inject: `MasterPlanPlanningContextBuilder`, `MasterPlanProblemMapper`, `MasterPlanService` (or solver facade)

Methods:
- `buildContext(OntologyGraph graph, String planVersionId)` — M2: 仍从 DB/planVersion 构建 Context（graph 用于校验 PISP 集合一致）
- `toChangeSet(MasterPlanSchedule solved)` — `OrderAllocation.timeSlot` → 内存 `ResourceAssignment` + SO 交期/period 聚合

- [ ] Unit test with fixture allocations → ChangeSet ops count

---

## Epic M2-B: Session optimize + confirm

### Task B.1: optimize endpoint

**Files:**
- Modify: `MasterPlanOntologySessionService.java` — `optimize(sessionId, strategyId?)`
- Modify: `MasterPlanSessionResource.java` — `POST /{id}/optimize`
- DTO: `MasterPlanSessionOptimizeResultDto(sessionId, score, allocationCount, solveDurationMs)`

Flow:
```
session → OntologyTimefoldBridge.buildContext → solve in memory → toChangeSet → RolTransaction.apply → update session graph
```

- [ ] `@QuarkusTest` optimize returns non-null score on sample plan version

### Task B.2: confirm 持久化

**Files:**
- Modify: `MasterPlanOntologySessionService.confirm` — 复用 `MasterPlanService.persist*` 逻辑或提取 `MasterPlanPersistenceService`
- 写入：`PlanVersionEntity` 子版本 + `MasterPlanAllocationEntity` 从 session 内 assignment 视图

- [ ] Integration test: confirm 后 `MasterPlanAllocationEntity.count(planVersionId) > 0`

---

## Epic M2-C: 前端最小联动（可选 M2.1）

- `frontend/src/api/client.ts` — masterPlanSession API
- 主计划页或新 Tab：PISPP 表格 + simulate 按钮（调用已有 REST）

---

## 执行顺序

1. M2-A（ChangeSet + Bridge）  
2. M2-B（optimize + confirm）  
3. M2-C（前端，可并行 B.2 之后）

---

## Execution Handoff

**Plan saved to:** `docs/superpowers/plans/2026-06-07-otd-ontology-m2-timefold-bridge.md`

**Prerequisite:** Push `feature/otd-ontology-m1` and merge when network available.

**Next task:** M2-A Task A.1 (ChangeSet model)
