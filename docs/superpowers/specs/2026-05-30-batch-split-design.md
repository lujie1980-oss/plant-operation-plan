# 批次拆解（排程域）设计

## 目标

在 **生产排程阶段**（工单已下发之后）支持将生产工单拆分为多个 **生产批次**；详细排程 S05 与机台甘特以 **批次** 为最小排程单位，每个批次独立占用设备。

提供：

1. **拆批策略参数**（计划参数 · 批次拆解）
2. **批次计划页**（工单 ↔ 批次 ↔ 工艺/设备）
3. **S05 推演与求解**按批次展开工序与工时

## 域边界（重要）

| 属于排程域 | **不属于 / 不改动** |
|-----------|---------------------|
| 已下发工单的拆批、取消批、手工建批 | MRP  explosion、合并工单、pegging 生成 |
| `production_batch` 持久化 | `MrpExplosionService`、`MrpLotSizing` |
| S05 `OperationAssignment` 按批次量展开 | 主计划 S04 工单级分配逻辑（首期） |
| 排程阶段齐套（按批次量消耗库存池） | S02 订单行齐套报告 |

**原则：拆批是排程的工作内容，不影响 MRP 逻辑。**

- MRP 仍按现有规则产出 **整张工单 + 总量**；不读取、不写入 `production_batch`。
- 批次仅在 **工单 `dispatch_status = DISPATCHED`** 之后创建与管理。
- 若用户重新跑 MRP 导致工单被替换或删除，批次数据由排程模块自行处理孤儿/失效提示；**不要求 MRP 流程感知批次**。

---

## 已确认决策

| 项 | 结论 |
|----|------|
| S05 最小排程单位 | **批次**（独立占设备） |
| 待排 / S05 队列 | **仅批次**；父工单拆批后退出队列 |
| 齐套拆批 | **按当前可齐套量拆**：可齐套部分成批（`KITTED`），剩余为未齐套批次（`SHORT`）或暂留父工单剩余量（见参数） |

---

## 流水线位置

```mermaid
flowchart LR
  MRP["S01–S03 / MRP\n（工单级，不改）"]
  DISP["工单下发"]
  BATCH["批次拆解\n（本设计）"]
  KIT["排程齐套"]
  S05["S05 详细排程\n（批次级）"]

  MRP --> DISP --> BATCH --> KIT --> S05
```

- **未拆批**的已下发工单：可暂时仍按整单进入 S05（向后兼容），直到用户在批次计划页拆批。
- **已拆批**的工单：父工单 `pending_schedule_eligible = false`，仅 **ACTIVE 批次** 进入 S05。

---

## 数据模型

### 新表 `production_batch`

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT PK | |
| `workspace_id` | BIGINT | |
| `batch_no` | VARCHAR UK | 如 `BAT-{workOrderNo}-{seq:02d}` |
| `work_order_no` | VARCHAR FK | 父工单（仅作来源/汇总，非 BOM 父子） |
| `batch_seq` | INT | 工单内序号 |
| `quantity` | DECIMAL | 批次生产量 |
| `kitting_status` | VARCHAR | `KITTED` / `SHORT` / `UNKNOWN` |
| `split_method` | VARCHAR | `MANUAL` / `FIXED` / `KITTING` / `AUTO` |
| `status` | VARCHAR | `ACTIVE` / `CANCELLED` |
| `pending_schedule_eligible` | BOOLEAN | 是否进入 S05 候选（默认 true） |
| `created_ts` | TIMESTAMP | |

### 工单扩展字段 `work_order`

| 列 | 说明 |
|----|------|
| `batch_split_status` | `NONE` / `SPLIT` / `PARTIAL` |

**数量守恒**：同一父工单下 `sum(ACTIVE batch.quantity) ≤ work_order.quantity`。

**状态规则**

- `batch_split_status = NONE`：无有效批次，父工单可进待排队列（整单排程兼容）。
- `SPLIT` / `PARTIAL`：存在有效批次 → 父工单 **不可** 进 S05，仅批次可排。
- 取消全部批次 → 恢复 `NONE`，父工单重新可排。

> `work_order.parent_work_order_no` 继续表示 **BOM/MRP 层级**；批次父子关系 **仅** 通过 `production_batch.work_order_no` 表达，二者不混用。

---

## 策略参数（`system_parameter` · 排程 · 批次拆解）

| 参数 ID | 说明 |
|---------|------|
| `batch_split_mode` | `NONE` / `FIXED_QTY` / `KITTING` / `AUTO` |
| `batch_fixed_qty` | 固定拆批量（>0） |
| `batch_remainder_mode` | `FLOOR` / `CEIL` / `SEPARATE_TAIL` / `MERGE_TAIL` |
| `batch_kitting_create_short_batch` | 齐套拆批时，剩余未齐套量是否立即建 `SHORT` 批次（默认 true） |
| `batch_min_qty` | 自动拆批最小批量（Phase 3） |
| `batch_max_qty` | 自动拆批最大批量（Phase 3） |
| `batch_auto_on_dispatch` | 下发后是否自动按策略拆批（默认 false） |

### 固定数量拆批

工单量 `Q`，批量 `B`，余数 `R = Q mod B`：

| 模式 | 结果 |
|------|------|
| `FLOOR` | `floor(Q/B)` 个整批，余量不拆 |
| `CEIL` | 最后不足 `B` 的也单独成批 |
| `SEPARATE_TAIL` | 整批 + 尾批 `R`（`R>0` 时） |
| `MERGE_TAIL` | 最后一批 = `B+R`（仅一批时即 `Q`） |

### 齐套拆批

1. 对父工单调用排程齐套（`KittingService`，按工单 BOM 与库存池）。
2. `kittingEligibleQty =` 当前可齐套数量（≤ 剩余可拆量）。
3. 若 `kittingEligibleQty > 0`：创建批次，量 = `kittingEligibleQty`，`kitting_status = KITTED`。
4. 剩余量：若 `batch_kitting_create_short_batch` → 建 `SHORT` 批次；否则留父工单 `PARTIAL` 不建批。

### 自动拆批（Phase 3）

在 `FIXED_QTY` 基础上用 `batch_min_qty` / `batch_max_qty` 夹紧，并预留交期、产能、齐套权重启发式；**不接入 MRP**。

---

## 批次计划页

**路由**：`/scheduling/batch-plan`（生产排程导航）

**布局**（参考 `PendingScheduleWorkOrdersPage`）：

```
┌──────────────────┬─────────────────────────────┐
│ 已下发生产工单     │ 批次列表（选中工单的拆批结果）  │
│                  ├─────────────────────────────┤
│ 右键：            │ 批次工艺路径 + 可用设备        │
│ · 自动拆批        │ （选中批次）                  │
│ · 手工创建批次     │                             │
│ · 取消全部批次     │                             │
└──────────────────┴─────────────────────────────┘
```

**批次行右键**：取消单批（量回父工单剩余池）

**API**（`/api/v1/scheduling/batches`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/work-orders` | 已下发、可拆批工单列表 |
| GET | `/by-work-order/{wo}` | 工单下批次 |
| POST | `/split/auto` | 按当前策略自动拆批 |
| POST | `/split/manual` | `{ workOrderNo, quantity }` |
| POST | `/cancel` | 取消批次（单批或整单） |
| GET | `/{batchNo}/routing` | 批次工艺（量按批次） |

---

## S05 改造

| 组件 | 改动 |
|------|------|
| `DetailSchedulePlanningContextBuilder` | 候选集 = 已下发且 `ACTIVE` 的批次；未拆批工单整单作为隐式单批（兼容） |
| `DetailScheduleAssignmentBuilder` | `buildForBatch(batch, wo, ops, …)`；工时按 `batch.quantity` |
| `OperationAssignment` | +`batchNo`、+`batchQuantity`；`operationId` 含 batch |
| `DetailScheduleOperationEntity` | +`batch_no` |
| `KittingService`（S05 路径） | 按批次量 `checkAndConsume` |
| 机台甘特 / `MachineScheduleGantt` | 展示 `batchNo` |

**主计划 S04**：首期 **不** 改为批次级；S04 仍工单级。批次仅影响 S05 与排程 UI。

---

## 分阶段交付

| 阶段 | 范围 |
|------|------|
| **Phase 1** | 表结构、参数 UI、`NONE`/`FIXED_QTY`、手工/取消、批次计划页、S05 批次排程 |
| **Phase 2** | 齐套拆批 + 批次齐套状态刷新 |
| **Phase 3** | `AUTO` 启发式/优化拆批 |

---

## 非目标

- 修改 MRP、BOM lot sizing、合并工单算法
- S04 主计划批次级优化
- 批次与 ERP/MES 回写（后续集成）

---

## 测试要点

- 固定量四种余数模式数量正确
- 拆批后父工单不进 `listDispatched` 的 S05 候选（仅批次进）
- 取消批次后数量守恒、父工单恢复可排
- S05 工序时长 ∝ 批次量
- MRP 重跑后：不读写 `production_batch`；批次页面对缺失父工单显示失效态
