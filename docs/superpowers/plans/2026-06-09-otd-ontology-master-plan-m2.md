# OTD 主计划本体 M2 — 供需联动 + Timefold 桥接 + confirm 持久化 + 前端视图

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M1（内存本体图 + ROL-lite + Session）基础上，把真实供需聚合进 PISPP，打通 `optimize`（Timefold → ChangeSet → 本体传播），实现 `confirm` 持久化到 `MasterPlanAllocationEntity`，并在前端用 PISPP 库存曲线 + simulate 交互展示。

**Architecture:** 复用现有 `MasterPlanProblemMapper` / `MasterPlanService` 的 Timefold 求解能力；新增 `OntologyTimefoldMapper` 做「本体视图 ↔ 求解模型」双向投影，求解结果转为 ChangeSet 经 `RolEngine` 应用并触发 PISPP 传播。`confirm` 仍写现有 `MasterPlanAllocationEntity`（D4）。前端新增主计划本体 Session 面板。

**Tech Stack:** Java 21, Quarkus, Timefold 3.x, JUnit 5；React + 现有 `frontend/src`。

**Prerequisite finding (M1 复盘):** `OntologyLoader.buildGraph` 仅把期初库存写入 PISPP[0]，未将 `SupplyOrder.quantity`、销售需求按日期聚合进 `plannedSupplyTotal` / `plannedDemandQuantityTotal`。M2 Epic A 先补这一层，否则 optimize/曲线无业务意义。

**Locked decisions (继承 M1 + M2 新增):**

| # | 决策 | 值 |
|---|------|-----|
| D1 | StockingPoint | 单 `DEFAULT-FG`（不变） |
| D2 | WorkOrder/SupplyOrder | 双对象（不变） |
| D4 | confirm 持久化 | 写 `MasterPlanAllocationEntity`（M2 实现） |
| D5 | optimize 来源 | **复用** 现有 `MasterPlanService` 求解产物，不重写 Timefold 模型 |
| D6 | ChangeSet 应用 | 经 `RolEngine` + `RolTransaction`，传播到 PISPP |
| D7 | 供需聚合粒度 | 按 `dueDate/needDate` 落到所属 Period（28 日桶；超出 horizon 落末桶或忽略，见 A.1） |
| D8 | 前端范围 | 只读曲线 + simulate；optimize 触发 + 结果刷新；confirm 按钮 |

**M2 验收（约 4–6 周）：**

1. PISPP 的 supply/demand 来自真实 SupplyOrder/销售需求，滚动库存曲线非平凡
2. `POST /master-plan/sessions/{id}/optimize` 返回求解后受影响 PISPP 快照
3. `POST .../confirm` 写入 `MasterPlanAllocationEntity` 并返回 `planVersionId`
4. `StandardResourcePeriod.freeCapacity` derived 单测通过
5. 前端主计划本体页：PISPP 曲线 + simulate 改值联动 + optimize/confirm 按钮
6. 全量后端测试无 M2 引入回归

**Related:** [M1 计划](./2026-06-07-otd-ontology-master-plan.md)、[otd-ontology-mapping.md](../../otd-ontology-mapping.md)、[aps-planning-layer.md](../../aps-planning-layer.md)

---

## File structure (M2)

```
src/main/java/com/plantops/
  ontology/
    OntologyLoader.java                 (modify: 供需聚合 + period 落桶)
    period/
      StandardResourcePeriod.java       (new)
    supply/
      SupplyOrder.java                  (modify: 确保 dueDate/quantity 可读)
  rol/
    PispPeriodDerivations.java          (modify: demand/supply 输入说明)
    SrpCapacityDerivations.java         (new: free_capacity 规则)
    ChangeSet.java                      (new, 若 M1 未建)
    RolTransaction.java                 (确认存在/补全)
  scenario/planning/
    OntologyTimefoldMapper.java         (new)
    OntologyChangeSetFactory.java       (new: allocation → ChangeSet)
    MasterPlanOntologySessionService.java (modify: optimize + confirm)
    MasterPlanOntologyConfirmService.java (new: 写 MasterPlanAllocationEntity)
  api/
    MasterPlanSessionResource.java      (modify: optimize endpoint 已有则接线)
    dto/planning/
      MasterPlanSessionOptimizeResultDto.java   (确认/补全)
      MasterPlanSessionConfirmResultDto.java     (new)

frontend/src/
  types/ontology.ts                     (new)
  api/client.ts                         (modify: masterPlanSessions client)
  pages/MasterPlanOntologyPage.tsx      (new)
  components/PispInventoryChart.tsx     (new)

src/test/java/com/plantops/
  ontology/OntologyLoaderSupplyDemandTest.java   (new)
  rol/SrpCapacityDerivationTest.java             (new)
  scenario/planning/OntologyTimefoldMapperTest.java (new)
  scenario/planning/MasterPlanOntologyConfirmServiceTest.java (new)
```

---

## Epic A: 供需聚合进 PISPP（前置）

### Task A.1: SupplyOrder/需求按 Period 聚合

**Files:**
- Modify: `src/main/java/com/plantops/ontology/OntologyLoader.java`
- Modify: `src/main/java/com/plantops/ontology/supply/SupplyOrder.java`（若缺 dueDate/quantity getter）
- Test: `src/test/java/com/plantops/ontology/OntologyLoaderSupplyDemandTest.java`

- [ ] **Step 1: Write failing @QuarkusTest** — 加载含工单与销售行的样例工作区，断言：
  - 某 productCode 的 `PISPP` 在 SupplyOrder.dueDate 所属 period 上 `plannedSupplyTotal > 0`
  - 销售需求 needDate 所属 period 上 `plannedDemandQuantityTotal > 0`
  - 滚动后 `PISPP[last].plannedInventoryLevel` = 期初 + Σsupply − Σdemand

```java
@QuarkusTest
class OntologyLoaderSupplyDemandTest {
    @Inject OntologyLoader loader;

    @Test
    @TestTransaction
    void supplyAndDemandAggregateIntoPispByPeriod() {
        // sample data loaded by SampleDataLoader; pick a known productCode
        OntologyGraph g = loader.loadForWorkspace(LocalDate.now());
        // assert at least one PISPP has supply>0 and one has demand>0
        boolean anySupply = g.pispPeriodsById().values().stream()
                .anyMatch(p -> p.getPlannedSupplyTotal() > 0);
        boolean anyDemand = g.pispPeriodsById().values().stream()
                .anyMatch(p -> p.getPlannedDemandQuantityTotal() > 0);
        assertTrue(anySupply, "expected some PISPP supply from work orders");
        assertTrue(anyDemand, "expected some PISPP demand from sales orders");
    }
}
```

- [ ] **Step 2: Run — FAIL**

Run (PowerShell): `cd d:\AILab\PlantOperationPlan\plant-operation-plan; ./mvnw -q test "-Dtest=OntologyLoaderSupplyDemandTest"`

- [ ] **Step 3: Implement aggregation in `buildGraph`**

新增私有方法：
- `periodIndexForDate(LocalDate date, LocalDate planningStart)`：`days = date - planningStart`；`<0 → 0`；`>=COUNT → COUNT-1`（落末桶，A.1 规则）
- 遍历 `SupplyOrder`（已在 graph）：按 `productCode` → pispId，按 `dueDate` → period index，累加 `plannedSupplyTotal`
- 遍历销售需求 `SalesOrderLineEntity.listInWorkspace()`（非 CANCELLED）：按 `productCode` + `dueDate` 累加 `plannedDemandQuantityTotal`
- 聚合后对每个 PISP 调 `PispRolling.rollChain(orderedPisppForPisp)` 完成滚动

注意：聚合发生在 PISPP 构建之后、`build()` 之前；保持 period 0 期初 on-hand 逻辑。

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Run regression** — `./mvnw -q test "-Dtest=OntologyLoaderTest,ProductInStockingPointPeriodTest,MasterPlanOntologySessionServiceTest"`

---

## Epic B: StandardResourcePeriod + 产能 derived

### Task B.1: SRP 模型与 free_capacity 规则

**Files:**
- Create: `src/main/java/com/plantops/ontology/period/StandardResourcePeriod.java`
- Create: `src/main/java/com/plantops/rol/SrpCapacityDerivations.java`
- Modify: `src/main/java/com/plantops/ontology/OntologyGraph.java`（加 srpById 索引 + builder）
- Test: `src/test/java/com/plantops/rol/SrpCapacityDerivationTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void freeCapacityIsAvailableMinusReserved() {
    var srp = new StandardResourcePeriod("SRP-1", "RES-1", "P-0");
    srp.setTotalCapacity(480);
    srp.setCalendarDowntime(60);
    srp.setTechnicalDowntime(0);
    srp.setReservedCapacity(120);
    srp.recalculateCapacityFields();
    assertEquals(420, srp.getAvailableCapacity(), 1e-6);   // 480-60-0
    assertEquals(300, srp.getFreeCapacity(), 1e-6);        // 420-120
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement** `StandardResourcePeriod`（字段对齐 OTD v4.0 §SRP）+ `recalculateCapacityFields()`：

```
available_capacity = total_capacity - calendar_downtime - technical_downtime
free_capacity = available_capacity - reserved_capacity
overload_capacity = max(0, reserved_capacity - available_capacity)
```

`SrpCapacityDerivations` 注册到 `DerivationRegistry`（同 PISPP 模式，单对象规则，无跨对象边）。

- [ ] **Step 4: Run — PASS**

> M2 不要求 SRP 进入 Session 装载（loader 暂不建 SRP）；本 Epic 仅打底 derived，供 Epic C optimize 回写 reserved_capacity 时使用。若时间紧可将 loader SRP 装载延后到 M3。

---

## Epic C: Timefold 桥接 — optimize

### Task C.1: OntologyTimefoldMapper（本体 → 求解输入）

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/OntologyTimefoldMapper.java`
- Test: `src/test/java/com/plantops/scenario/planning/OntologyTimefoldMapperTest.java`

- [ ] **Step 1: Decide bridge strategy（写入计划，不写代码）**

M2 采用 **复用** 策略（D5）：optimize 不从本体重建 Timefold 模型，而是：
1. 调用现有 `MasterPlanService` 对 `basePlanVersionId` 求解（或读取已求解 allocation）
2. `OntologyTimefoldMapper.toChangeSet(allocations, graph)` 把 allocation 的资源/时段占用映射为本体属性变更

> 这样避免重写 `MasterPlanProblemMapper`，本体作为「结果投影 + 传播」层。M3 再评估是否让本体直接驱动求解。

- [ ] **Step 2: Write failing test** — 给定一组 `MasterPlanAllocationDto`（或 `OrderAllocation`）+ graph，`toChangeSet` 产出包含「按 period 增加对应 PISP supply」的操作列表

- [ ] **Step 3: Implement `toChangeSet`**

输入：求解后的 allocation（workOrderNo/productCode/资源/分配日期）+ `OntologyGraph`
输出：`ChangeSet`（一组 `(pisppId, "plannedSupplyTotal", delta/abs)` 操作）
映射：allocation 完工日期 → period index → 该 product 的 PISPP supply。

- [ ] **Step 4: Run — PASS**

### Task C.2: ChangeSet / RolTransaction 落实

**Files:**
- Create/confirm: `src/main/java/com/plantops/rol/ChangeSet.java`、`Operation.java`、`RolTransaction.java`
- Modify: `src/main/java/com/plantops/rol/RolEngine.java`（`apply(ChangeSet)` 入口）
- Test: `src/test/java/com/plantops/rol/RolChangeSetTest.java`

- [ ] **Step 1: Write failing test** — `rolEngine.apply(changeSet)` 后，受影响 PISPP 链全部重算

- [ ] **Step 2: Implement** `ChangeSet`（List<Operation>），`RolEngine.apply` 遍历 operation → set 属性 → 标脏 → 一次性 propagate（batch-at-commit，对齐 ROL 设计 K4）

- [ ] **Step 3: Run — PASS**

### Task C.3: SessionService.optimize 接线

**Files:**
- Modify: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologySessionService.java`
- Modify: `src/main/java/com/plantops/api/MasterPlanSessionResource.java`（optimize endpoint）
- Confirm DTO: `MasterPlanSessionOptimizeResultDto`
- Test: `src/test/java/com/plantops/scenario/planning/MasterPlanOntologySessionServiceTest.java`（加 optimize 用例）

- [ ] **Step 1: Write failing @QuarkusTest** — create session → optimize → 返回非空受影响 PISPP 快照

- [ ] **Step 2: Implement optimize**

```
optimize(sessionId):
  session = store.require(sessionId, ws)
  allocations = masterPlanService.<solve or fetch>(session.basePlanVersionId)
  changeSet = ontologyTimefoldMapper.toChangeSet(allocations, session.graph())
  session.rolEngine().apply(changeSet)
  return affected PISPP snapshots
```

- [ ] **Step 3: REST** `POST /api/v1/master-plan/sessions/{id}/optimize`

- [ ] **Step 4: Run — PASS**

---

## Epic D: confirm 持久化

### Task D.1: confirm → MasterPlanAllocationEntity

**Files:**
- Create: `src/main/java/com/plantops/scenario/planning/MasterPlanOntologyConfirmService.java`
- Create: `src/main/java/com/plantops/api/dto/planning/MasterPlanSessionConfirmResultDto.java`
- Modify: `MasterPlanOntologySessionService.confirm`（去掉 501，调用 confirm service）
- Modify: `MasterPlanSessionResource`（confirm 返回 result DTO）
- Test: `src/test/java/com/plantops/scenario/planning/MasterPlanOntologyConfirmServiceTest.java`

- [ ] **Step 1: Write failing @QuarkusTest + @TestTransaction** — confirm 后 `MasterPlanAllocationEntity.count(planVersionId)` > 0，返回 `planVersionId`

- [ ] **Step 2: Implement**

confirm 策略（M2 最小）：
- 复用 optimize 的 allocation 结果（或在 confirm 内重求解）
- 写入新的 `planVersionId`（沿用 `MasterPlanService` 现有持久化路径；优先复用其已有方法而非重写）
- 返回 `{ planVersionId, allocationCount }`
- 标记 session 为 confirmed（可选：store 移除或置状态）

> 若 `MasterPlanService` 已有「求解并持久化」入口，confirm 直接委托它，本 service 仅做 session→version 关联与返回包装，避免重复持久化逻辑。

- [ ] **Step 3: Run — PASS**

- [ ] **Step 4:** 更新 `docs/otd-ontology-mapping.md` D4 行：confirm 状态由「暂缓」改「实现」

---

## Epic E: 前端 PISPP 视图

### Task E.1: 类型与 API client

**Files:**
- Create: `frontend/src/types/ontology.ts`
- Modify: `frontend/src/api/client.ts`

- [ ] **Step 1:** 定义 TS 类型：`MasterPlanSession`, `PispPeriodSnapshot`, `SimulateRequest`, `OptimizeResult`, `ConfirmResult`（对齐后端 DTO 字段）

- [ ] **Step 2:** `api.masterPlanSessions = { create, get, simulate, optimize, confirm }` 指向 `/api/v1/master-plan/sessions*`

### Task E.2: PISPP 曲线 + Session 页

**Files:**
- Create: `frontend/src/components/PispInventoryChart.tsx`
- Create: `frontend/src/pages/MasterPlanOntologyPage.tsx`
- Modify: `frontend/src/App.tsx`（路由）、`frontend/src/components/Layout.tsx`（导航入口，按现有模式）

- [ ] **Step 1:** `PispInventoryChart` — 按 period 序列画 `plannedInventoryLevel` 折线 + `stockShortageQuantity` 标记（复用现有图表/SVG 模式，勿引新依赖除非已有）

- [ ] **Step 2:** `MasterPlanOntologyPage` —
  - 选 planVersion → create session
  - PISP 列表（左）+ 选中 PISP 的 PISPP 曲线（右）
  - simulate：改某 period 的 supply/demand → 局部刷新曲线
  - optimize 按钮 → 刷新
  - confirm 按钮 → 显示返回的 planVersionId

- [ ] **Step 3:** 前端构建验证 `cd frontend; npm run build`（或项目既有命令）

---

## Epic F: 回归与文档

### Task F.1: 全量回归 + 文档

- [ ] **Step 1:** `./mvnw -q test`（注意 M1 已知端口 8081 占用为环境问题，非回归）；记录通过数
- [ ] **Step 2:** 更新 `docs/aps-planning-layer.md` §5.7：补 optimize/confirm 行为与新前端页
- [ ] **Step 3:** 更新 `docs/otd-ontology-mapping.md`：SupplyOrder→PISPP 聚合、SRP、confirm 状态
- [ ] **Step 4:** 更新 `docs/scheduling-domain-model.md`：`OntologyTimefoldMapper`、confirm 路径

---

## M3 预告（本计划不实施）

| 项 | 内容 |
|----|------|
| 本体直驱求解 | 本体作为 Timefold problem facts 来源（替代复用策略） |
| SRP 进 Session | loader 装载资源时段 + optimize 回写 reserved_capacity |
| Operation 时间窗 derived | earliest/latest_possible 链 |
| PeriodSequence | 日→周→月混合桶替代固定 28 日 |
| S05 合并 | `SchedulingSession` 迁入统一 `OntologySandbox` |

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| optimize 重复求解耗时 | 优先复用 session.basePlanVersionId 已求解 allocation；必要时缓存 |
| confirm 与现有 MasterPlanService 持久化重复/冲突 | 委托现有持久化入口，不另写一套 |
| 供需落桶超出 horizon | A.1 规则：超出落末桶；mapping 文档标注 |
| 前端引入新图表依赖 | 优先复用现有甘特/SVG；禁止无谓新依赖 |
| ChangeSet 语义（delta vs abs） | 单测固定为「set 绝对值」，allocation 聚合后整体覆盖该 period supply |

---

## Self-Review

| Spec 要求 | 任务 |
|-----------|------|
| 供需聚合 PISPP | Epic A |
| SRP free_capacity | Epic B |
| optimize → ChangeSet → 传播 | Epic C |
| confirm 持久化 | Epic D |
| 前端曲线 + simulate | Epic E |
| 回归无 M2 退化 | Epic F |

Placeholder scan：无 TBD 实现步骤；M3 项仅在预告表。

---

## Execution Handoff

**Plan saved to:** `docs/superpowers/plans/2026-06-09-otd-ontology-master-plan-m2.md`

**推荐顺序：** A（前置）→ B → C → D → E → F。A 与 B 可部分并行；C 依赖 A；D 依赖 C；E 依赖 D 的 DTO。

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每 Task 派子 agent + 审查
2. **Inline Execution** — 本会话连续实施

**Which approach?**
