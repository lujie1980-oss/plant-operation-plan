# Plant Operation Plan — 产品说明文档（PDD）

> **文档类型：** Product Description Document（产品说明 / 产品功能介绍）  
> **产品版本：** 1.0.0-SNAPSHOT  
> **架构基线：** Plant Operation Ontology（本体直驱 **PROC-S04** 计划求解）  
> **文档日期：** 2026-06-20  
> **配套文档：** [SDD.md](./SDD.md)（规范分章 [`sdd/`](./sdd/)）· [sdd/core/05-domain-model.md](./sdd/core/05-domain-model.md) · [sdd/volumes/platform/17-ui-ux.md](./sdd/volumes/platform/17-ui-ux.md) · [sdd/volumes/platform/18-19-workspace-platform.md](./sdd/volumes/platform/18-19-workspace-platform.md) · [otd-ontology-mapping.md](./otd-ontology-mapping.md)

---

## 1. 文档目的

本文档面向业务方、产品经理、实施顾问与测试人员，说明 **Plant Operation Plan（工厂运营计划系统）** 的产品定位、目标用户、业务场景、功能边界与界面地图。不涉及类级实现细节（见 [SDD.md](./SDD.md)）。

---

## 2. 产品定位

**Plant Operation Plan** 是一套面向 **单工厂** 的高级计划与排程（APS）系统，将销售需求、物料计划、粗能力平衡、**订单协同计划**、**作业排程**、执行反馈与 KPI 串联为一条可运行的计划链路。

> **术语：** 界面模块 **订单协同计划（MOD-OCP）** 承载计划运行与分析；求解过程 **主计划求解（PROC-S04）** 为工序级时间槽分配引擎，二者不可混称（见 [SDD §2](./sdd/core/02-glossary.md)）。

### 2.1 核心价值

| 价值主张 | 说明 |
|----------|------|
| **提高按时满足率** | 以 COLD 为粒度贯通满足链，提前识别 PEG-SH 缺口，支撑 OTIF 提升 |
| **缩短订单响应周期** | 沙盘 simulate → optimize → confirm，缩短从接单到可承诺交期 / 计划确认的时间 |
| **缩短生产周期** | 订单协同计划与细排协同压缩工单 Makespan 与工序等待 |
| **提升库存周转** | PISPP 期间平衡与 PEG-INV 结构优化，在保障交付前提下降低占用 |
| **提高设备有效产出** | SRP 产能平衡与 S05 产线排程，提升瓶颈利用率、抑制长期超载 |
| **提高计划制定效率** | 推演/选优分层、沙盘 confirm，减少重复跑批 |
| **风险可视化** | 以 COLD 为根展示订单交付全过程，前置识别 PEG-SH 缺口与计划信号 |
| **决策可视化** | 多 planVersion 场景对比，输出延期、产能、工单等量化差异 |

> 规范 ID 与 KPI 见 [SDD §1](./sdd/core/01-value-goals.md)（VAL-01~06）。

### 2.2 与 OTD 的关系

本产品实现 OTD（On-Time Delivery）本体语义的一个 **MPS 子集**，对齐 OTD v4 中供需、工艺、满足链与期间物料平衡概念。映射基线见 [otd-ontology-mapping.md](./otd-ontology-mapping.md)。

---

## 3. 目标用户

| 角色 | 主要诉求 | 典型功能入口 |
|------|----------|--------------|
| **计划员** | 订单协同计划运行、需求满足分析、产能与物料平衡、场景对比 | 订单协同计划 · 计划运行 · 计划分析 · 本体推演 |
| **排程员** | 批次拆解、产线甘特、手动调序与推演确认 | 作业排程 · 批次计划 · 生产排程 |
| **物料计划** | 齐套、MRP 缺料追溯、物料期间平衡 | 订单协同计划 · 物料计划；作业排程 · 齐套 |
| **生产主管** | 产能负荷、工单下发、交付跟踪 | 产能平衡、生产工单、需求跟踪 |
| **工艺/数据管理员** | 主数据、工艺路线、日历、外部数据导入与质检 | 数据集成、工厂日历；过渡期仍可用主数据 / 业务数据 |
| **分切计划员**（扩展） | 母卷分切排样优化 | 分切排样 · 母卷分切 Studio |
| **Workspace 管理员** | 成员、模块开关、数据集隔离 | 数据集管理、Workspace 设置（IAM · **已落地**） |

---

## 4. 典型业务场景

### 场景 A：从订单到订单协同计划

销售订单进入需求池后，计划员需要回答：哪些交付批次能按时满足？瓶颈资源如何分配？不同策略对 OTIF 与产能负荷有何影响？

**典型路径：**

1. 维护或导入销售订单 → MRP 展开多级 BOM 工单  
2. 在 **订单协同计划 · 需求满足** 页：按 `COLD-*` 展示满足链（库存 peg / 工单 peg / 缺口 peg）  
3. 运行齐套与物料计划，识别缺料  
4. 在 **计划运行** 选择计划策略，执行 **PROC-S04 主计划求解**  
5. 在 **场景对比** 与计划分析各子页切换 `planVersionId` 对比结果  

### 场景 B：单交付批次沙盘推演（本体路径）

针对某一交付批次，计划员希望在不影响全厂数据的前提下，试算交期变更、产能占用或上游工单创建的影响。

**典型路径：**

1. 需求满足页选中 `CustomerOrderLineDelivery`  
2. 打开满足链甘特 / 物料面板，查看 `OrderFulfillmentChainDto`  
3. 进入 **订单协同计划 · 本体推演** 或交付级 Sandbox：`simulate` 修改 needDate / SRP → `optimize` → `confirm`  
4. 前端通过 `PlanningSignalBadge` 展示本体层信号（非旧版实体路径「推演诊断」）  

### 场景 C：从工单到产线排程

订单协同计划（PROC-S04）完成后，**作业排程** 以 **批次** 为最小排程单位。

**典型路径：**

1. 已下发工单在 **批次计划** 拆批（固定批量 / 齐套拆批 / 手工）  
2. **排程齐套** 按批次量消耗库存池  
3. **作业排程 · 生产排程** 创建 Scheduling Session：种子入队 + 链式赋时 + 可选 Timefold 选优  
4. 手动改序 → simulate → confirm 落库为排程版本  

### 场景 D：计划变更与闭环

MES 反馈设备停机、缺料、加急等事件，系统按规则级别决定是否重算 **PROC-S04** 或 **PROC-S05** 详细排程；排程反馈可冻结 cutoff 前的工序计划时间。

### 场景 E：多数据集并行验证

通过 **Workspace** 隔离不同工厂/项目/演示数据集（如 `default`、`te`、`jinghua`），切换后主数据、计划、排程完全独立，便于 POC 与回归测试。

### 场景 F：母卷分切排样（扩展模块）

针对卷材行业，在 **分切排样（MOD-SLT）** 模块维护母卷、子卷订单与 BOM 范围，运行 Timefold 嵌套排样求解，在 Studio 画布上交互调整。

### 场景 G：外部数据导入与质检（数据集成）

工艺管理员或实施顾问从 ERP / MES / Excel 导入主数据与交易数据，经质检后同步至内部 `md_*` / `txn_*`，供计划链路读取。

**典型路径：**

1. 打开 **数据集成** 概览，查看最近导入批次与适配器状态  
2. 在 **External 主数据 / 交易** 浏览 staging 表行与 `quality_issue_codes`  
3. 配置并触发 **ERP SAP / MES / Excel** 适配器同步（或上传 Excel）  
4. 在 **质检报告** 修正失败行后，由集成流程 sync 至 canonical 表  
5. 计划模块仅读 **md_* / txn_***，不直接消费 external_*（RULE-MD-01）

---

## 5. 核心能力一览

### 5.1 计划流水线 S01–S07

| 步骤 | 能力 | 说明 | 典型入口 |
|------|------|------|----------|
| **S01** | 需求满足 | 订单 KPI、满足链追溯（库存/工单/缺料 pegging）；主粒度 **COLD** | 订单协同计划 · 需求满足 |
| **S02** | 齐套分析 | 订单行/工单级关键料齐套，`KITTING_OK` / `SHORTAGE` | 订单协同计划 · 物料计划；作业排程 · 齐套 |
| **S03** | 产能平衡 | 资源×期间负荷、利用率、超载（SRP） | 订单协同计划 · 产能平衡 |
| **S04** | 主计划求解（PROC-S04） | 工序级时间槽分配；**PATH-ONT** 本体直驱为规范路径 | 订单协同计划 · 计划运行；本体推演 |
| **批次** | 批次拆解 | 已下发工单拆批，S05 以批次为最小单位 | 作业排程 · 批次计划 |
| **S05** | 详细排程（PROC-S05） | Session 推演 + Timefold 选优，产线甘特 | 作业排程 · 生产排程 |
| **S06** | 执行闭环 | 下发 MES、生产任务状态、事件驱动重排 | 生产工单、需求跟踪 |
| **S07** | KPI | OTIF、利用率、计划版本对比 | 需求跟踪、场景对比 |

### 5.2 本体与满足链

| 能力 | 说明 |
|------|------|
| **OntologyGraph** | 内存只读聚合图，承载供需、工序、满足、期间物料与产能 |
| **需求语义链** | `CustomerOrderLine` → `CustomerOrderLineDelivery` → `Demand` |
| **供应语义链** | `SupplyOrder` → `PlanUnit` → `Operation` → OOSR / OIM / OOM → `Supply` |
| **Fulfillment** | `Demand` ↔ `Supply` 挂接边，类型：`INVENTORY_PEG` / `WORK_ORDER_PEG` / `SHORTAGE_PEG` |
| **BomDependency** | 父子工单 BOM 依赖，由 Fulfillment **派生**（非 JPA 直读真相源） |
| **工艺模板** | `PISP` → `Routing` → `RoutingStep` → RSOSR / RSIM / RSOM，装载时投影为 `Operation` 族 |
| **PISPP MRP** | 全物料 `ProductInStockingPointPeriod` 期间平衡，支撑物料硬约束 |
| **本体推演 Session** | simulate（ROL）→ optimize → confirm，TTL 约 8 小时 |

### 5.3 求解器能力

| 求解器 | 用途 | 状态 |
|--------|------|------|
| **Timefold** | PROC-S04 槽位分配（可配置）、PROC-S05 产线排序、分切嵌套 | 生产可选 |
| **OR-Tools** | 本体路径 **PROC-S04** 默认引擎（`PlanningOptimizer` 插件） | 默认 |
| **确定性推演** | MRP、齐套、链式赋时、JIT 倒排 | 始终启用 |

系统参数 `planning_optimizer_engine` 控制 **PROC-S04** 选用 OR-Tools 或 Timefold（`PATH-ONT` 为本体直驱规范路径；实体路径已废止，见 ADR-08）。

### 5.4 已移除 / 收敛的能力

| 原能力 | 现状 |
|--------|------|
| **推演诊断**（实体路径 `PlanningDiagnosticsPanel`） | 已移除；前端改用语义层 `PlanningSignalBadge` + 本体满足链 |
| **订单推演链独立页**（`OrderPlanningChainPage`） | 已收敛至需求满足 + `OrderFulfillmentChainDto` |
| **齐套独立页**（`KittingPage`） | 合并至物料计划 / 排程齐套 |

---

## 6. 功能模块与界面地图

> **规范详述：** 路由、组件、SCN 映射、验收见 [SDD §17 UI/UX 规范](./sdd/volumes/platform/17-ui-ux.md)。本节为业务向概览。

### 6.1 导航结构（当前）

Workspace 侧栏按 **模块（MOD-*）** 组织；业务规则 **内嵌于各计划模块**，不再使用全局「业务规则」菜单（旧路径 `/business-rules/*` 自动重定向）。

```
首页
数据集成 (MOD-DI)
  ├─ 集成概览              /#/integration
  ├─ External 主数据       /#/integration/external/master
  ├─ External 交易         /#/integration/external/transactional
  ├─ 适配器                /#/integration/adapters
  │    ├─ ERP SAP          /#/integration/adapters/erp-sap
  │    ├─ MES              /#/integration/adapters/mes
  │    └─ Excel 导入       /#/integration/adapters/excel
  ├─ 质检报告              /#/integration/quality
  └─ 工厂日历              /#/factory-calendar   (MOD-CAL)
订单协同计划 (MOD-OCP)      /#/master-plan/*
  ├─ 计划参数              /#/master-plan/parameters
  ├─ 优化目标              /#/master-plan/objectives
  ├─ 计划运行              /#/master-plan/plan-run
  ├─ 本体推演              /#/master-plan/ontology
  ├─ 数据模型              /#/master-plan/data-model
  ├─ 场景对比              /#/master-plan/scenario-comparison
  ├─ 计划分析
  │    ├─ 需求满足         /#/master-plan/analysis/demand
  │    ├─ 产能平衡         /#/master-plan/analysis/capacity
  │    ├─ 物料计划         /#/master-plan/analysis/material-planning
  │    └─ 生产工单         /#/master-plan/analysis/work-orders
  └─ 业务规则
       ├─ 需求规则         /#/master-plan/rules/demand
       ├─ 产能规则         /#/master-plan/rules/capacity
       └─ 物料规则         /#/master-plan/rules/material
作业排程 (MOD-SCH)         /#/scheduling/*
  ├─ 计划参数 / 待排工单 / 批次计划 / 物料齐套 / 生产排程 / 版本对比
  └─ 业务规则
       ├─ 生产规则         /#/scheduling/rules/production
       └─ 人力规则         /#/scheduling/rules/labor
分切排样 (MOD-SLT)         /#/slitting/*
  └─ 基础数据 / 优化参数 / 优化运行 / 母卷分切 Studio
数据集                     /#/workspaces
需求跟踪                   /#/demand-tracking
```

**过渡期 legacy（映射 MOD-DI）：** `/master-data` · `/business-data` 仍可用，规范目标为数据集成模块内入口。

**顶栏：** Workspace 选择器；切换数据集后计划与主数据完全隔离（SCN-T03）。

### 6.2 模块说明

| 模块 | 用户目标 | 关键输出 |
|------|----------|----------|
| **数据集成（MOD-DI）** | 导入 ERP/MES/Excel，质检 staging，同步 canonical | `external_*` 批次、质检报告、`md_*` / `txn_*` |
| **工厂日历（MOD-CAL）** | 维护资源可用日历 | 日历模板、例外日（归属数据集成分类） |
| **订单协同计划（MOD-OCP）** | 跑 PROC-S04、分析满足链与产能物料、配置需求/产能/物料规则 | `planVersionId`、`OrderFulfillmentChainDto`、KPI-MP-* |
| **作业排程（MOD-SCH）** | 分钟级产线甘特、手动调序、生产/人力规则 | `DetailSchedule`、排程版本 |
| **分切排样（MOD-SLT）** | 母卷嵌套排样优化 | 分切方案、Studio 布局 |
| **数据集（Workspace）** | 多工厂/项目/POC 数据隔离 | 独立 ENT-WS、`ownerUserId` |
| **需求满足** | 看清每个交付批次的满足路径与承诺交期 | `OrderFulfillmentChainDto`、甘特、物料面板 |
| **本体推演** | 在沙盘内试算并确认 PROC-S04 结果 | `MasterPlanSessionDto`、Operation 时间窗 |
| **数据模型** | 查看 Routing 主数据投影 | `RoutingDto`、`RoutingStepDetailDto` |
| **计划运行** | 一键跑 S01–S05 流水线 | `planVersionId`、排程版本 |

**业务规则：** 按模块分域 — OCP 含需求/产能/物料；SCH 含生产/人力。权限随 **MOD-OCP / MOD-SCH** 的 VIEW/EDIT（§18 · §19.4.5）。

---

## 7. 关键业务概念

### 7.1 前端主粒度：CustomerOrderLineDelivery

- **ID 格式：** `COLD-{salesOrderNo}-{lineNo}-{seq}`  
- **含义：** 销售订单行的交付批次（当前实现多为 1:1 合成）  
- **关联：** 每个 COLD 对应一条 `Demand`（`CUSTOMER_DELIVERY`）  

用户在前端看到的「订单行满足链」「有限能力试算」均以 COLD 为根，而非裸 `SalesOrderLine`。

### 7.2 工单与供应订单

- `SupplyOrder.id` = `WorkOrderEntity.workOrderNo`（同一业务对象的双层投影）  
- 工单由 MRP 根据销售需求与 BOM 展开生成  

### 7.3 满足链挂接顺序

1. **库存优先**（`INVENTORY_PEG`）  
2. **工单供应**（`WORK_ORDER_PEG`）  
3. **缺口**（`SHORTAGE_PEG`）  

### 7.4 计划场景（Scenario）

一次 **PROC-S04 主计划求解** 产生一个 `planVersionId`。场景选择器在订单协同计划分析页共享，切换后联动重载 API。

### 7.5 订单协同计划策略

可命名、可复用的策略包（**计划运行** 选用），包含：

- `capacityStrategy`：`UNCONSTRAINED` / `FINITE_CAPACITY`  
- `objectives[]`：延期最小化、优先级、锁定订单、产能均衡等软目标  

---

## 8. 范围边界

| 在范围内 | 不在范围内（当前版本） |
|----------|------------------------|
| 单工厂、多产线、多 Workspace | 多工厂网络计划 |
| Workspace 成员与 **MOD-* 模块开关**（IAM · **已落地 2026-06**）；登录后 **手动** 创建工作区 | 完整企业 IdP 生产部署 / 多租户 SaaS RBAC |
| H2 文件库 + Flyway（可扩展 PostgreSQL） | 生产级 HA 数据库集群 |
| **数据集成模块** + ERP/MES/Excel 适配器契约（ADP-*） | 全部连接器生产就绪（当前前端骨架 + Mock） |
| React Web（HashRouter · Ant Design Shell） | 移动端原生应用 |
| Plant Operation Ontology 直驱 + 求解器插件化 | 全量 OTD 全模块对标 |

---

## 9. 架构演进

> 原 M1–M5 里程碑编号已停用；演进计划待重新制定。现行基线：**Plant Operation Ontology**（见 [SDD §0](./sdd/core/00-meta.md)）。

| 现行能力 | 说明 |
|----------|------|
| 本体直驱 + 供需语义链 | COLD 满足链、Session 沙盘、Routing 数据模型 |
| **Workspace 模块化** | MOD-DI / MOD-OCP / MOD-SCH / MOD-SLT / MOD-CAL；规则内嵌计划模块 |
| 求解器插件化 | `PlanningOptimizer` 接口；OR-Tools 默认 PROC-S04，Timefold 用于细排与分切 |

---

## 10. 相关文档

| 文档 | 用途 |
|------|------|
| [SDD.md](./SDD.md) | 详细设计：场景、规则、API、验收 |
| [sdd/volumes/platform/17-ui-ux.md](./sdd/volumes/platform/17-ui-ux.md) | UI 路由、组件、SCN 映射 |
| [sdd/volumes/platform/18-19-workspace-platform.md](./sdd/volumes/platform/18-19-workspace-platform.md) | 用户、Workspace 成员、MOD 权限、ADP |
| [sdd/core/05-domain-model.md](./sdd/core/05-domain-model.md) | Plant Operation Ontology 领域模型（§5） |
| [sdd/volumes/knowledge/15-16-planning-knowledge.md](./sdd/volumes/knowledge/15-16-planning-knowledge.md) | PROC-S04 KPI（MOD-OCP UI 消费） |
| [ontology-domain-model.drawio](./ontology-domain-model.drawio) | 领域模型 draw.io 图源 |
| [otd-ontology-mapping.md](./otd-ontology-mapping.md) | OTD ↔ Java 映射表 |
| [aps-planning-layer.md](./aps-planning-layer.md) | 推演层与 Timefold 分工 |
| [ontology-optimizer-plugin.md](./ontology-optimizer-plugin.md) | 求解器插件设计 |
| [docker-deploy.md](./docker-deploy.md) | 部署说明 |

---

*Plant Operation Plan · 产品说明文档 · 2026-06-20*
