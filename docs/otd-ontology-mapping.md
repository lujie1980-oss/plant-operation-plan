# OTD 本体 ↔ Plant Operation Plan 映射基线（M1–M3）

> **版本：** M1 设计基线 + M2/M3 实施同步（Epic 0 / Task 0.1；M3 见 [2026-06-10-otd-ontology-master-plan-m3.md](./superpowers/plans/2026-06-10-otd-ontology-master-plan-m3.md)）  
> **关联计划：** [2026-06-07-otd-ontology-master-plan.md](./superpowers/plans/2026-06-07-otd-ontology-master-plan.md)  
> **领域参考：** [scheduling-domain-model.md](./scheduling-domain-model.md)、[aps-planning-layer.md](./aps-planning-layer.md)  
> **OTD 语义对照：** `d:\AILab\OTD\docs\current\04-technical-reference\OTD-Product-Design-v4.0.md`（只读，无运行时依赖）

本文档定义 **MPS（主生产计划）最小对象集** 在 OTD 本体与现有 Java 代码之间的映射关系，作为 M1 实现与评审基线。

**图例：**

| M1 状态 | 含义 |
|---------|------|
| **实现** | M1 新建 `com.plantops.ontology.*` POJO + 装载/传播逻辑 |
| **映射** | 不新建本体类，直接投影或经 Mapper 映射现有实体/求解类 |
| **暂缓** | M2 及以后；M1 文档占位 |

**包约定（M1 计划）：**

| 层 | 包 |
|----|-----|
| 本体 POJO | `com.plantops.ontology.*` |
| ROL-lite 传播 | `com.plantops.rol.*` |
| Session | `com.plantops.scenario.planning.MasterPlanOntologySession*` |
| 持久化 | `com.plantops.persistence.entity.*` |
| 求解投影 | `com.plantops.solver.masterplan.*` / `detailschedule.*` |
| API DTO | `com.plantops.api.dto.*` |

---

## 1. MPS 最小对象集映射表

共 **29** 行，覆盖 M1 主计划本体 PoC 所需的主数据、供需、时段、产能与 Session 边界（**M3** 新增 PeriodSequence、StandardResourcePeriod）。

| OTD 对象名 | Java 本体类（`com.plantops.ontology.*`） | JPA 实体 | Solver 类 | 前端 DTO | M1 状态 |
|------------|------------------------------------------|----------|-------------|----------|---------|
| **Product** | `master.Product` | `MaterialEntity` | — | `MasterDataDtos.MaterialDto` | 实现 |
| **StockingPoint** | `master.StockingPoint` | —（常量 `DEFAULT-FG`，见 D1） | — | `MasterDataDtos.InventoryDto.stockingPointCode` | 实现 |
| **ProductInStockingPoint** | `master.ProductInStockingPoint` | `MaterialEntity` + synthetic SP | — | — | 实现 |
| **Period** | `period.Period` | —（内存；**M3** `PeriodSequenceSpec` 展开，系统参数 `ontology_period_sequence`，如 `"14x1d,4x1w,2x1m"`；缺省 `28×1d`） | `TimeSlot`（粒度对照，非 1:1） | — | 实现 |
| **PeriodSequence** | `period.PeriodSequenceSpec` + `period.PeriodIndex` | `SystemParameterEntity`（`ontology_period_sequence`） | — | — | **M3 实现** |
| **ProductInStockingPointPeriod** | `period.ProductInStockingPointPeriod` | —（内存 PoC） | — | `PispPeriodSnapshotDto`（M1 计划） | 实现 |
| **Customer** | — | —（`SalesOrderLineEntity.customerCode` 字段） | — | `MasterDataDtos.SalesOrderDto.customerCode` | 映射 |
| **DemandOrder** | — | `SalesOrderLineEntity` | — | `MasterDataDtos.SalesOrderDto` / `DemandPoolEntryDto` | 映射 |
| **DemandOrderLine** | — | `SalesOrderLineEntity`（单行即一行需求） | — | `DemandPoolEntryDto` | 映射 |
| **SupplyOrder** | `supply.SupplyOrder` | `WorkOrderEntity` | `OrderAllocation`（M2 桥接） | `WorkOrderDto` | 实现 |
| **WorkOrder**（执行投影） | —（D2：与 SupplyOrder 双对象） | `WorkOrderEntity` | `OrderAllocation` | `WorkOrderDto` | 映射 |
| **SupplyOrderPegging** | — | `WorkOrderPeggingEntity` | — | — | 映射 |
| **BillOfMaterial** | — | `BomComponentEntity` | `BomDependencyEdge`（求解边） | `MasterDataDtos.BomDto` | 映射 |
| **OperationDefinition** | — | `ProductResourceEntity` | — | `MasterDataDtos.ProductResourceDto` | 映射 |
| **Operation**（工序实例） | `supply.Operation` | `ProductResourceEntity`（路由源） | `OrderAllocation`（S04）/ `OperationAssignment`（S05） | `OperationSnapshotDto`（**M3** Session API）/ `DetailScheduleOperationDto` | **M3 实现** |
| **ScheduledResource** | — | `ProductionResourceEntity` | `TimeSlot.resourceId` | `MasterDataDtos.ResourceDto` | 映射 |
| **ProductionLine** | — | `ProductionLineEntity` | `ScheduleLine`（S05） | `MasterDataDtos.ProductionLineDto` | 映射 |
| **StandardResourcePeriod** | `period.StandardResourcePeriod` | `ProductionLineEntity.resourceId` + `ResourceCalendarEntity`（按 period 聚合） | `TimeSlot`（产能对照） | `SrpSnapshotDto`（**M3** Session API） | **M3 实现** |
| **ResourceSchedulingPeriod** | —（OTD 日历源；M3 投影至 SRP） | `ResourceCalendarEntity` | `TimeSlot` | `MasterDataDtos.ResourceCalendarDto` / `LoadBucketDto` | 映射 |
| **ResourceAssignment** | — | `MasterPlanAllocationEntity` | `OrderAllocation` | `MasterPlanAllocationDto` | M2 实现（confirm） |
| **InventoryBalance** | — | `InventoryEntity` | `MaterialFeasibilityContext` | `MasterDataDtos.InventoryDto` | 映射 |
| **PlanVersion** | — | `PlanVersionEntity` | — | —（`MasterPlanResultDto.versionId` 等字段） | 映射 |
| **Workspace** | — | `WorkspaceEntity` | — | — | 映射 |
| **MaterialFeasibilitySnapshot** | — | — | `MaterialFeasibilityContext` | `MasterPlanPlanningDiagnosticsDto` | 映射 |
| **KittingResult** | — | `KittingResultEntity` | —（S05 推演字段 `kittingEligible`） | `ProductionBatchKittingDto` | 映射 |
| **MasterPlanPlanningContext** | — | — | `MasterPlanSchedule`（Problem 输入） | `MasterPlanPlanningPreviewDto` | 映射 |
| **OntologyGraph** | `OntologyGraph` | —（内存） | — | `MasterPlanSessionDto`（M1 计划） | 实现 |
| **MasterPlanOntologySession** | —（`scenario.planning`，**M3** `implements OntologySandbox`） | —（内存，8h TTL；**M3** 继承 `OntologySandboxStore`） | — | `MasterPlanSessionDto` / `MasterPlanSessionSimulateResultDto` | 实现 |
| **SchedulingSession** | —（`scenario.planning`，S05 细排；**M3** 同上 Sandbox 基类） | —（内存，8h TTL） | `DetailSchedule` | `ScheduleSessionDto` | 映射 |

### 1.1 必含映射示例（展开说明）

#### ProductInStockingPoint → synthetic from Material + DEFAULT-FG

| 项 | 值 |
|----|-----|
| OTD | `ProductInStockingPoint`（产品×库存点组合） |
| 本体 | `com.plantops.ontology.master.ProductInStockingPoint` |
| JPA | **`MaterialEntity`**（`materialCode` = 产品码）+ **synthetic** `StockingPoint.DEFAULT_FG`（`"DEFAULT-FG"`） |
| 合成 ID | `PISP-{productCode}-DEFAULT-FG`（D1） |
| 装载 | `OntologyLoader` 对每个 distinct `productCode`（来自 WO / Material / Inventory）生成一条 PISP |
| M1 状态 | **实现** |

> M1 不引入多库存点；`InventoryEntity.stockingPointCode` 在装载期初 on-hand 时过滤或归并到 `DEFAULT-FG`。

#### SupplyOrder ↔ WorkOrderEntity（双对象 D2）

| 项 | SupplyOrder（OTD 供应语义） | WorkOrder（执行语义） |
|----|----------------------------|----------------------|
| 本体 | `com.plantops.ontology.supply.SupplyOrder` | —（不单独建类） |
| JPA | **`WorkOrderEntity`** | **`WorkOrderEntity`**（同一行） |
| 映射器 | `WorkOrderSupplyOrderMapper.toSupplyOrder(WorkOrderEntity)` | 原生 JPA / `WorkOrderService` |
| Solver | M2：`OrderAllocation` 由 SO 展开 | 现有 S04 已用 `OrderAllocation` |
| DTO | `WorkOrderDto` | `WorkOrderDto` |
| 约定 | SO.id = `workOrderNo`；数量/交期/产品来自 WO 字段 | WO 保留 `sourceType`、`bomLevel`、`parentWorkOrderNo` 等制造属性 |

> **D2 决策：** OTD 侧计划供应用 `SupplyOrder` 表达；执行与 Timefold 仍沿用 `WorkOrderEntity` / `OrderAllocation`，M1 只建 Mapper，M2 做 optimize 桥接。

#### PISPP → ProductInStockingPointPeriod（内存 only，M1 PoC）

| 项 | 值 |
|----|-----|
| OTD | `ProductInStockingPointPeriod`（PISPP） |
| 本体 | `com.plantops.ontology.period.ProductInStockingPointPeriod` |
| JPA | **—**（纯内存，不落库） |
| 生成 | `OntologyLoader`：每个 PISP × N 个 `Period`（**M3** `PeriodSequenceSpec` 展开；缺省 N=28 日桶，自 `PlanVersionEntity` 规划起点） |
| date→period | **M3** `PeriodIndex.of(periods).sequenceFor(date)`（区间查找；早于首桶→0，晚于末桶→last），替代 M1/M2 日差除法 |
| 期初 | `PISPP[P-0].onHand` ← `InventoryEntity` 汇总 |
| 供应输入 | **M2 实现** — `OntologyLoader` 按 `SupplyOrder.dueDate` 聚合至 `plannedSupplyTotal` |
| 需求输入 | **M2 实现** — 销售需求按 `needDate` 聚合至 `plannedDemandQuantityTotal` |
| API | `POST .../sessions/{id}/simulate` → `PispPeriodSnapshotDto` |
| M1 状态 | **实现** |

#### ResourceAssignment → MasterPlanAllocationEntity / OrderAllocation（M2）

| 项 | 值 |
|----|-----|
| OTD | `ResourceAssignment`（资源×时段上的供应/工序分配） |
| JPA 持久化 | **`MasterPlanAllocationEntity`**（confirm 后真相源，D4） |
| Solver 运行时 | **`OrderAllocation`** + `TimeSlot`（`MasterPlanSchedule`） |
| 推演预览 | `MasterPlanPlanningPreviewAllocationDto` |
| API 结果 | `MasterPlanAllocationDto` |
| M1 | Session 仅 create/simulate PISPP |
| M2 | **实现** — `confirm` 委托 `MasterPlanService.solve()` 持久化 `MasterPlanAllocationEntity`；`OntologyTimefoldMapper` 用于 optimize |
| M3 | optimize 另按 `(resourceId, period)` 聚合 allocation `durationMinutes` → SRP `reservedCapacity`（`ChangeSet` / `TARGET_STANDARD_RESOURCE_PERIOD`） |

#### StandardResourcePeriod → ResourceCalendar 聚合（M3）

| 项 | 值 |
|----|-----|
| OTD | `StandardResourcePeriod`（资源×Period 产能桶） |
| 本体 | `com.plantops.ontology.period.StandardResourcePeriod` |
| JPA | **`ProductionLineEntity.resourceId`**（资源集合）+ **`ResourceCalendarEntity`**（`calendarDate` 经 `PeriodIndex` 归入 period；horizon 外行剔除） |
| 装载 | `totalCapacity` = Σ(available + unavailable)；`calendarDowntime` = Σ unavailable；`technicalDowntime` = 0；`recalculateCapacityFields()` |
| derived | `SrpCapacityDerivations`（`free_capacity` 等）；**M3** optimize 回写 `reservedCapacity` |
| API | `GET .../sessions/{id}/resources` → `SrpSnapshotDto` |
| 前端 | `SrpCapacityTable`（资源/周期/总产能/停机/已占用/可用/空闲/超载） |
| M3 状态 | **实现** |

#### Operation → ProductResource 工序链 + 时间窗（M3）

| 项 | 值 |
|----|-----|
| OTD | `Operation`（SupplyOrder 上的工序实例） |
| 本体 | `com.plantops.ontology.supply.Operation` |
| JPA | **`ProductResourceEntity`**（按 `productCode` 匹配 SO；`operationName` 去重、`sequenceNo` 排序） |
| 工时 | `productionTimeMinutes = setupTimeMinutes + processTimeSeconds × quantity / 60` |
| derived | `OperationTimeWindowDerivations`：`latestPossibleEnd` 自 `needDate` JIT 倒推；`earliestPossibleStart` 自 planningStart 正排；`earliest > latest` → `infeasible` |
| 传播 | `RolEngine.applySupplyOrderNeedDateChange` → needDate 变更重算整链 |
| API | `GET .../sessions/{id}/supply-orders/{soId}/operations` → `OperationSnapshotDto` |
| M3 状态 | **实现** |

---

## 2. Derived 规则表

### 2.1 M1 实现（PISPP 滚动链）

注册于 `com.plantops.rol.DerivationRegistry` / `PispPeriodDerivations`，由 `RolEngine.applyPropertyChange` 触发 `Propagator` 拓扑传播。

| 目标属性 | 依赖 | 公式 / 规则 | 实现类 |
|----------|------|-------------|--------|
| `planned_inventory_level` | `on_hand`, `planned_supply_total`, `planned_demand_quantity_total` | `on_hand + planned_supply_total - planned_demand_quantity_total` | `ProductInStockingPointPeriod.recalculatePlanningFields()` |
| `replenished_inventory_level` | `on_hand`, `planned_supply_total` | `on_hand + planned_supply_total` | 同上 |
| `stock_shortage_quantity` | `planned_demand_quantity_total`, `inventory_target_quantity`, `replenished_inventory_level` | `max(0, planned_demand + inventory_target - replenished)` | 同上 |
| `on_hand`（Period ≥ 1） | **上一 Period** 的 `planned_inventory_level` | `PISPP[i].on_hand = PISPP[i-1].planned_inventory_level` | `PispRolling.rollChain()` / ROL 跨对象边 |

**传播验收：** 修改任一 Period 的 `plannedSupplyTotal` → 下游 Period 链式重算；单线程 1000 次 P95 &lt; 10ms（见 M1 计划 Task 3.2）。

### 2.2 M2/M3 实现 / 暂缓

| 目标 | OTD 对象 | 现有代码锚点 | 状态 |
|------|----------|--------------|------|
| `free_capacity` | `StandardResourcePeriod` | `SrpCapacityDerivations`；`totalCapacity − calendarDowntime − technicalDowntime − reservedCapacity` | **M2 实现**（规则）；**M3** 装载 + optimize 回写 `reservedCapacity` |
| `earliest_possible` | `Operation` | `OperationTimeWindowDerivations`（planningStart 正排链） | **M3 实现** |
| `latest_possible` | `Operation` | `OperationTimeWindowDerivations`（needDate JIT 倒推链） | **M3 实现** |
| `infeasible` | `Operation` | `earliestPossibleStart > latestPossibleEnd` | **M3 实现** |

---

## 3. Session 状态机

M1 实现 **create → simulate**；M2 起 **optimize / confirm**；M3 扩展 optimize 回写 SRP、Operation 时间窗与混合桶。

```
                    ┌─────────────────────────────────────────────────────────┐
                    │              MasterPlanOntologySession                     │
                    │  (workspaceId 隔离, TTL 8h, 内存 OntologyGraph + RolEngine) │
                    └─────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌──────────┐    POST /sessions     ┌──────────┐    POST .../simulate    ┌──────────┐
│  (none)  │ ───────────────────►  │  CREATED │ ─────────────────────►  │ SIMULATED│
└──────────┘   OntologyLoader.load   │          │   RolEngine.applyChange │          │
              WO→SO, Inv→PISPP[0]    └──────────┘   返回 PISPP 快照       └────┬─────┘
                                              │                                  │
                                              │         ┌────────────────────────┘
                                              │         │
                                              │         ▼  [M2+]
                                              │    ┌──────────┐
                                              │    │ OPTIMIZED│  Timefold + ChangeSet（PISPP supply + **M3** SRP reserved）
                                              │    └────┬─────┘
                                              │         │
                                              │         ▼  [M2+]
                                              │    ┌──────────┐
                                              └──► │ CONFIRMED│  投影 → MasterPlanAllocationEntity
                                                   └──────────┘
                                                        │
                                                        ▼
                                                   PlanVersion 更新 / 404 expired

并行参考（S05，已有；**M3** 与本体 Session 共用 `OntologySandbox` / `OntologySandboxStore`）：
  SchedulingSession: create → simulate → confirm → DetailScheduleOperationEntity
  M1 Epic 1 / **M3 D17**：`SchedulingSessionStore` / `MasterPlanOntologySessionStore` 继承泛型 Store；`require(sessionId, workspaceId)` 硬隔离
```

| 阶段 | M1 行为 | 持久化 |
|------|---------|--------|
| create | 装载本体图 + 初始 PISPP 链 | 无 |
| simulate | 改 PISPP/SRP/Operation 属性 → ROL 传播（**M3** `RolEngine.withMasterPlanRules` = PISPP + SRP + Operation） | 无 |
| optimize | **M2 实现** — Timefold → ChangeSet → PISPP `plannedSupplyTotal`；**M3** 另回写 SRP `reservedCapacity` | 无 |
| confirm | **M2 实现** — `MasterPlanService.solve()` → 新 `planVersionId` + `MasterPlanAllocationEntity` | 是 |

---

## 4. 锁定决策（Locked Decisions）

| # | 决策 | 值 | 映射影响 |
|---|------|-----|----------|
| **D1** | StockingPoint 初版 | 单默认 SP：`DEFAULT-FG` | 所有 PISP 合成自 `MaterialEntity` + 常量 SP；无 `StockingPointEntity` 表 |
| **D2** | WorkOrder ↔ SupplyOrder | **双对象 + 映射** | 本体 `SupplyOrder` + JPA `WorkOrderEntity`；`WorkOrderSupplyOrderMapper` |
| **D3** | 首批 derived | M1 **PISPP 滚动链**；M2 **SRP `free_capacity`**；**M3 Operation 时间窗** + SRP 装载/回写 | 见 §2 |
| **D4** | confirm 持久化 | 仍写 **`MasterPlanAllocationEntity`** | M1 Session 不写库；与现有 S04 结果表兼容 |
| **D9** | PeriodSequence 配置（**M3**） | 系统参数 `ontology_period_sequence`，格式 `"14x1d,4x1w,2x1m"`；缺省 `28×1d` | `PeriodSequenceSpec` + `PeriodIndex` |
| **D16** | 本体直驱求解评估（2026-06-10） | **维持复用（D5）**；本体作结果投影层，直驱缺口过大暂不实施 | [otd-ontology-direct-solve-evaluation.md](./otd-ontology-direct-solve-evaluation.md) |
| **D17** | Sandbox 合并（**M3**） | `OntologySandbox` 接口 + `OntologySandboxStore<S>`；两 Session Store 继承；REST/DTO 不变 | 见 §3 |
| **隔离** | Session | **`workspaceId` 硬隔离** | `MasterPlanOntologySessionStore.require(id, ws)`；跨 workspace 返回 404；同 Session 内多 Customer 共存 |

---

## 5. 已知差异与缺口（M1 评审用）

| 缺口 | 说明 | 计划 |
|------|------|------|
| Period vs TimeSlot | **M3** PISPP 支持可配置混合桶（`ontology_period_sequence`）；S04 `TimeSlot` 仍日/周粒度且绑 `resourceId` | 后续对齐或文档化转换 |
| PISPP 供应/需求来源 | **M2 已实现** — `OntologyLoader` 聚合 WO/SO；未与 MRP `MaterialFeasibilityContext` 闭合 | 后续 |
| DemandOrder 本体类 | 暂无 `ontology.demand.*` | M1 **映射**到 `SalesOrderLineEntity` 即可 |
| Operation 本体类 | **M3 已实现** — `supply.Operation` + 时间窗 derived；S04/S05 求解类仍独立 | — |
| SRP 日历数据缺失 | **M3** 无 `ResourceCalendarEntity` 时 SRP 容量为 0（不报错） | 主数据前置 |
| ResourceAssignment confirm | **M2 实现** — `POST .../confirm` → `MasterPlanService.solve()` | — |
| 前端 PISPP 曲线 | **M2 实现** — `/master-plan/ontology` + `GET .../pisps/{id}/periods` | — |
| 前端 SRP 产能表 | **M3 实现** — `SrpCapacityTable`；optimize 后刷新 | — |
| OTD Python 运行时 | 仅语义对照 | 不引入依赖 |

---

## 6. 相关代码索引

| 用途 | 路径 |
|------|------|
| JPA 实体 | `src/main/java/com/plantops/persistence/entity/` |
| S04 求解 | `src/main/java/com/plantops/solver/masterplan/` |
| S05 求解 | `src/main/java/com/plantops/solver/detailschedule/` |
| 推演 Context | `src/main/java/com/plantops/scenario/planning/` |
| API DTO | `src/main/java/com/plantops/api/dto/` |
| M1 本体 | `src/main/java/com/plantops/ontology/` |
| M1 ROL | `src/main/java/com/plantops/rol/` |
| M3 周期 | `ontology/period/PeriodSequenceSpec.java`, `PeriodIndex.java` |
| M3 Sandbox | `scenario/planning/sandbox/OntologySandbox.java`, `OntologySandboxStore.java` |
| 直驱评估 | `docs/otd-ontology-direct-solve-evaluation.md` |

---

*文档随 M1–M3 实施更新；类名以 `src/main/java` 为准。M3 文档同步见 Epic F Task F.1。*
