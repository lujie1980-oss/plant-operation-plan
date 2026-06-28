# §7 标准化匹配与定制标注

> 标签：`[STD]` 标准产品 · `[CFG]` 配置可达 · `[CUST]` 定制开发  
> 范围：`[COMMON]` 通用 · `[SPECIFIC]` 客户/项目特有 · `[REFLOW?]` 回流候选  
> **场景分类**见 [§3.0](./03-scenarios.md#30-场景分类目录)。

---

## 7.1 能力矩阵（按场景分类）

| 分类 | 能力 | 标签 | 场景 | 说明 | REFLOW |
|------|------|------|------|------|--------|
| **1 新订单接收** | ATP / CTP / 满足链 / 确认·取消 / 手工建链 | STD COMMON | SCN-01a~h | 交期试算闭环 | 升 main |
| **2 需求满足分析** | OTIF 预警、根因、跳转 | STD COMMON | SCN-02a~c | 风险可视化 | 升 main |
| **3 产能平衡** | 瓶颈 KPI、钻取、能力试算 | STD COMMON | SCN-03a~c | 产能决策 | 升 main |
| **4 物料满足** | 短缺、供应计划、试算 | STD COMMON | SCN-04a~c | 物料决策 | 升 main |
| **5 工单管理** | 一览、Firm 预警、下发排程 | STD COMMON | SCN-05a~d | 执行衔接 S05 | 升 main |
| **6 主计划** | PlanningRun / S04（**MOD-OCP**） | STD COMMON | SCN-06 | ENT-PV | 升 main |
| **7 供需平衡** | PISPP 表、路径建供应、物料预留 | STD COMMON | SCN-07a~j | PROC-S02 | 升 main |
| **平台** | PATH-ONT Session | STD COMMON | SCN-T01~T02 | ADR-08 | 升 main |
| **平台** | OR-Tools 主计划 | CFG COMMON | SCN-01b, SCN-T01 | `planning_optimizer_engine` | 升 main |
| **平台** | Workspace 隔离 | STD COMMON | SCN-T03 | RULE-WS-01 | — |
| **平台** | 数据集成 MOD-DI | STD COMMON | SCN-T07 | ADP-* · §19 | TODO-19 |
| **平台** | 用户与 IAM | STD COMMON | SCN-T06 | RULE-IAM-* · §18 | TODO-18 |
| **平台** | 工艺主数据 | STD COMMON | SCN-T04 | PISP→RT→RS；**§11 External→md** | 升 main |
| **平台** | 详细排程 | STD COMMON | SCN-T05 | S05 Timefold | — |
| **扩展** | 分切 Studio | CFG SPECIFIC | — | TODO-07 | 分支 |

---

## 7.2 Gap 分析（相对 OTD v4）

| OTD 概念 | 本产品 | Gap |
|----------|--------|-----|
| CustomerOrderLineDelivery | 实现 | 多批次交付；confirmedDeliveryDate（SCN-01d）；取消承诺（SCN-01f） |
| 取消计划 vs 取消承诺 | SCN-01e / SCN-01f | 现行 CANCEL_PLAN  bundled 清 promiseDate；待 TODO-10 拆分 |
| 手工建链（JIT / 有限能力） | SCN-01g / SCN-01h | INFINITE_PLAN_JIT / FINITE_PLAN 已实现 |
| 需求满足分析跳转 | SCN-02c 规范 | 部分 UI 待实现 |
| 产能/物料试算页 | SCN-03c, SCN-04c | simulate 深度待对齐 |
| 供需平衡 PISPP 页 | SCN-07a~j | 专页与 API-MAT-02~08 待实现（TODO-11） |
| 物料预留拖拽/自动 | SCN-07g~j | UI 与 ENT-FF 手工写入待实现 |
| 默认最长采购周期 UI | RULE-MRP-04 | MOD-OCP `/master-plan/rules/material` · 采购提前期 tab 已标注 * 默认行 |
| 多路径 ENT-RT | SCN-07b~d | 初版可能单 RT/PISP；多路径为规范目标 |
| PlanningOptimizer | 主计划已落地 | S05/分切 TODO-07 |

---

## 7.3 配置项（CFG 汇总）

| 参数 | 默认 | 影响 |
|------|------|------|
| `planning_optimizer_engine` | `ortools` | SCN-01b CTP / SCN-06 / SCN-T01 |
| `ontology_direct_solve_enabled` | **废止中** | ADR-08；TODO-08 删除 |
| `ontology_period_sequence` | 14x1d,4x1w,2x1m（扩展：`14x3shift,...` · ADR-16） | ENT-PER · §5.8.1 |
| `master_plan_demand_scale` | 0.01 | 求解 qty 缩放 |
| `master_plan_multi_resource_split` | false | 多资源 CP-SAT |
| `capacity_overload_threshold_pct` | 110 | RULE-MP-07 超载 KPI（SCN-03a） |
| `default_procurement_lead_time_days` | 7 | RULE-MRP-04 回退（无 * 行时） |
| `reservation_auto_policy` | 默认 RULE-FF-06 | SCN-07h/i 自动预留 |

## 7.4 业务知识三层（与 §13 对齐）

| 层 | §7 典型标签 | 规范位置 | 运行时 |
|----|-------------|----------|--------|
| **StandardKnowledge** | `[STD] [COMMON]` | §3 §4 §8 · [§14](../volumes/knowledge/13-14-business-knowledge.md) · `knowledge/standard/` | 产品内置 |
| **IndustrySpecificKnowledge** | `[CFG] [SPECIFIC]` | `knowledge/industries/{id}/` | Workspace.industry_id |
| **CustomizedKnowledge** | `[CFG]`/`[CUST] [SPECIFIC]` | BusinessRules 表 · `knowledge_overlay` | per Workspace |

**Effective Knowledge** = merge(Standard, Industry, Custom)；优先级 **Custom > Industry > Standard**（hard RULE 不可被静默覆盖，见 §13.4）。

---

**回指：** [01-value-goals.md](./01-value-goals.md) · [03-scenarios.md](./03-scenarios.md)
