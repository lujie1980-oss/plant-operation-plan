# 主计划：BOM / 工艺多资源排产

## 粒度对比

| 方式 | 含义 | 优点 | 缺点 | 本项目 |
|------|------|------|------|--------|
| 按销售行展开 BOM | 从订单成品 DFS 爆炸，对「有工艺」的物料建分配 | 与 BOM 一致，不依赖预生成工单 | 需维护 BOM×工艺；与 MES 工单可能脱节 | 可作为工单缺失时的补充 |
| **按工单** | 每个 `WorkOrderEntity`（含子工单）一条 `OrderAllocation` | 与现有工单树、详细排程、满足链一致；Demo 数据已具备 | 工单需事先由 BOM 展开生成 | **已采用** |
| 按工序 | 每个工艺步骤一条分配（多资源、顺序、换型） | 最细，适合多工站、跨天拆段 | 需独立工艺路线表；复杂度高 | **S04 已采用**（`OrderAllocation` 工序级；详细排程 S05 在 `lineId` 分钟序） |

## 实现要点

1. **规划实体**：开放订单下的全部工单；`productResources` 无记录的物料（原材料）不进入主计划。
2. **资源与时间槽**：`ProductionResourceEntity.routingResourceIds()` = 瓶颈 + 所有 `productResources.resourceId`（含 SMT1、切割机等）。
3. **硬约束**：`resourceMatch`；`upstreamBeforeAssembly`（子工单槽位 index &lt; 父工单）。
4. **持久化**：`master_plan_allocation.work_order_no`、`product_code`。
5. **产能平衡**：同一资源集合；主计划按 `workOrderNo` 匹配区间负荷。

## 数据前提

- `productResources`：可制造品 → 主资源（成品→组装，电子半成品→SMT，机加→切割机等）。
- `workOrders`：销售订单 BOM 展开结果，父子关系用于先后顺序约束。

## 按 BOM 生成工单（运行时）

`MrpExplosionService`（经 `WorkOrderGenerationService` 调用）：

- 对**全部开放销售订单行**汇总 Level 0 毛需求，按 `(productCode, needDate)` 合并；
- 按 BOM 层级向下展开：子件需求 = 父项计划量 × 用量 × (1+scrapRate)；
- 对制造件应用 BOM 行上的 `lot_size` / `lot_size_multiple` 取整（作用于父项）；
- 生成合并工单 `WO-MRP-{product}-{date}-{seq}`，pegging 表追溯销售行贡献量；
- BOM 依赖表 `work_order_bom_dependency` 供主计划上下游约束。

触发方式：

| 入口 | 行为 |
|------|------|
| `POST /api/v1/demand/import` | 导入后全场景 MRP 重建 |
| `POST /api/v1/demand/work-orders/generate` | 全场景或单行触发（均重跑全场景 MRP） |
| 主计划 pipeline | `regenerateForAllOpenOrdersSkipping` |
| `POST .../work-orders/generate/{so}/{line}?replaceExisting=` | 单行快捷接口 |
| 全链路 `run-full-pipeline` | 对尚无工单的开放订单行自动 `ensure` |
