# OTD 主计划本体 + ROL-lite + Session 骨架 — M1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Quarkus 主计划（S04）上落地 M1 里程碑：OTD 映射基线、PISPP 传播 PoC、带 workspace 隔离的 `MasterPlanOntologySession` 内存 API；为后续 Timefold ChangeSet 回写打底。

**Architecture:** 路线 2（平衡）：S04 新建 `com.plantops.ontology` + `com.plantops.rol`（ROL-lite），与 S05 `SchedulingSession` 分叉演进；`OntologyLoader` 从现有 JPA 装载并 synthetic PISP；Session 绑定 `workspaceId`，confirm 仍投影 `MasterPlanAllocationEntity`。OTD Python (`d:\AILab\OTD\src\`) 仅作语义对照，不引入运行时依赖。

**Tech Stack:** Java 21, Quarkus, JUnit 5, 现有 Panache/JPA 实体；暂不接 Timefold（M1 范围外）。

**Locked decisions (2026-06-07):**

| # | 决策 | 值 |
|---|------|-----|
| D1 | StockingPoint 初版 | 单默认 SP：`DEFAULT-FG` |
| D2 | WorkOrder ↔ SupplyOrder | 双对象 + 映射（WO = SO 执行投影） |
| D3 | 首批 derived | M1 仅实现 **PISPP 滚动链**；SRP/Operation 时间窗列入 M2 |
| D4 | confirm 持久化 | 仍写 `MasterPlanAllocationEntity`（M1 Session 仅 create/simulate，confirm 占位） |
| 隔离 | Session | `workspaceId` 硬隔离；同 Session 内多 Customer 共存 |

**Related:** [OTD-Product-Design-v4.0.md](d:/AILab/OTD/docs/current/04-technical-reference/OTD-Product-Design-v4.0.md), [aps-planning-layer.md](../../aps-planning-layer.md), [2026-06-02-workshop-scheduling-layered-architecture.md](./2026-06-02-workshop-scheduling-layered-architecture.md)

**M1 验收（约 4 周）：**

1. `docs/otd-ontology-mapping.md` 评审通过  
2. PISPP 传播单测：3 Period 改 supply → 链式重算，P95 < 10ms（单线程、1000 次）  
3. `POST /api/v1/master-plan/sessions` + `POST .../simulate` 可创建内存 Session 并返回 PISPP 快照  
4. `SchedulingSessionStore` 跨 workspace 访问返回 404  
5. 现有 S04 集成测试仍 PASS（无行为回归）

---

## File structure (M1)

```
src/main/java/com/plantops/
  ontology/
    OntologyIds.java
    OntologyGraph.java
    OntologyLoader.java
    master/
      Product.java
      StockingPoint.java
      ProductInStockingPoint.java
    period/
      Period.java
      ProductInStockingPointPeriod.java
    supply/
      SupplyOrder.java
      SupplyOrderStatus.java
      SupplyOrderType.java
      WorkOrderSupplyOrderMapper.java
  rol/
    Derivation.java
    DerivationRegistry.java
    DependencyGraph.java
    DirtySet.java
    Propagator.java
    RolEngine.java
  scenario/planning/
    MasterPlanOntologySession.java
    MasterPlanOntologySessionStore.java
    MasterPlanOntologySessionService.java
  api/
    MasterPlanSessionResource.java
    dto/planning/
      CreateMasterPlanSessionRequest.java
      MasterPlanSessionDto.java
      MasterPlanSessionSimulateResultDto.java
      PispPeriodSnapshotDto.java

src/test/java/com/plantops/
  ontology/period/ProductInStockingPointPeriodTest.java
  rol/PispRollingDerivationTest.java
  rol/PispPropagationBenchmarkTest.java
  scenario/planning/MasterPlanOntologySessionStoreTest.java
  scenario/planning/MasterPlanOntologySessionServiceTest.java

docs/
  otd-ontology-mapping.md
```

---

## Epic 0: 设计基线

### Task 0.1: OTD 映射文档

**Files:**
- Create: `docs/otd-ontology-mapping.md`

- [ ] **Step 1:** 写入 MPS 最小集对象表（~25 行），列：OTD 对象 | Java 本体类 | JPA 实体 | Solver 类 | 前端 DTO | M1 状态（实现/映射/暂缓）

必含映射示例：

| OTD | 本体 (M1) | JPA | Solver | 备注 |
|-----|-----------|-----|--------|------|
| ProductInStockingPoint | `ProductInStockingPoint` | `MaterialEntity` + synthetic SP | — | D1: productCode + DEFAULT-FG |
| SupplyOrder | `SupplyOrder` | `WorkOrderEntity` | `OrderAllocation` (M2) | D2: 双对象 |
| PISPP | `ProductInStockingPointPeriod` | — (内存) | — | M1 PoC |
| ResourceAssignment | — | `MasterPlanAllocationEntity` | `OrderAllocation` | M2 |

- [ ] **Step 2:** 写入首批 derived 规则表（M1 实现 PISPP 三条公式；M2 列 SRP/Operation）

- [ ] **Step 3:** 写入 Session 状态机 ASCII 图：`create → simulate → [optimize M2] → [confirm M2]`

- [ ] **Step 4:** 团队评审签字（Issue/PR 描述贴链接）

---

## Epic 1: S05 Session workspace 修补（独立小 PR，可与 Epic 2 并行）

### Task 1.1: SchedulingSession 绑定 workspaceId

**Files:**
- Modify: `src/main/java/com/plantops/scenario/planning/SchedulingSession.java`
- Modify: `src/main/java/com/plantops/scenario/planning/SchedulingSessionStore.java`
- Modify: `src/main/java/com/plantops/scenario/DetailScheduleSessionService.java` (create 时 stamp workspace)
- Test: `src/test/java/com/plantops/scenario/planning/SchedulingSessionStoreTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.plantops.scenario.planning;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.workspace.WorkspaceConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulingSessionStoreTest {

    SchedulingSessionStore store;

    @BeforeEach
    void setUp() {
        store = new SchedulingSessionStore();
    }

    @Test
    void requireRejectsWrongWorkspace() {
        SchedulingSession session = new SchedulingSession(
                "SS-TEST",
                WorkspaceConstants.DEFAULT_ID,
                "MPV-1",
                LocalDate.now(),
                new DetailSchedule(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                false,
                null,
                null,
                null);
        store.put(session);
        assertThrows(Exception.class, () -> store.require("SS-TEST", "other-workspace"));
    }
}
```

- [ ] **Step 2: Run test — expect FAIL** (constructor / require 签名不存在)

Run: `./mvnw -q test -Dtest=SchedulingSessionStoreTest`

- [ ] **Step 3: Implement**

`SchedulingSession` 增加字段 `workspaceId`，新构造函数参数顺序：`sessionId, workspaceId, masterPlanVersionId, ...`；旧构造函数委托到 `WorkspaceResolver.currentWorkspaceId()`（或测试里显式传）。

`SchedulingSessionStore.require(String sessionId, String workspaceId)`：
- session 不存在 → `NotFoundException`
- `!session.workspaceId().equals(workspaceId)` → `NotFoundException`（不泄露跨 workspace 存在性）
- expired → 删除并 404

`DetailScheduleSessionService.create`：`WorkspaceResolver.currentWorkspaceId()` 写入 session。

- [ ] **Step 4: Run test — PASS**

Run: `./mvnw -q test -Dtest=SchedulingSessionStoreTest`

- [ ] **Step 5: Fix compile errors** in all `SchedulingSession` call sites (grep `new SchedulingSession`)

- [ ] **Step 6: Run related tests**

Run: `./mvnw -q test -Dtest=DetailScheduleSessionServiceTest`

---

## Epic 2: 本体 POJO + OntologyGraph

### Task 2.1: Period 与 PISPP 纯模型

**Files:**
- Create: `src/main/java/com/plantops/ontology/OntologyIds.java`
- Create: `src/main/java/com/plantops/ontology/period/Period.java`
- Create: `src/main/java/com/plantops/ontology/period/ProductInStockingPointPeriod.java`
- Test: `src/test/java/com/plantops/ontology/period/ProductInStockingPointPeriodTest.java`

- [ ] **Step 1: Write failing test — recalculatePlanningFields**

```java
package com.plantops.ontology.period;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductInStockingPointPeriodTest {

    @Test
    void plannedInventoryLevelFromOnHandSupplyDemand() {
        var p = new ProductInStockingPointPeriod("PISPP-1", "PISP-FG-100", "P-0");
        p.setOnHand(100);
        p.setPlannedSupplyTotal(50);
        p.setPlannedDemandQuantityTotal(30);
        p.recalculatePlanningFields();
        assertEquals(120, p.getPlannedInventoryLevel(), 1e-6);
        assertEquals(150, p.getReplenishedInventoryLevel(), 1e-6);
    }

    @Test
    void stockShortageWhenBelowTarget() {
        var p = new ProductInStockingPointPeriod("PISPP-1", "PISP-FG-100", "P-0");
        p.setOnHand(10);
        p.setPlannedSupplyTotal(0);
        p.setPlannedDemandQuantityTotal(50);
        p.setInventoryTargetQuantity(20);
        p.recalculatePlanningFields();
        assertEquals(60, p.getStockShortageQuantity(), 1e-6);
    }
}
```

- [ ] **Step 2: Run — FAIL**

Run: `./mvnw -q test -Dtest=ProductInStockingPointPeriodTest`

- [ ] **Step 3: Implement** `Period`（id, sequenceNr, startDate, endDate）与 `ProductInStockingPointPeriod`：

字段对齐 OTD v4.0 §PISPP；方法 `recalculatePlanningFields()` 实现：

```
planned_inventory_level = on_hand + planned_supply_total - planned_demand_quantity_total
replenished_inventory_level = on_hand + planned_supply_total
stock_shortage_quantity = max(0, planned_demand_quantity_total + inventory_target_quantity - replenished_inventory_level)
```

- [ ] **Step 4: Run — PASS**

### Task 2.2: PISP 滚动链（Period 间 on_hand 传播）

**Files:**
- Modify: `src/main/java/com/plantops/ontology/period/ProductInStockingPointPeriod.java` — 添加 static helper
- Create: `src/main/java/com/plantops/rol/PispRolling.java`
- Test: extend `ProductInStockingPointPeriodTest`

- [ ] **Step 1: Write failing test — roll forward**

```java
@Test
void rollOnHandFromPreviousPlannedLevel() {
    var p0 = new ProductInStockingPointPeriod("PP-0", "PISP-1", "P-0");
    p0.setOnHand(100);
    p0.setPlannedSupplyTotal(40);
    p0.setPlannedDemandQuantityTotal(30);
    p0.recalculatePlanningFields();

    var p1 = new ProductInStockingPointPeriod("PP-1", "PISP-1", "P-1");
    PispRolling.rollChain(List.of(p0, p1));
    assertEquals(110, p1.getOnHand(), 1e-6); // PL[0]=110
    p1.setPlannedSupplyTotal(0);
    p1.setPlannedDemandQuantityTotal(20);
    p1.recalculatePlanningFields();
    assertEquals(90, p1.getPlannedInventoryLevel(), 1e-6);
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement `PispRolling.rollChain(List<ProductInStockingPointPeriod> ordered)`**

从 index 1 起：`p[i].onHand = p[i-1].plannedInventoryLevel`（先 ensure p[i-1] 已 recalculate）；然后 `p[i].recalculatePlanningFields()`。

- [ ] **Step 4: Run — PASS**

### Task 2.3: 主数据最小集 + OntologyGraph

**Files:**
- Create: `src/main/java/com/plantops/ontology/master/Product.java`
- Create: `src/main/java/com/plantops/ontology/master/StockingPoint.java`
- Create: `src/main/java/com/plantops/ontology/master/ProductInStockingPoint.java`
- Create: `src/main/java/com/plantops/ontology/supply/SupplyOrder.java` (+ enums)
- Create: `src/main/java/com/plantops/ontology/supply/WorkOrderSupplyOrderMapper.java`
- Create: `src/main/java/com/plantops/ontology/OntologyGraph.java`
- Create: `src/main/java/com/plantops/ontology/OntologyLoader.java`
- Test: `src/test/java/com/plantops/ontology/OntologyLoaderTest.java`

- [ ] **Step 1: Write failing test** — loader 从测试 fixture WorkOrder 构建 SupplyOrder + synthetic PISP

使用 `@QuarkusTest` + `@Transactional` + sample data 或 test builder；断言：
- 每个 distinct `productCode` 有 PISP id `PISP-{productCode}-DEFAULT-FG`
- 每个 open WorkOrder 映射为 SupplyOrder，id = workOrderNo

- [ ] **Step 2–4: Implement loader**（读 `WorkOrderEntity.listInWorkspace()`, `MaterialEntity`, `InventoryEntity`）

D1：`StockingPoint.DEFAULT_FG = "DEFAULT-FG"`  
D2：`WorkOrderSupplyOrderMapper.toSupplyOrder(WorkOrderEntity wo)`

- [ ] **Step 5: Run OntologyLoaderTest — PASS**

---

## Epic 3: ROL-lite 传播引擎（PISPP 专用）

### Task 3.1: DerivationRegistry + Propagator

**Files:**
- Create: `src/main/java/com/plantops/rol/Derivation.java`
- Create: `src/main/java/com/plantops/rol/DerivationRegistry.java`
- Create: `src/main/java/com/plantops/rol/DependencyGraph.java`
- Create: `src/main/java/com/plantops/rol/DirtySet.java`
- Create: `src/main/java/com/plantops/rol/Propagator.java`
- Create: `src/main/java/com/plantops/rol/RolEngine.java`
- Create: `src/main/java/com/plantops/rol/PispPeriodDerivations.java`
- Test: `src/test/java/com/plantops/rol/PispRollingDerivationTest.java`

- [ ] **Step 1: Write failing test — dirty PISPP supply triggers chain**

```java
@Test
void changingSupplyTotalRollsSubsequentPeriods() {
    var graph = buildThreePeriodGraph(); // helper: 1 PISP, 3 periods
    RolEngine engine = RolEngine.withDefaultPispRules();
    var p1 = graph.pispPeriod("P-1");
    engine.applyPropertyChange(p1, "plannedSupplyTotal", 100.0);
    assertEquals(100, graph.pispPeriod("P-2").getOnHand(), 1e-6);
}
```

- [ ] **Step 2: Implement**

`Derivation` record: `(targetType, property, sources, BiConsumer<Object, OntologyGraph> recompute)`

M1 注册规则：
1. `ProductInStockingPointPeriod.plannedInventoryLevel` ← onHand, supply, demand
2. `ProductInStockingPointPeriod.replenishedInventoryLevel` ← onHand, supply
3. `ProductInStockingPointPeriod.stockShortageQuantity` ← demand, target, replenished
4. `ProductInStockingPointPeriod.onHand` ← **previous period**.plannedInventoryLevel（跨对象边）

`Propagator.propagate(DirtySet, graph)`：拓扑序 batch 重算；环检测在 registry 构建时抛出 `IllegalStateException`。

`RolEngine.applyPropertyChange(node, property, value)`：set + mark dirty + propagate。

- [ ] **Step 3: Run — PASS**

### Task 3.2: 性能基准

**Files:**
- Create: `src/test/java/com/plantops/rol/PispPropagationBenchmarkTest.java`

- [ ] **Step 1: Benchmark test** — 28 periods × 100 products，单次改 supply，1000 次传播

```java
@Test
void propagationP95Under10ms() {
    // build graph: 100 PISPs × 28 periods
    // warmup 100, measure 1000, assert p95 < 10ms
}
```

- [ ] **Step 2: Run — tune if needed**（通常纯内存 Java 应远低于 10ms；若超则减少对象分配）

Run: `./mvnw -q test -Dtest=PispPropagationBenchmarkTest`

---

## Epic 4: MasterPlanOntologySession

### Task 4.1: Session 模型 + Store

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologySession.java`
- Create: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologySessionStore.java`
- Test: `src/test/java/com/plantops/scenario/planning/MasterPlanOntologySessionStoreTest.java`

- [ ] **Step 1: Write failing test** — workspace 隔离（同 Task 1.1 模式）

- [ ] **Step 2: Implement**

```java
public final class MasterPlanOntologySession {
    private final String sessionId;
    private final String workspaceId;
    private final String basePlanVersionId;
    private final OntologyGraph graph;
    private final RolEngine rolEngine;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    // fork(), expired(now)
}
```

Store：`ConcurrentHashMap<String, MasterPlanOntologySession>` + `require(sessionId, workspaceId)` + 8h TTL。

- [ ] **Step 3: Run — PASS**

### Task 4.2: SessionService — create + simulate

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologySessionService.java`
- Create: `src/main/java/com/plantops/api/dto/planning/CreateMasterPlanSessionRequest.java`
- Create: `src/main/java/com/plantops/api/dto/planning/MasterPlanSessionDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/MasterPlanSessionSimulateResultDto.java`
- Create: `src/main/java/com/plantops/api/dto/planning/PispPeriodSnapshotDto.java`
- Create: `src/main/java/com/plantops/api/MasterPlanSessionResource.java`
- Test: `src/test/java/com/plantops/scenario/planning/MasterPlanOntologySessionServiceTest.java`

- [ ] **Step 1: Write failing @QuarkusTest**

```java
@Test
void createAndSimulateSupplyChangeUpdatesPispChain() {
    // given planVersionId with work orders
    var created = service.create(new CreateMasterPlanSessionRequest(planVersionId, null));
    var result = service.simulate(created.sessionId(), new SimulatePispRequest(pispPeriodId, "plannedSupplyTotal", 200.0));
    assertTrue(result.recalculatedPeriodIds().size() >= 2);
}
```

- [ ] **Step 2: Implement create**

1. `OntologyLoader.loadForPlanVersion(planVersionId)` — 装载 WO→SO、Inventory→PISPP[0].onHand、生成 Period 序列（M1：从 planVersion planningStart 起 28 天日桶）
2. `RolEngine.withDefaultPispRules()`
3. sessionId = `MOS-` + UUID
4. workspaceId = `WorkspaceResolver.currentWorkspaceId()`

- [ ] **Step 3: Implement simulate**

输入：target PISPP id + property + value → `rolEngine.applyPropertyChange` → 返回 `PispPeriodSnapshotDto` 列表（变更 + 下游 period）

- [ ] **Step 4: REST**

```
POST /api/v1/master-plan/sessions          body: { planVersionId }
GET  /api/v1/master-plan/sessions/{id}     → graph summary + PISPP snapshots
POST /api/v1/master-plan/sessions/{id}/simulate
```

- [ ] **Step 5: Run QuarkusTest — PASS**

Run: `./mvnw -q test -Dtest=MasterPlanOntologySessionServiceTest`

### Task 4.3: confirm 占位（M2 预留）

**Files:**
- Modify: `MasterPlanOntologySessionService.java` — 添加 `confirm()` stub

- [ ] **Step 1:** `confirm(sessionId)` 抛出 `501 Not Implemented` + message `"M2: project to MasterPlanAllocationEntity"`

M1 不实现持久化投影，避免半成品路径。

---

## Epic 5: 回归与文档收尾

### Task 5.1: 全量回归

- [ ] **Step 1:** Run: `./mvnw -q test` — 全部 PASS

- [ ] **Step 2:** 更新 `docs/aps-planning-layer.md` — 新增 §「OTD 本体 M1」三行指向 mapping 文档与新 API

- [ ] **Step 3:** 更新 `docs/scheduling-domain-model.md` — 在 L2 层补充 `MasterPlanOntologySession` 与 `OntologyGraph`

---

## M2 预告（本计划不实施）

| 项 | 内容 |
|----|------|
| Timefold 桥接 | `OntologyTimefoldMapper` + optimize → ChangeSet |
| confirm | Session → `MasterPlanAllocationEntity` |
| derived 扩展 | SRP.free_capacity、Operation 时间窗 |
| 前端 | PISPP 曲线 + Session simulate UI |
| S05 合并 | `SchedulingSession` 迁入统一 `OntologySandbox` |

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| OntologyLoader 与 MRP 双轨 | M1 只读 WO/Inventory；PISPP 与 MaterialFeasibility 并行，M2 收敛 |
| SchedulingSession 构造函数变更面大 | Epic 1 独立 PR，先合并 |
| Period 生成与现有 TimeSlot 不一致 | M1 固定 28 日桶；mapping 文档标注差异 |
| 性能 SLA | Task 3.2 benchmark 硬门禁 |

---

## Self-Review

| Spec 要求 | 任务 |
|-----------|------|
| 映射基线 | Task 0.1 |
| PISP 中心 D1 | Task 2.3 |
| WO/SO 双对象 D2 | Task 2.3 |
| PISPP derived D3 | Task 2.1–2.2, 3.1 |
| workspace Session 隔离 | Task 1.1, 4.1 |
| M1 不接 Timefold | confirm stub Task 4.3 |
| P95 < 10ms | Task 3.2 |

Placeholder scan: 无 TBD 实现步骤；M2 项仅在预告表。

---

## Execution Handoff

**Plan saved to:** `docs/superpowers/plans/2026-06-07-otd-ontology-master-plan.md`

**推荐执行顺序：**

1. Epic 0（映射文档）与 Epic 1（workspace 修补）**并行**
2. Epic 2 → Epic 3 → Epic 4 **串行**
3. Epic 5 回归

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每个 Epic/Task 派生子 agent，Task 间审查  
2. **Inline Execution** — 本会话按 Task 0.1 → 1.1 → 2.1… 连续实施

**Which approach?**
