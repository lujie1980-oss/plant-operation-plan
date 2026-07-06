# 工厂运营计划系统（Plant Operation Plan）— 项目完整文档

| 项目 | 说明 |
|------|------|
| 名称 | Plant Operation Plan |
| 版本 | 1.0.0-SNAPSHOT |
| 代码路径 | `plant-operation-plan/` |
| 文档日期 | 2026-07-06 |
| 最近更新 | Timefold 2.0、工作区数据集、生产批次、详细排程 Session 推演、SimulationProfile、生产任务发布与反馈冻结 |

---

## 目录

1. [项目概述](#1-项目概述)
2. [业务蓝图](#2-业务蓝图)
3. [功能设计](#3-功能设计)
4. [详细技术方案](#4-详细技术方案)
5. [部署方案](#5-部署方案)
6. [运维与扩展](#6-运维与扩展)
7. [附录](#7-附录)

---

## 1. 项目概述

### 1.1 建设目标

面向**单工厂**的高级计划与排程（APS），将销售需求、物料齐套、粗能力、主计划、详细排程、执行反馈与 KPI 串成一条可运行的计划链路。核心优化能力由 **Timefold Solver** 提供（S04 主计划、S05 详细排程），其余场景以规则引擎与持久化服务实现。

### 1.2 范围边界

| 在范围内 | 不在范围内（当前版本） |
|----------|------------------------|
| 单工厂、多产线、瓶颈资源主计划、生产批次排程 | 多工厂网络计划 |
| 工作区数据集 + H2 内存库 + Flyway 演示数据 | 生产级 PostgreSQL 集群（可扩展） |
| ERP/MES Mock 集成 | 真实 SAP/MES 连接器 |
| React 业务前端（数据管理、业务规则、主计划、生产排程） | 移动端、权限租户体系 |

### 1.3 技术栈总览

| 层级 | 技术 |
|------|------|
| 后端运行时 | Java 21、Quarkus 3.17.5 |
| 优化引擎 | Timefold Solver 2.0.0（Community） |
| 持久化 | Hibernate ORM Panache、Flyway、H2 |
| API | REST + JSON、SmallRye OpenAPI |
| 前端 | React 18、TypeScript、Vite 6、React Router 7、gantt-task-react |
| 构建 | Maven Wrapper、npm |

### 1.4 仓库结构

```text
PlantOperationPlan/                    # 工作区根目录
├── 工厂计划*.md                        # 业务方法论文档（场景卡片，只读参考）
└── plant-operation-plan/              # 可运行工程
    ├── pom.xml                        # Quarkus 3.17.5 + Timefold 2.0.0
    ├── mvnw / mvnw.cmd
    ├── src/main/java/com/plantops/
    │   ├── api/                       # REST 资源
    │   ├── api/dto/                   # 对外 DTO
    │   ├── scenario/                  # S01–S07 场景服务、排程 Session、批次、执行反馈
    │   ├── solver/                    # Timefold 模型与约束
    │   ├── workspace/                 # 工作区数据集
    │   ├── persistence/entity/        # JPA 实体
    │   ├── integration/               # ERP/MES 端口
    │   ├── config/                    # 求解器、参数、主计划策略
    │   └── sample/                    # 示例数据加载
    ├── src/main/resources/
    │   ├── application.properties
    │   ├── db/migration/              # Flyway V1–V48+（工作区、MRP、批次、生产任务、推演配置）
    │   ├── sample-data/factory-demo.json
    │   └── META-INF/resources/        # 前端生产构建产物
    ├── frontend/                      # React 源码（HashRouter）
    └── docs/
        ├── architecture.md
        ├── aps-planning-layer.md
        ├── detail-schedule-simulation-layer.md
        ├── master-plan-bom-routing.md
        ├── PROJECT_DOCUMENTATION.md   # 本文档
        └── superpowers/specs/...      # 设计历史，不作为最新 Runbook 的唯一来源
```

---

## 2. 业务蓝图

### 2.1 价值链与计划层次

```mermaid
flowchart LR
  ERP[ERP 销售订单] --> S01[S01 需求满足]
  S01 --> S02[S02 齐套]
  S02 --> S03[S03 产能平衡]
  S03 --> S04[S04 主计划]
  S04 --> S05[S05 批次与详细排程 Session]
  S05 --> S06[S06 生产任务发布]
  S06 --> MES[MES / 现场反馈]
  MES --> FEEDBACK[反馈冻结 / 重排边界]
  FEEDBACK --> S05
  S04 --> S07[S07 KPI]
  S05 --> S07
```

计划时间粒度由粗到细：

| 层次 | 场景 | 决策内容 |
|------|------|----------|
| 需求层 | S01 | 订单优先级、交期、满足链（库存/工单追溯） |
| 物料层 | S02 | 关键料齐套、缺料原因 |
| 能力层 | S03 | 负荷桶、超载、开线建议 |
| 主计划层 | S04 | 瓶颈资源时间槽、订单分配、开线决策 |
| 排程层 | S05 | 产线工序顺序、换线时间、缺口建议 §H |
| 执行层 | S06 | 排程确认发布、生产任务状态、反馈冻结与事件驱动重排 |
| 绩效层 | S07 | OTIF、利用率、计划版本对比 |

### 2.2 场景卡片（S01–S07）

#### S01 — 需求满足

- **输入**：销售订单行（产品、数量、交期、优先级、锁定标记）。
- **输出**：订单需求满足列表、KPI 汇总、选中订单的**满足追溯链**。
- **满足链语义**（pegging，非时间表）：
  - 销售订单可由**成品库存**或**根工单**满足；
  - 工单由 **BOM 子件** 满足：子件优先匹配**子工单**（`parent_work_order_no`），否则**原材料库存**，否则**缺料**。
- **业务价值**：计划员看清「这单货从哪来」，而不是仅看工序时间。

#### S02 — 齐套

- 按 BOM 关键料与可用库存（扣减预留）逐单计算 `KITTING_OK` / `SHORTAGE`。
- 结果持久化至 `kitting_result`，供 S04 过滤可排订单。

#### S03 — 产能平衡

- 按资源×日期×班次聚合需求分钟与可用分钟，计算利用率与超载标记。
- 启发式给出产线开线建议（人数、原因）。

#### S04 — 主计划（Timefold）

- 将齐套通过的订单分配到瓶颈资源**时间槽**（`TimeSlot`）。
- 持久化：`plan_version`、`master_plan_allocation`、`line_opening_decision`。
- 输出：计划版本号、得分、分配明细。

#### S05 — 生产排程（批次 + Session 推演 + Timefold）

- 生产排程的最小作业单元可以是 `production_batch`，批次由工单拆分而来，支持自动拆分、手工拆分、取消、批次齐套与待排标记。
- `DetailScheduleSessionService` 提供「创建 Session → 局部拖拽补丁 → 推演 → 可选优化 → 确认」工作流。
- `simulate` 请求可带 `stepPatches`、`affectedOperationIds`、`fullReschedule`、`simulationProfileId`、`ruleOverrides`、`feedbackCutoff`；规则层处理工厂日历、反馈冻结、连续批次等约束。
- Timefold 仍用于 S05 选优；Session 推演侧重解释变更影响、规则应用、冲突与可确认版本。

#### S06 — 执行闭环

- **发布**：排程 Session 确认后写入/更新 `detail_schedule_operation`，并由 `ProductionTaskService` 发布为 `production_task`。
- **反馈**：`production_task` 支持按工序 `start` / `complete`，执行状态可用于后续推演的 `feedbackCutoff` 冻结边界。
- **冲突**：当已运行任务与新计划冲突时，系统记录 `planning_conflict`，计划员需在确认前处理。
- **事件**：设备停机、缺料、加急、小延误等 → 映射重排级别 R0–R3 并执行对应重排动作；R0/R1 只记录影响，R2/R3 调用主计划/详细排程服务重算。

| 级别 | 典型事件 | 行为 |
|------|----------|------|
| R0/R1 | 小延误 | 记录影响，不自动重算 |
| R2 | 设备/缺料 | 保留主计划，重算详细排程 |
| R3 | 订单加急 | 重算齐套 + 主计划 + 详细排程 |

#### S07 — KPI

- 汇总计划绩效指标（如交付率、超载率等，见 `KpiService`）。
- 支持两版计划得分与影响摘要对比。

### 2.3 角色与界面

前端采用**左侧导航 + 主内容区**布局，顶部 `WorkspaceSelector` 用于切换工作区数据集；导航按「数据管理 / 业务规则 / 主计划 / 生产排程」分组：

| 导航分组 | 页面 | 用途 |
|----------|------|------|
| 首页 | `/` | 总览入口 |
| 数据管理 | `/master-data` | 产品、BOM、工艺、资源、规则基础数据 |
| 数据管理 | `/business-data` | 订单、库存、工单等业务数据 |
| 数据管理 | `/factory-calendar` | 工厂日历与资源工作时间 |
| 数据管理 | `/workspaces` | 工作区 / 数据集管理 |
| 业务规则 | `/business-rules/production` 等 | 生产、产能、物料、人力、需求规则 |
| **主计划** | | |
| 计划参数 | `/master-plan/parameters` | 规划窗、时栅、班次等全局参数 |
| 优化目标 | `/master-plan/objectives` | **主计划策略**列表：产能模式 + 软目标权重 |
| 计划运行 | `/master-plan/plan-run` | 选择策略并运行主计划流水线 |
| 需求满足 | `/master-plan/analysis/demand` | S01 订单满足与追溯链 |
| 产能平衡 | `/master-plan/analysis/capacity` | S03 资源×班次负荷热力甘特 |
| 物料需求 | `/master-plan/analysis/material` | S02 物料滚算与缺料 |
| 生产工单 | `/master-plan/analysis/work-orders` | 工单生成、下发与满足链 |
| 推演诊断 | `/master-plan/analysis/diagnostics` | 主计划 / 排程诊断预览 |
| 订单推演 | `/master-plan/analysis/order-chain` | 订单计划链预览 |
| 场景对比 | `/master-plan/scenario-comparison` | 多场景 KPI 柱状对比 |
| **生产排程** | `/scheduling/parameters` | 排程参数 |
| 生产排程 | `/scheduling/pending-work-orders` | 待排工单池 |
| 生产排程 | `/scheduling/batch-plan` | 生产批次拆分与批次计划 |
| 生产排程 | `/scheduling/kitting` | 批次齐套 |
| 生产排程 | `/scheduling/detail-schedule` | Session 推演、优化与确认 |
| 生产排程 | `/scheduling/version-comparison` | 排程版本对比 |
| 需求跟踪 | `/demand-tracking` | 订单交付跟踪 |

| 角色 | 主要界面 |
|------|----------|
| 计划员 | 计划运行、需求满足、产能平衡、生产工单、场景对比 |
| 物料计划 | 物料需求、满足链中的库存/缺料 |
| 生产主管 | 产能平衡、生产工单下发 |
| 管理层 | 场景对比、需求跟踪 |

### 2.4 核心业务对象（领域语言）

| 对象 | 说明 |
|------|------|
| 销售订单行 | 需求最小单元 |
| 工单 | 生产任务；`parent_work_order_no` 表示上游工单满足本工单 |
| 生产批次 | `production_batch`，由工单拆分，用于 S05 批次级排程、批次齐套与连续批次规则 |
| 生产任务 | `production_task`，由确认后的排程发布，状态用于现场执行与反馈冻结 |
| BOM | 父件 → 子件用量 |
| 库存 | 库位×物料，可用量 = 在手 − 预留 − 质检冻结 |
| 计划版本 | 主计划 / 详细排程每次求解生成独立版本号；主计划版本关联 **strategyId / strategyName** |
| 排程 Session | S05 的临时推演上下文，保存工序/批次步骤、补丁、规则应用、冲突与可确认结果 |
| SimulationProfile | 可复用的推演规则配置，运行时可通过 `simulationProfileId` 或 `ruleOverrides` 覆盖 |
| 工作区 | 数据集隔离边界；前端顶部选择，后端资源按当前工作区读写演示数据 |
| 主计划策略 | 命名配置包：产能模式（无限/有限）+ 一组软优化目标权重；运行时可选用 |
| 计划场景 | 一次主计划求解产出的 `planVersionId`，可在结果页间切换查看 |
| 满足链节点 | 销售订单 / 工单 / 库存 / 缺料 |
| 满足边 | 供应方 → 需求方（`INVENTORY_PEG` / `WORK_ORDER_PEG`） |

---

## 3. 功能设计

### 3.1 功能清单

| 编号 | 功能 | 后端 | 前端路由 |
|------|------|------|----------|
| F01 | 需求满足查询与 KPI | `GET /demand/demand-pool`、`/summary` | `/#/master-plan/analysis/demand` |
| F02 | 订单满足链追溯 | `GET .../fulfillment-chain` | `/#/master-plan/analysis/demand` |
| F03 | 订单导入 | `POST /demand/import` | （API/Swagger） |
| F04 | 物料需求计算 | `POST /kitting/compute`、`POST /material-requirements/compute` | `/#/master-plan/analysis/material` |
| F05 | 产能平衡分析 | `POST /capacity/analyze?masterPlanVersionId=` | `/#/master-plan/analysis/capacity` |
| F06 | 主计划求解/查询 | `POST/GET .../master-plan/*` | 计划运行 + 场景选择器 |
| F07 | 详细排程求解 | `POST .../detail-schedule/solve` | `/#/scheduling/detail-schedule` |
| F08 | 工单下发 | `POST /planning/dispatch` | `/#/master-plan/analysis/work-orders` |
| F09 | 事件与重排 | `POST /events`、`/planning/reschedule` | （API） |
| F10 | KPI 与版本对比 | `GET /kpi/report`、`/planning/compare` | `/#/demand-tracking` |
| F11 | 主计划流水线 | `POST /planning/pipeline-runs` | `/#/master-plan/plan-run` |
| F12 | ERP/MES 联调 Mock | `GET /integration/*` | — |
| F13 | **主计划策略 CRUD** | `GET/POST/PUT/DELETE .../master-plan/strategies` | `/#/master-plan/objectives` |
| F14 | **场景列表与对比** | `GET /planning/scenarios`、`/scenarios/compare` | `/#/master-plan/scenario-comparison` |
| F15 | **场景选择器** | 复用 F06/F14 场景 API | 四个结果页 `PageHeader` |
| F16 | **工作区数据集** | `GET/POST/DELETE /workspaces` | `/#/workspaces` + 顶部选择器 |
| F17 | **主数据与规则基础数据** | `GET/POST/PUT/DELETE /master-data/*`、Excel 导入导出 | `/#/master-data`、`/#/business-rules/*` |
| F18 | **生产批次拆分** | `/scheduling/batches/split/*`、`/by-work-order/*` | `/#/scheduling/batch-plan` |
| F19 | **批次齐套与待排标记** | `/scheduling/batches/kitting/*`、`/{batchNo}/pending-schedule-eligible` | `/#/scheduling/kitting` |
| F20 | **排程 Session 推演** | `/planning/schedule-sessions/*` | `/#/scheduling/detail-schedule` |
| F21 | **SimulationProfile** | `/planning/simulation-profiles` | 由排程 Session 推演调用 |
| F22 | **排程版本对比** | `/planning/detail-schedule/versions/*` | `/#/scheduling/version-comparison` |
| F23 | **生产任务反馈** | `/production-tasks`、`/{stepId}/start`、`/{stepId}/complete` | 生产排程 / 执行反馈 |
| F24 | **推演诊断与订单计划链** | `/planning/*/diagnostics/preview`、`/planning/order-chain/preview` | `/#/master-plan/analysis/diagnostics`、`/#/master-plan/analysis/order-chain` |

> 旧路由（如 `/#/demand`、`/#/pipeline`、`/#/detail-schedule`）保留重定向至新路径，见 `frontend/src/App.tsx`。

### 3.2 S01 需求满足界面设计

布局（视口内分栏滚动）：

```text
┌─────────────┬──────────────────────────┐
│  关键 KPI   │   销售订单列表（可选中）    │
│             ├──────────────────────────┤
│             │  满足关系列表              │
│             │  满足链甘特图（业务节点）   │
└─────────────┴──────────────────────────┘
```

- 甘特图**仅显示业务节点**（销售订单、工单、库存、缺料），无「需求/直接满足」抽象泳道。
- 箭头依赖：上游供应节点 → 被满足节点。

### 3.4 主计划策略（优化目标页）

「优化目标」页实质为**主计划策略**管理，将原先分散的产能模式与目标权重合并为可命名、可复用的策略包。

**策略结构**

| 字段 | 说明 |
|------|------|
| `id` / `name` | 策略标识与显示名称 |
| `capacityStrategy` | `UNCONSTRAINED`（无限产能）或 `FINITE_CAPACITY`（有限产能、跨天拆段） |
| `objectives[]` | 软优化目标：启用开关 + 权重 |
| `isDefault` | 默认策略；计划运行页未指定时选用 |

**软优化目标（`MasterPlanObjectiveCatalog`）**

| ID | 名称 | 说明 |
|----|------|------|
| `minimize_lateness` | 最小化延期 | 完成晚于交期按延期天数惩罚 |
| `prioritize_high_priority` | 高优先级靠前 | 高优先级订单倾向靠前时栅 |
| `locked_orders_prefer_earlier` | 锁定订单靠前 | 冻结窗内订单尽量排在前段 |
| `balance_adjacent_slot_loading` | **产能均衡** | 同一资源相邻时间槽负荷尽量接近，避免陡增/陡降 |

**持久化**

- 策略 JSON 存于 `system_parameter.param_id = 'master_plan_strategies'`（CLOB）。
- 每次求解/流水线运行将 `strategy_id`、`strategy_name` 写入 `plan_version` 与 `planning_pipeline_run`。

**操作**：新建、重命名、复制、删除、设为默认；保存后立即生效于下次求解。

### 3.5 计划场景与场景选择器

**场景** = 一次主计划求解产生的 `planVersionId`，附带策略名、产能模式、得分、生成时间等元数据（`PlanningScenarioDto`）。

**场景选择器（`ScenarioSelector`）**

- 通过 `PlanContext` 全局维护 `scenarios`、`selectedScenarioId`；启动时从 localStorage 恢复上次选中场景。
- 下拉展示：`planVersionId · strategyName`；右侧策略 pill + 刷新按钮。
- **仅出现在四个计划结果页**的 `PageHeader`（`showScenarioSelector={true}`）：
  - 需求满足、产能平衡、物料需求、生产工单
- 配置类页面（计划参数、优化目标、业务规则、业务数据、计划运行）及场景对比页**不展示**全局场景选择器；场景对比页使用左侧多选列表。

**场景联动**

| 页面 | 联动行为 |
|------|----------|
| 产能平衡 | 切换场景 → `POST /capacity/analyze?masterPlanVersionId=` 重算负荷 |
| 需求满足 / 物料需求 / 生产工单 | 切换场景 → 各 API 带 `masterPlanVersionId` 重载；满足链甘特使用场景内主计划分配时间 |
| 计划运行 | 运行完成后 `refreshScenarios()`，新场景进入列表 |
| 场景对比 | 列表展示 `strategyName`；勾选多场景对比 Score、产能、排产 KPI |

**产能平衡页 UI**：已移除页内「主计划策略」下拉、「运行主计划」「重新分析」按钮；分析完全随顶部场景切换自动触发。

### 3.6 状态与枚举

**齐套状态**：`KITTING_OK`、`SHORTAGE`

**主计划产能模式**：`UNCONSTRAINED`、`FINITE_CAPACITY`

**满足链节点类型**：`SALES_ORDER`、`WORK_ORDER`、`INVENTORY`、`SHORTAGE`

**满足边类型**：`INVENTORY_PEG`、`WORK_ORDER_PEG`、`SHORTAGE_PEG`

**订单满足汇总**：`ON_TRACK`、`PLANNED`、`PENDING`、`AT_RISK`

**事件类型（示例）**：`MES_DELAY`、`MES_SCRAP`、`NEW_ORDER`、`MATERIAL_SHORTAGE`、`EQUIPMENT_DOWN`、`ORDER_EXPEDITE`、`MINOR_DELAY`

---

## 4. 详细技术方案

### 4.1 逻辑架构

```mermaid
flowchart TB
  subgraph presentation [表现层]
    UI[React SPA]
    Swagger[Swagger UI]
  end

  subgraph api [API 层]
    DR[DemandResource]
    PR[PlanningResource]
    SSR[ScheduleSessionResource]
    SBR[SchedulingBatchResource]
    PTR[ProductionTaskResource]
    WSR[WorkspaceResource]
    MDR[MasterDataResource]
    IR[IntegrationResource]
  end

  subgraph application [应用层 scenario]
    DS[DemandService]
    FPS[FulfillmentPeggingService]
    KS[KittingService]
    CS[CapacityService]
    MPS[MasterPlanService]
    DSS[DetailScheduleService]
    ES[ExecutionService]
    KpiS[KpiService]
    PO[PlanningOrchestrator]
    SCS[ScenarioComparisonService]
    STR[MasterPlanStrategyConfigService]
    DSSN[DetailScheduleSessionService]
    BSS[ProductionBatchSplitService]
    PTS[ProductionTaskService]
    WSS[WorkspaceService]
  end

  subgraph domain [领域/求解]
    TF1[MasterPlanSchedule]
    TF2[DetailSchedule]
    SIM[SimulationPipeline]
  end

  subgraph infra [基础设施]
    JPA[(H2 + Flyway)]
    ERP[MockErpAdapter]
    MES[MockMesAdapter]
  end

  UI --> DR & PR & SSR & SBR & PTR & WSR & MDR
  Swagger --> DR & PR & SSR & SBR & PTR & WSR & MDR
  DR --> DS & FPS
  PR --> KS & CS & MPS & DSS & ES & KpiS & PO & SCS & STR
  SSR --> DSSN
  SBR --> BSS
  PTR --> PTS
  WSR --> WSS
  MPS --> TF1
  DSS --> TF2
  DSSN --> SIM & TF2
  DS & FPS & KS --> JPA
  DSSN & BSS & PTS & WSS --> JPA
  PO --> ERP
  ES --> MES
```

### 4.2 包结构约定

| 包 | 职责 |
|----|------|
| `com.plantops.api` | JAX-RS 资源，无业务逻辑 |
| `com.plantops.api.dto` | 稳定 JSON 契约（Java record） |
| `com.plantops.scenario` | 用例服务，事务边界；含主计划、排程 Session、批次、执行反馈服务 |
| `com.plantops.scenario.planning` | 推演上下文、Session、SimulationPipeline 与 Timefold 映射边界 |
| `com.plantops.solver.*` | Timefold `@PlanningSolution` / `@PlanningEntity` |
| `com.plantops.workspace` | 工作区数据集解析、复制与默认工作区 |
| `com.plantops.persistence.entity` | Panache 实体 |
| `com.plantops.integration.*` | 六边形端口适配器 |
| `com.plantops.config` | CDI 生产者（双 SolverManager） |

### 4.3 数据模型（ER 概要）

```mermaid
erDiagram
  sales_order_line ||--o{ work_order : "SO line"
  work_order ||--o{ work_order : "parent_work_order_no"
  bom_component }o--|| product : "parent/component"
  sales_order_line }o--|| product : "product_code"
  work_order ||--o{ detail_schedule_operation : "work_order_no"
  work_order ||--o{ production_batch : "work_order_no"
  production_batch ||--o{ detail_schedule_operation : "batch_no"
  plan_version ||--o{ master_plan_allocation : "plan_version_id"
  plan_version ||--o{ detail_schedule_operation : "plan_version_id"
  detail_schedule_operation ||--o{ production_task : "step_id"
  production_task ||--o{ planning_conflict : "step_id"
  inventory }o--|| product : "product_code"
```

**Flyway 迁移**

当前仓库包含 V1–V48+ 迁移。不要只按版本号推断表结构，具体字段以 `src/main/resources/db/migration/` 与 JPA 实体为准。

| 版本范围 | 主题 | 代表文件 |
|----------|------|----------|
| V1–V10 | 基础业务表、工单父子、工艺元数据、工单下发、主计划策略与流水线运行 | `V1__schema.sql`、`V10__master_plan_strategies.sql` |
| V11–V13 | 工作区数据集与 legacy 唯一索引调整 | `V11__workspace.sql` |
| V14–V30 | 物料主数据、BOM/工艺扩展、规则集版本、并行/U 线/换线规则、MRP 与工单 pegging | `V16__bom_routing_extended_fields.sql`、`V29__mrp_merged_work_orders.sql` |
| V31–V41 | 后处理、提前期、日历、字段目录、业务规则作用域与描述 | `V37__factory_calendar.sql`、`V39__master_field_catalog.sql` |
| V42–V45 | 生产批次、批次序列、工艺名称回填与工序连接规则 | `V42__production_batch.sql` |
| V46–V48 | 生产任务、计划冲突、SimulationProfile、工厂日历/反馈冻结/连续批次推演规则 | `V46__production_task_and_planning_conflict.sql`、`V47__simulation_profile.sql`、`V48__phase3_simulation_extension_rules.sql` |

### 4.4 满足链对象结构（API）

```json
{
  "salesOrderNo": "SO-001",
  "salesOrderLineNo": 10,
  "productCode": "FG-100",
  "overallStatus": "PLANNED",
  "nodes": [
    {
      "nodeId": "so-SO-001-10",
      "nodeType": "SALES_ORDER",
      "label": "销售订单 SO-001-10",
      "depth": 0,
      "quantity": 500
    },
    {
      "nodeId": "inv-FG-100-1",
      "nodeType": "INVENTORY",
      "label": "库存 · FG-100",
      "depth": 1,
      "quantity": 80
    },
    {
      "nodeId": "wo-WO-001",
      "nodeType": "WORK_ORDER",
      "label": "工单 · WO-001",
      "depth": 1,
      "quantity": 420
    }
  ],
  "edges": [
    { "fromNodeId": "inv-FG-100-1", "toNodeId": "so-SO-001-10", "pegType": "INVENTORY_PEG" },
    { "fromNodeId": "wo-WO-001", "toNodeId": "so-SO-001-10", "pegType": "WORK_ORDER_PEG" }
  ]
}
```

**构建算法**（`FulfillmentPeggingService`）：

1. 创建销售订单节点（depth=0）。
2. `pegDemand(demander, product, qty, depth)`：
   - 扣减可用库存 → 库存节点 + 边；
   - 剩余需求 → 解析工单（SO 用根工单；工单用 `parentWorkOrderNo` 子工单）；
   - 展开工单 BOM，对每个关键子件递归 `pegDemand`；
   - 无法满足 → `SHORTAGE` 节点 + 边。
3. 内存维护库存扣减池，避免重复分配。

### 4.5 Timefold 求解设计

**为何不用 `timefold-solver-quarkus`**

工程内存在两个 `@PlanningSolution`（主计划 + 详细排程），Quarkus 扩展仅允许单一方案。采用 `SolverProducers` 手工注册两个 `SolverManager`。

**S04 主计划**

| 类型 | 类 | 说明 |
|------|-----|------|
| Solution | `MasterPlanSchedule` | 槽位列表、订单分配列表 |
| Entity | `OrderAllocation` | 决策变量：分配到的 `TimeSlot` |
| 约束 | `MasterPlanConstraintProvider` | 交期、优先级、产能硬约束；软约束含延期、优先级、锁定靠前、**相邻槽位负荷均衡** |
| 策略注入 | `MasterPlanStrategyConfigService.ResolvedStrategy` | 求解前解析产能模式与目标权重 |

**S05 详细排程**

| 类型 | 类 | 说明 |
|------|-----|------|
| Solution | `DetailSchedule` | 产线范围、工序列表 |
| Entity | `OperationAssignment` | 决策变量：产线；时间由 `DetailScheduleTimingKernel` / 后处理计算 |
| 约束 | `DetailScheduleConstraintProvider` | 顺序、换线、班次容量、批次与合同边界等 |
| 推演 | `DetailScheduleSessionService`、`SimulationPipeline` | 先应用拖拽补丁与规则闭包，再计算影响范围、违规、冲突与可确认版本 |

**推演 vs 优化边界**

- `POST /planning/schedule-sessions/{id}/simulate` 不做全局搜索，主要用于验证局部调整、规则覆盖与 `feedbackCutoff` 冻结边界。
- `POST /planning/schedule-sessions/{id}/optimize` 调用排程求解器，将当前 Session 上下文投影为 Timefold 问题再选优。
- `POST /planning/schedule-sessions/{id}/confirm` 才会持久化排程结果并发布生产任务；未确认的推演结果只存在于 Session 上下文。

### 4.6 全链路编排

`PlanningOrchestrator.runFullPipeline()` 是主计划流水线快捷入口；默认 `includeDetailSchedule=false`，不执行详细排程，也不发布生产任务。设置 `includeDetailSchedule=true` 时才会追加 S05 详细排程求解。

```text
需求准备 → 主数据校验 → 工单重建 → MRP 物料可行性 → 产能基线 → S04 主计划求解
  └─ 可选 includeDetailSchedule=true：S05 推演层构建 → S05 Timefold 详细排程求解
```

数据为空时自动加载 `factory-demo.json`。

交互式排程页通常不直接依赖流水线快捷入口，而是先从主计划版本、待排工单或批次创建 `schedule-session`，再在 Session 内推演、优化、确认。只有 `schedule-session/{id}/confirm` 会发布 `production_task`；流水线求解出的排程版本不会自动执行任务发布。

### 4.7 前端技术方案

| 项 | 方案 |
|----|------|
| 路由 | HashRouter（`/#/...`），避免刷新 404 |
| API | `fetch` + Vite 开发代理 `/api` → `:8080` |
| 状态 | `PlanContext`：主计划场景列表与选中场景；`WorkspaceContext`：当前工作区；`ScheduleVersionContext`：排程版本上下文 |
| 场景 UI | `ScenarioSelector` + `PageHeader.showScenarioSelector`（默认 false） |
| 甘特 | `gantt-task-react`；满足链用 `dependencies` 表示 pegging |
| 生产构建 | `npm run build` → `src/main/resources/META-INF/resources` |

### 4.8 集成端口

```java
public interface ErpPort {
    List<DemandPoolEntryDto> fetchOpenOrderLines();
}

public interface MesPort {
    // 下发、反馈轮询（Mock 实现）
}
```

替换真实 ERP/MES 时：新增 `@ApplicationScoped` 实现类，通过 CDI 覆盖 Mock。

### 4.9 配置项

| 键 | 默认 | 说明 |
|----|------|------|
| `quarkus.http.port` | 8080 | HTTP 端口 |
| `quarkus.datasource.jdbc.url` | H2 内存 | 数据源 |
| `plantops.sample-data.enabled` | true | 启动加载演示数据 |
| `quarkus.http.cors.origins` | `http://localhost:5173` | 前端开发跨域 |

### 4.10 测试策略

- `PlantOperationPlanResourceTest`：RestAssured 端到端，触发 S04/S05 求解并断言 HTTP 200。
- 单元测试可针对 `FulfillmentPeggingService`、`KittingService` 扩展（当前以集成为主）。

---

## 5. 部署方案

### 5.1 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 21（推荐 Eclipse Temurin） |
| Node.js | 18+（仅构建前端时需要） |
| 内存 | 开发 ≥ 4GB；求解时建议 ≥ 8GB |
| OS | Windows / Linux / macOS |

**无需全局 Maven**：使用 `mvnw.cmd` / `./mvnw`。

**PowerShell 脚本策略**：若 `npm` 报执行策略错误，使用 `npm.cmd` 或 `frontend/*.cmd`。

### 5.2 开发环境部署

**终端 1 — 后端**

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
.\mvnw.cmd quarkus:dev
```

- API：http://localhost:8080  
- Swagger：http://localhost:8080/q/swagger-ui  

**终端 2 — 前端（热更新）**

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan\frontend
.\install.cmd
.\dev.cmd
```

- UI：http://localhost:5173  

### 5.3 生产一体包部署（推荐演示/内网）

将前端打入 Quarkus 静态资源，单进程对外服务：

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan\frontend
.\build.cmd

cd ..
.\mvnw.cmd package -DskipTests
java -jar target\quarkus-app\quarkus-run.jar
```

访问：http://localhost:8080/#/

> `quarkus package` 生成 `target/quarkus-app/`，含 `lib/`、`app/`、`quarkus-run.jar`。

### 5.4 配置覆盖（生产）

创建 `application-prod.properties` 或通过环境变量：

```properties
# 示例：PostgreSQL
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://db-host:5432/plantops
quarkus.datasource.username=plantops
quarkus.datasource.password=${DB_PASSWORD}

plantops.sample-data.enabled=false

quarkus.http.cors.origins=https://aps.example.com
```

启动：

```bash
java -jar quarkus-run.jar -Dquarkus.profile=prod
```

### 5.5 容器化部署（参考）

**Dockerfile 多阶段示例思路**

1. 阶段 `node`：`frontend` 下 `npm ci && npm run build`  
2. 阶段 `maven`：复制前端产物到 `META-INF/resources`，`mvnw package -DskipTests`  
3. 阶段 `runtime`：`eclipse-temurin:21-jre`，复制 `quarkus-app/`，暴露 8080  

**Kubernetes 要点**

- Deployment：1 副本起步，求解 CPU 建议 limit ≥ 2  
- Service：ClusterIP 8080  
- Ingress：路径 `/` 指向服务；注意 SPA 使用 Hash 路由无需服务端 rewrite  
- 健康检查：`/q/health`（Quarkus 标准）

### 5.6 网络与端口

| 端口 | 用途 |
|------|------|
| 8080 | Quarkus HTTP（API + 静态前端） |
| 5173 | 仅开发态 Vite |

### 5.7 数据持久化说明

当前默认 **H2 内存库**，进程退出数据丢失。生产必须：

1. 切换 PostgreSQL / Oracle 等；  
2. 关闭 `plantops.sample-data.enabled`；  
3. 通过 ERP 接口或 `POST /demand/import` 灌入主数据。

---

## 6. 运维与扩展

### 6.1 日志与诊断

- Quarkus 开发模式控制台输出 Timefold 求解日志（阶段耗时、best score）。
- 求解超时可在 `SolverProducers` 中调整 `terminationSpentLimit`。
- 排程推演返回的 `appliedRules`、`violations`、`conflicts` 是定位 Session 调整问题的第一入口；更完整的规则说明见 `docs/detail-schedule-simulation-layer.md`。
- 主计划/排程诊断预览分别由 `/planning/master-plan/diagnostics/preview`、`/planning/detail-schedule/diagnostics/preview` 提供，前端在「推演诊断」页展示。

### 6.2 常见问题

| 现象 | 处理 |
|------|------|
| `mvn` 无法识别 | 使用 `.\mvnw.cmd` |
| `npm.ps1` 禁止脚本 | 使用 `npm.cmd` 或 `frontend\dev.cmd` |
| 满足链无上游工单 | 确认已重启后端以加载 V3 迁移与更新后的 `factory-demo.json` |
| 甘特无数据 | 先选中订单；检查 `/fulfillment-chain` 返回 |
| 产能页切换场景无变化 | 确认 `POST /capacity/analyze?masterPlanVersionId=` 传入选中场景 ID |
| 场景列表为空 | 先在「计划运行」选择策略并运行流水线 |
| 策略保存不生效 | 重启后检查 `system_parameter.master_plan_strategies`；保存后需重新求解 |
| 排程拖拽后被规则改回 | 检查推演结果中的 `appliedRules`，尤其是工厂日历、反馈冻结、批次连续规则 |
| 已开工工序无法移动 | 检查 `production_task` 状态与 simulate 请求中的 `feedbackCutoff`；RUNNING/COMPLETED 工序会参与冻结 |
| 批次没有进入待排池 | 确认批次为 ACTIVE、齐套已计算，并检查 `pendingScheduleEligible` 标记 |
| 页面数据看似“丢失” | 检查顶部工作区选择器，数据按当前工作区隔离 |

### 6.3 扩展建议

1. **ERP**：实现真实 `ErpPort`，定时同步订单。  
2. **MES**：`MesPort` 回写工序实绩，驱动 S06 事件。  
3. **满足链**：接入预留库存、采购在途、替代料。  
4. **排程**：恢复完整 chained 工序变量（当前为产线分配 + 后处理时间）。  
5. **权限**：在 Quarkus 增加 OIDC / JWT。  

---

## 7. 附录

### 7.1 REST API 一览

> 本表列公共接口族与关键端点；字段级契约以 `http://localhost:8080/q/swagger-ui` 和 `src/main/java/com/plantops/api/dto/` 为准。

**工作区、看板、主数据**

| Resource | 关键路径 | 说明 |
|----------|----------|------|
| `WorkspaceResource` | `GET/POST /api/v1/workspaces`、`GET/DELETE /api/v1/workspaces/{id}` | 工作区数据集列表、创建、查看、删除 |
| `DashboardResource` | `GET /api/v1/dashboard/summary` | 首页汇总 |
| `AdminResource` | `POST /api/v1/admin/reload-sample-data` | 重新加载示例数据 |
| `MasterDataResource` | `/api/v1/master-data/{sales-orders,boms,materials,inventory,resources,product-resources,lines,calendar,parameters,...}` | 主数据与规则基础数据 CRUD |
| `MasterDataExcelResource` | `/api/v1/master-data/excel/{template,export,import,...}` | 主数据、换线、并行工序、设备线别 Excel 导入导出 |
| `BusinessRuleExcelResource` | `/api/v1/business-rules/excel/{kind}/{template,export,import}` | 分类业务规则 Excel 模板、导出、导入 |
| `FactoryCalendarResource` | `GET/PUT /api/v1/factory-calendar/policy`、`GET /month`、`PUT /day`、`POST /sync` | 工厂日历策略、日期覆盖、同步到资源日历 |

**需求、物料、工单**

| Resource | 关键路径 | 说明 |
|----------|----------|------|
| `DemandResource` | `GET /api/v1/demand/demand-pool`、`/summary`、`/{so}/{line}/fulfillment-chain`、`POST /import` | 需求池、KPI、满足链、订单导入 |
| `DemandResource` | `POST /api/v1/demand/demand-pool/{so}/{line}/actions/{action}` | 订单侧计划动作 |
| `DemandResource` | `POST /api/v1/demand/work-orders/generate`、`/generate/{so}/{line}` | 生成工单 |
| `MaterialRequirementResource` | `GET /api/v1/material-requirements/balance`、`POST /compute`、`GET /materials/{productCode}/demand-usages` | MRP/物料平衡与需求用途树 |
| `WorkOrderResource` | `/api/v1/work-orders/*` | 工单列表、下发、齐套、待排标记、pegging、工艺详情、排程工序 |

**主计划与计划链路**

| Resource | 关键路径 | 说明 |
|----------|----------|------|
| `PlanningResource` | `POST /api/v1/kitting/compute`、`POST /api/v1/capacity/analyze?masterPlanVersionId=` | 物料齐套与产能分析 |
| `PlanningResource` | `POST /api/v1/planning/master-plan/solve?strategyId=`、`GET /planning/master-plan/result/{versionId}` | 主计划求解与结果 |
| `PlanningResource` | `POST /api/v1/planning/master-plan/preview`、`GET /planning/master-plan/diagnostics/preview`、`POST /planning/order-chain/preview` | 主计划推演、诊断、订单计划链 |
| `PlanningResource` | `POST/GET /api/v1/planning/pipeline-runs`、`POST /pipeline-runs/{runId}/execute`、`POST /planning/run-full-pipeline` | 主计划流水线；`includeDetailSchedule=true` 时追加详细排程求解 |
| `PlanningResource` | `GET /api/v1/planning/scenarios`、`POST /planning/scenarios/compare`、`GET /planning/compare` | 主计划场景和版本对比 |
| `MasterPlanStrategyResource` | `/api/v1/planning/master-plan/strategies/*` | 主计划策略 CRUD、默认策略、复制 |
| `MasterPlanObjectiveResource` | `/api/v1/planning/master-plan/objectives`、`/reset-defaults` | 优化目标权重配置 |

**生产排程、推演、执行反馈**

| Resource | 关键路径 | 说明 |
|----------|----------|------|
| `SchedulingBatchResource` | `GET /api/v1/scheduling/batches/work-orders`、`GET /by-work-order/{workOrderNo}` | 批次计划页工单与批次列表 |
| `SchedulingBatchResource` | `POST /split/auto`、`/split/auto-all`、`/split/manual`、`/cancel` | 批次拆分与取消 |
| `SchedulingBatchResource` | `GET/POST /kitting`、`GET /kitting/component/{productCode}/allocations`、`PATCH/PUT /{batchNo}/pending-schedule-eligible` | 批次齐套、组件占用、待排标记 |
| `ScheduleSessionResource` | `POST /api/v1/planning/schedule-sessions`、`GET /{sessionId}` | 创建/读取排程 Session |
| `ScheduleSessionResource` | `PATCH /{sessionId}/steps`、`POST /{sessionId}/simulate`、`POST /{sessionId}/optimize`、`POST /{sessionId}/confirm` | 局部补丁、推演、优化、确认发布 |
| `ScheduleSessionResource` | `GET /{sessionId}/operations/{operationId}/candidate-lines` | 工序候选产线 |
| `SimulationProfileResource` | `GET/POST /api/v1/planning/simulation-profiles`、`GET/DELETE /{profileId}` | 推演规则配置保存、读取、删除 |
| `PlanningResource` | `GET /api/v1/planning/detail-schedule/{versionId}`、`POST /detail-schedule/solve`、`POST /detail-schedule/preview` | 排程版本查询、求解、预览 |
| `PlanningResource` | `GET /api/v1/planning/detail-schedule/versions`、`POST /versions/compare`、`GET/POST /detail-schedule/page-kpis` | 排程版本列表、对比、页面 KPI |
| `ProductionTaskResource` | `GET /api/v1/production-tasks?executionState=`、`GET /{stepId}`、`POST /{stepId}/start`、`POST /{stepId}/complete` | 生产任务查询、开工、完工反馈 |

**集成 Mock**

| Resource | 关键路径 | 说明 |
|----------|----------|------|
| `IntegrationResource` | `GET /api/v1/integration/erp/orders`、`GET /api/v1/integration/mes/status` | Mock ERP 订单与 Mock MES 状态 |

### 7.2 示例数据场景（SO-001）

- 订单 500 件 FG-100。  
- 库存 80 件 FG-100 → 库存满足 80。  
- 工单 WO-001 满足剩余 420。  
- WO-001 需要 SFG-50 → 子工单 WO-001-SFG。  
- WO-001 需要 RM-B → 库存；WO-001-SFG 需要 RM-A → 库存。

### 7.3 相关文档

| 文档 | 路径 |
|------|------|
| 快速启动 | `README.md` |
| 架构摘要 | `docs/architecture.md` |
| APS 分层推演 | `docs/aps-planning-layer.md` |
| 详细排程 Session 推演 Runbook | `docs/detail-schedule-simulation-layer.md` |
| BOM / 工艺 / MRP 链路 | `docs/master-plan-bom-routing.md` |
| Timefold 2.0 升级说明 | `docs/timefold-2-upgrade.md` |
| Docker 部署 | `docs/docker-deploy.md` |
| 设计摘要 | `docs/superpowers/specs/2026-05-25-plant-operation-plan-design.md` |
| **帕累托扫描模式（v1 设计）** | `docs/pareto-scan-design.md` |
| 业务方法论 | 工作区根目录 `工厂计划*.md` |

### 7.4 变更记录

| 日期 | 内容 |
|------|------|
| 2026-05-25 | 初版：S01–S07 场景、Timefold 双求解器、满足链、React 前端 |
| 2026-05-27 | 主计划策略体系（产能模式 + 目标权重 CRUD）；产能均衡软目标；计划运行选策略；场景列表/对比展示策略名；`PlanContext` 场景选择器；产能页按场景分析；导航重命名与结果页分组；场景选择器仅保留于四个计划结果页；四结果页绑定 `masterPlanVersionId` |
| 2026-05-28 | 帕累托扫描模式产品设计 v1（`docs/pareto-scan-design.md`）：权重网格批量求解、分目标 KPI、非支配前沿、帕累托探索页 |
| 2026-06-02 | 工作区数据集、MRP/工单链路、生产批次、详细排程 Session 推演、生产任务发布、SimulationProfile 与推演规则扩展进入主流程；Timefold 升级到 2.0.0 |
| 2026-07-06 | 同步 README 与本项目文档：更新导航、API 族、迁移范围、S05/S06 工作流与深度文档索引 |

---

*本文档随代码演进更新；以仓库内实现为准。*
