# 生产批次排程说明

本文说明 **批次计划** 在 Plant Operation Plan 中的位置、数据契约与常用操作。批次属于 S05 生产排程域：工单下发后才创建，详细排程以批次作为可排对象；MRP 与 S04 主计划仍按工单级运行。

## 1. 流水线位置与边界

```mermaid
flowchart LR
  MRP["S01-S03 / MRP\n工单级"] --> DISPATCH["工单下发"]
  DISPATCH --> BATCH["批次计划\n拆批 / 齐套 / 待排开关"]
  BATCH --> S05["S05 详细排程\n批次或整单候选"]
```

- **创建时机**：只有 `dispatch_status = DISPATCHED` 的工单可拆批；接口会拒绝未下发工单。
- **排程单位**：存在 `ACTIVE` 批次时由批次进入 S05；没有活动批次时才走整单进入 S05 的兼容路径。默认 `batch_split_mode=NONE` 会在工单下发时创建整单 `WHOLE` 批次。
- **不影响范围**：批次不改 MRP explosion、BOM lot sizing、pegging，也不把 S04 主计划改为批次级。
- **工作区隔离**：`production_batch` 带 `workspace_id`，所有查询和唯一性约束按当前工作区隔离。

## 2. 用户工作流

1. 在主计划生产工单页下发工单。
2. 打开 `/#/scheduling/batch-plan`（生产排程 > 批次计划）。
3. 在 `/#/scheduling/parameters` 的「计划参数 · 批次拆解」设置策略：
   - `batch_split_mode`: `NONE` / `FIXED_QTY` / `KITTING` / `AUTO`
   - `batch_fixed_qty`: 固定拆批基准量
   - `batch_min_qty`, `batch_max_qty`: `AUTO` 模式边界
   - `batch_remainder_mode`: 固定量余数处理
   - `batch_kitting_create_short_batch`: 未齐套余量是否创建 `SHORT` 批次
   - `batch_auto_on_dispatch`: 工单下发时是否自动拆批
4. 在批次计划页执行自动拆批、手工创建批次、刷新齐套或取消批次。
5. 在待排工单/批次齐套页控制 `pendingScheduleEligible`，再进入 S05 详细排程。

> 默认参数 `batch_split_mode=NONE` 表示“不按策略拆成多个批次”。下发工单时系统会幂等创建一个等于工单总量的 `WHOLE` 批次；若要使用 `/split/auto` 或 `/split/auto-all`，先切换到 `FIXED_QTY`、`KITTING` 或 `AUTO`。

## 3. 数据模型与状态

核心表由 `V42__production_batch.sql` 创建：

| 表 / 字段 | 说明 |
|-----------|------|
| `production_batch.batch_no` | 批次号，格式为 `BAT-{workOrderNo}-{seq:02d}` |
| `production_batch.work_order_no` | 来源工单号；不是 BOM 父子关系 |
| `production_batch.quantity` | 批次数量，所有 `ACTIVE` 批次数量合计不能超过工单数量 |
| `production_batch.kitting_status` | `UNKNOWN` / `KITTED` / `SHORT` |
| `production_batch.split_method` | `MANUAL` / `FIXED` / `KITTING` / `AUTO` / `WHOLE` |
| `production_batch.status` | `ACTIVE` / `CANCELLED` |
| `production_batch.pending_schedule_eligible` | 批次是否进入 S05 候选 |
| `work_order.batch_split_status` | `NONE` / `PARTIAL` / `SPLIT` |
| `detail_schedule_operation.batch_no` | S05 结果中的批次来源 |

状态规则：

- `NONE`：没有有效批次，父工单可作为整单候选进入 S05。注意：默认 `batch_split_mode=NONE` 下发后通常会创建一个 `WHOLE` 批次，因此工单会变为 `SPLIT`。
- `PARTIAL`：存在有效批次但数量未覆盖整张工单；父工单不进入 S05，剩余量保留在工单侧。
- `SPLIT`：有效批次数量覆盖整张工单；仅批次进入 S05。
- 取消所有批次会回到 `NONE`，父工单恢复整单待排资格。

## 4. API 快查

所有接口位于 `/api/v1/scheduling/batches`，前端通过 `api.schedulingBatches` 调用。

| 方法 | 路径 | 用途 |
|------|------|------|
| `GET` | `/work-orders` | 列出已下发工单及剩余可拆量 |
| `GET` | `/by-work-order/{workOrderNo}` | 查看某工单下的活动批次 |
| `POST` | `/split/auto` | 按当前批次策略拆单张工单 |
| `POST` | `/split/auto-all` | 对所有有剩余量的已下发工单批量自动拆批 |
| `POST` | `/split/manual` | 手工创建批次，body: `{ "workOrderNo": "...", "quantity": 100 }` |
| `POST` | `/cancel` | 取消单批或整单批次 |
| `POST` | `/refresh-kitting` | 重新计算某工单下批次齐套状态 |
| `GET` | `/kitting` | 列出待排批次齐套视图 |
| `POST` | `/kitting/compute` | 重算所有待排批次齐套状态 |
| `PUT/PATCH` | `/{batchNo}/pending-schedule-eligible` | 设置批次是否进入 S05 候选 |
| `GET` | `/{batchNo}/routing` | 按批次数量查看工艺与可用产线 |

示例：

```bash
# 查看可拆批工单
curl http://localhost:8080/api/v1/scheduling/batches/work-orders

# 单张工单按当前策略自动拆批
curl -X POST http://localhost:8080/api/v1/scheduling/batches/split/auto \
  -H "Content-Type: application/json" \
  -d '{"workOrderNo":"WO-001","quantity":null}'

# 手工创建 100 件批次
curl -X POST http://localhost:8080/api/v1/scheduling/batches/split/manual \
  -H "Content-Type: application/json" \
  -d '{"workOrderNo":"WO-001","quantity":100}'

# 暂停某批次进入 S05
curl -X PUT http://localhost:8080/api/v1/scheduling/batches/BAT-WO-001-01/pending-schedule-eligible \
  -H "Content-Type: application/json" \
  -d '{"pendingScheduleEligible":false}'
```

## 5. 代码入口

| 关注点 | 代码路径 |
|--------|----------|
| REST API | `src/main/java/com/plantops/api/SchedulingBatchResource.java` |
| 拆批策略与状态刷新 | `src/main/java/com/plantops/scenario/batch/ProductionBatchSplitService.java` |
| 批次齐套与待排开关 | `src/main/java/com/plantops/scenario/batch/ProductionBatchKittingService.java` |
| S05 候选展开 | `src/main/java/com/plantops/scenario/planning/DetailSchedulePlanningContextBuilder.java` |
| 批次实体 | `src/main/java/com/plantops/persistence/entity/ProductionBatchEntity.java` |
| 参数读取 | `src/main/java/com/plantops/config/BatchSplitConfigService.java` |
| 前端批次计划页 | `frontend/src/pages/BatchPlanPage.tsx` |
| 前端 API 封装 | `frontend/src/api/client.ts` |

## 6. 常见问题

- **自动拆批报“不拆批次”**：`batch_split_mode=NONE` 时 `/split/auto` 和 `/split/auto-all` 会拒绝；先在生产排程计划参数中选择 `FIXED_QTY`、`KITTING` 或 `AUTO`。手工拆批仍按工单状态、数量和剩余可拆量校验。
- **批次未进入详细排程**：检查批次 `status=ACTIVE` 且 `pending_schedule_eligible=true`；已拆批工单以批次开关为准。
- **缺料批次如何处理**：`KITTING` / `AUTO` 模式会标记 `KITTED` 或 `SHORT`。若 `batch_kitting_create_short_batch=false`，未齐套余量不会立即成批，会留在父工单剩余量中。
- **重新跑 MRP 后批次是否改写**：MRP 不读写 `production_batch`；批次是排程阶段数据。
