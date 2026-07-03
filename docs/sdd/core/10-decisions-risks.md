# §10 决策、假设、风险与待办

## ADR-01 本体为 Session 真相源

| 项 | 内容 |
|----|------|
| **状态** | 已采纳 |
| **背景** | simulate 后 optimize 若重扫 DB 会丢失沙盘修改 |
| **决策** | S04 optimize/confirm 仅以 ENT-OG 为输入（ADR-08：PATH-ONT 为唯一规范路径） |
| **备选** | 每次 optimize 从 JPA 重建（否决：断层） |
| **后果** | 需对等性测试；confirm 写回 JPA |

---

## ADR-02 双路径并存 + Feature Flag

| 项 | 内容 |
|----|------|
| **状态** | **已被 ADR-08 取代**（2026-06-20） |
| **原决策** | `ontology_direct_solve_enabled` 切换 PATH-ENT / PATH-ONT |
| **取代原因** | 双轨维护成本高；ADR-01/07 已确立 ENT-OG 为推演真相源，PATH-ENT 无长期产品价值 |

---

## ADR-03 移除实体路径推演诊断 UI

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06） |
| **背景** | 诊断基于 WorkOrderEntity + ProductRoutingSteps，与 ENT-OG 语义偏离 |
| **决策** | 删除 diagnostics preview API 与前端 Panel；保留 `PlanningSignalBadge` |
| **备选** | 双轨诊断（否决：维护成本高） |
| **后果** | RULE-DIAG-01 废止；SCN 不再引用诊断页 |

---

## ADR-04 BomDependency 派生而非 JPA 直读

| 项 | 内容 |
|----|------|
| **状态** | 已采纳 |
| **决策** | ENT-BD 由 ENT-FF 追溯生成 |
| **备选** | WorkOrderBomDependencyEntity 为真相源（否决：与 peg 不一致） |

---

## ADR-05 求解器插件化

| 项 | 内容 |
|----|------|
| **状态** | 进行中（**主计划**已插件化；**详细排程 / 分切**配置化见 TODO-07） |
| **决策** | 主计划经 `PlanningOptimizer` 接口 + 系统参数 `planning_optimizer_engine` 路由（**默认 `ortools`**；Timefold 须显式配置） |
| **当前范围** | PROC-S04 / ENT-SES / ENT-SBX 有限能力 trial |
| **后续范围** | PROC-S05 详细排程、分切 Studio 仍直连 Timefold 与独立 solver 参数；纳入 TODO-07，不阻塞主计划 ADR-05 收口 |
| **实现细节** | 见 [ontology-optimizer-plugin.md](../ontology-optimizer-plugin.md)（HOW，非规范行为） |

---

## ADR-06 COLD 为前端主粒度

| 项 | 内容 |
|----|------|
| **状态** | 已采纳 |
| **决策** | 满足链、有限能力 trial 以 ENT-COLD 为根 |
| **现状** | 多 1:1 合成 COLD；未来支持多批次 |

---

## ADR-07 Workspace 单一 OntologyGraph 真相源

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | 全厂 `loadForWorkspace` 与单 COLD `buildDeliveryFulfillmentProjectionGraph` 并存，导致规范/实现不一致；CTP 需全厂产能状态；双图易漂移 |
| **决策** | 每个 ENT-WS **一张权威 ENT-OG**；simulate / optimize / confirm 仅在此图上进行。单 COLD = **视图 + scoped 子问题**，不是第二张并行图 |
| **备选** | 单订单独立投影图作 SoT（否决：CTP 乐观、与 ADR-01 冲突、双套 RULE） |
| **只读例外** | `buildDeliveryFulfillmentProjectionGraph` 仅作**无 Session** 的满足链只读 API（过渡）；**不得** simulate / optimize / confirm（RULE-SES-04） |
| **后果** | ENT-SBX 对齐 ENT-SES 装载；内存靠 NFR/TTL/并发 Session 上限，而非裁图 |

---

## ADR-08 废止 PATH-ENT（实体路径主计划）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | PATH-ENT（`MasterPlanPlanningContextBuilder` 扫 JPA → 求解）与 ADR-01/07 冲突：simulate 后 optimize 易与 Session 沙盘脱节；双路径对等（ADR-02）增加 CI 与认知成本 |
| **决策** | **废弃 PATH-ENT 作为产品能力**。PROC-S04、ENT-SES、ENT-SBX 有限能力 trial、计划流水线 S04 **统一 PATH-ONT**：权威 ENT-OG → `OntologyToMasterPlanScheduleMapper` → `PlanningOptimizer` → 写回 ENT-OG → confirm 落 JPA |
| **废止** | `ontology_direct_solve_enabled` 开关（迁移完成后删除或固定 true）；Session `optimizeLegacy`（基线 allocation 抄录） |
| **保留（过渡）** | ~~`MasterPlanPlanningContextBuilder` 与 AC-05 对等测试~~ **已移除（2026-07-01 · TODO-08 收口）** |
| **不含** | S05 详细排程、分切仍各自装载逻辑（与 PATH-ENT/PATH-ONT 无关，见 TODO-07） |
| **后果** | 实现待办 TODO-08；规范上 RULE-SES-03 适用于全部 S04 optimize/confirm，不再标注「PATH-ONT 限定」 |

---

## ADR-09 全量 Ontology 持久化（ont_* 表 · SQL 可查）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20）· **P0~P5 骨架已落地（2026-06-30）** · **收口 Sprint 6C~6D 已完成（2026-07-01）** |
| **背景** | 现行 JPA 与 ENT-OG 不同构；Session 纯内存；宕机丢失沙盘；PISPP/FF/BD 难以 SQL 查询 |
| **决策** | **以 Ontology 为基准**，引入 **`ont_revision` + `ont_*` 实体表**，与 ENT-* 1:1；`OntologyGraph` 由 **`WorkspaceAuthoritativeOntologyGraphService`**（`OntologyLoader` 壳 + `OntologyP0Overlay`）与 **`OntologyRestorer`** 组装；simulate/optimize/confirm **同事务**写 revision。Partial 持久化为 FULL 的 **存储策略子集**（§5.16），非第二套 schema |
| **备选** | 继续 Loader 重算 + 仅 confirm allocation（否决：与「表=Ontology」目标不符）；整图 JSON blob（否决：SQL 不可查，作可选 snapshot 加速） |
| **BomDependency** | COMMITTED revision 中 **`ont_bom_dependency` 为真相**；派生算法用于 DRAFT 校验与 Partial DERIVE 模式 |
| **与 ADR-07** | 仍 **每 Workspace 一张权威 OG**；权威 = `ont_revision_head(WORKSPACE)` 的 COMMITTED revision |
| **与 ADR-04** | ADR-04 约束 **legacy** `WorkOrderBomDependencyEntity`；ADR-09 以 **`ont_bom_dependency`** 取代其规范地位 |
| **迁移** | 双写 → 切读 `ont_*`（**P4 已落地**）→ 退役 legacy 写路径（TODO-12 收口）；`OntologyLoader` → 内部壳 + `OntologyLegacyImporter` |
| **后果** | Flyway 大迁移（**P0 已完成**）；Session/沙盘写路径（**P2/P3 已落地**）；§8 AC-PERS-*；存储与写放大 ↑ |

---

## ADR-10 External_* 主数据分层（质检 → md_* → 计划）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | 主数据来自多上游；legacy `material` / `product_resource` 与 Ontology ENT-RT/RS 不同构；计划需可 SQL 审计的 canonical 层 |
| **决策** | 引入 **`external_*` staging** + **`md_*` internal**；上游 **仅** 写 external；**质检通过** 后 sync 至 md；主计划 **只读 md**（RULE-MD-01） |
| **External 表** | `external_stocking_point`, `external_product_in_stocking_point`, `external_routing`, `external_routing_step`, `external_routing_step_on_standard_resource`, `external_routing_step_input_material`, `external_routing_step_output_material`, `external_resource_group`, `external_standard_resource`, `external_physical_resource` |
| **质量** | 公共列 `quality_status`, `quality_issue_codes`, `is_blocked`；问题码 MD-Q-*（§11.4.2） |
| **备选** | 继续 Excel 直写 legacy 表（否决：无质检、与 Ontology 脱节） |
| **迁移** | TODO-13：Flyway + Importer；`MasterPlanRoutingProjector` 切 md；legacy 只读对照至退役 |
| **后果** | 新 §11；API 导入/质检/同步；SCN-T04 扩展质量场景 |

---

## ADR-11 External 交易数据分层（`txn_*` · Firm SupplyOrder）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | 订单/工单/库存/PO 来自 ERP；与 §11 主数据不同生命周期；需 Firm 工单与计划内 REGENERATABLE 区分 |
| **决策** | 引入 **交易 staging `external_*`** + **内部 `txn_*`**；sync 路径独立于 `md_*`；**Firm 外部工单 → `txn_supply_order.firm_status=FIRM`** + PlanUnit/Operation/OSR 树 |
| **External 表** | `external_customer_order`, `external_customer_order_line`, `external_customer_order_line_delivery`, `external_work_order`, `external_work_order_operation`, `external_work_order_operation_resource`, `external_inventory`, `external_purchase_order` |
| **Internal 表** | `txn_customer_order*`, `txn_demand`, `txn_supply_order`, `txn_plan_unit`, `txn_operation`, `txn_operation_osr`, `txn_inventory_balance`, `txn_purchase_order` |
| **与 ADR-09** | committed ENT-OG 可投影为 `ont_*`；`txn_*` 为交易 canonical，装载 OG 的输入之一 |
| **迁移** | TODO-14；legacy `sales_order_line` / `work_order` / `inventory` 双写后退役 |
| **后果** | 新 §12 · RULE-TX-01~10 · AC-TX-* |

---

## ADR-12 业务知识三层（Standard / Industry / Customized）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | 同一产品多行业、多客户；§4 RULE 与 BusinessRules CFG 混放；缺少 overlay 与优先级 |
| **决策** | **StandardKnowledge** = SDD RULE/SCN + 默认参数；**IndustrySpecificKnowledge** = 可安装 Industry Pack；**CustomizedKnowledge** = Workspace overlay + BusinessRules；运行时 **`KnowledgeResolver` → Effective Knowledge** |
| **hard 规则** | 仅 Standard 定义；Industry/Custom **不得**削弱，仅 **KN-RULE-EXEMPT** 显式豁免 |
| **优先级** | Custom > Industry > Standard（同 KEY） |
| **备选** | 每客户 fork 代码分支（否决）；纯 CFG 无规范 RULE（否决：不可验收） |
| **后果** | §13 · TODO-15 · `Workspace.industry_id` · AC-KN-* |

---

## ADR-13 用户与 Workspace 权限（IAM）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | v1 仅 `X-Workspace-Id` 头隔离，任何人知 id 即可访问；多用户/多项目需要成员与模块级授权 |
| **决策** | **ENT-USR** ↔ **ENT-WS** 多对多成员；WS 级 **MOD-*** 开关 + 成员 **VIEW/EDIT** 矩阵；**ROLE-SUPER-ADMIN** 管理平台用户与全部 WS IAM；**v1** 工作区 **手动创建**（不随登录自动 PERSONAL WS） |
| **模块模型** | 计划/集成模块注册于 **`workspace-modules.yaml`** + **`integration-adapters.yaml`**；新模块须 MOD-EXT / ADP-EXT 契约 |
| **与 RULE-WS-01** | IAM 在 WS 行级隔离 **之前** 校验；二者叠加 |
| **备选** | 外置网关独占授权（部分采纳：生产 IdP）；纯 WS 头无用户（否决：生产） |
| **后果** | §18 · RULE-IAM-01~06 · API-IAM-* · SCN-T06 · ~~TODO-18~~ **已完成 2026-06** · AC-IAM-* |

---

## ADR-14 Workspace 模块分层与数据集成适配器

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-20） |
| **背景** | 原 MOD-DATA 混合 internal 页与集成；缺少 ERP/MES/Excel 标准适配器模型 |
| **决策** | **MOD-DI**（数据集成）展示 **external_*** + **ADP-***；**MOD-OCP/SCH/SLT** 含模块内业务规则；**MOD-CAL** 工厂日历 |
| **Phase 1** | Excel + SAP ERP + MES 适配器契约；`/integration` UI |
| **后果** | §19 · SCN-T07 · TODO-19 · AC-INT-* · `workspace-modules.yaml` |

---

## ADR-15 ENT-RCA 纳入 Ontology（产能占用边）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-21） |
| **背景** | 现行 `ResourceCapacityAssignment` 仅存在于 `solver.masterplan`，绑定 `TimeSlot` 日槽；与 ENT-SRP 的 `reservedCapacity`、RULE-MP 超载语义脱节；confirm 写 audit allocation 而非本体占用 |
| **决策** | **ENT-RCA**（ResourceCapacityAssignment）为 **ENT-OG 内**实体：一条 RCA = **ENT-OP** 经 **ENT-OOSR** 在 **ENT-SRP** 上的 `assignedMinutes`；`Σ RCA → SRP.reservedCapacity`（ROL） |
| **与 ENT-SS** | ENT-SS / `TimeSlot` 为日历细栅，供求解 **DERIVE**（**ADR-16** 目标态废止 ENT-SS）；规范读路径与 confirm 写回以 **ENT-RCA + SRP** 为准 |
| **备选** | 仅保留 solver RCA + allocation 表（否决：非 Ontology SoT，与 ADR-09 冲突） |
| **后果** | §5.5.1 · ENT-RCA 术语 · `ont_resource_capacity_assignment` · ~~**TODO-22**~~ **已收口 2026-07-01**（R0~R5） |

---

## ADR-16 Shift 级 Period 统一时间桶（废止 ENT-SS 本体地位）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-21） |
| **背景** | 现行 **ENT-PER**（日/周/月）与 **ENT-SS**（日槽+`shiftId`）两套栅格并行；ADR-15 后 ENT-RCA 已挂 **ENT-SRP×Period**，班次拆分若再依赖 ENT-SS 会重复建模 |
| **决策** | **在 Period 定义层支持 Shift 粒度**：近端可配置 `NxMshift`（如 `14x3shift`）；每个 **leaf Period**（含 shift）对应一组 **ENT-SRP / ENT-PISPP**；**ENT-RCA 挂在 leaf SRP 上**；父 Period（日/周）容量与占用 **rollup 自子 Period** |
| **ENT-SS 目标态** | **废止**为本体集合与 SoT；`schedulingSlotsOrdered` / `ont_scheduling_slot` 不再扩展；`SchedulingSlot`/`TimeSlot` 仅 **DERIVE**（**TODO-23 S5 已完成 2026-07-01**） |
| **物料粒度** | v1 默认 **PISPP 仍按日 Period 闭合**（RULE-MRP-05）；shift 级 PISPP 为 **TODO-23 可选**；产能（SRP/RCA）与物料（PISPP）粒度不一致时须显式 RULE |
| **备选** | 保留 ENT-SS 作班次层（否决：与 SRP/RCA 双轨，ADR-15 不一致） |
| **后果** | §5.8.1 · `ontology_period_sequence` 扩展 · ENT-PER 字段 · ~~**TODO-23**~~ **已收口 2026-07-01**（S0~S5）；RULE-MP-07/08 主语已迁 **SRP@shift-Period** |

---

## ADR-17 PhysicalResource 产能聚合（PR → SR · PRP → SRP）

| 项 | 内容 |
|----|------|
| **状态** | 已采纳（2026-06-21） |
| **背景** | 主数据已有 **ENT-SR** / **ENT-PR**（`md_standard_resource` / `md_physical_resource`，RULE-MD-12）；日历在设备/产线生效，但现行 `OntologyLoader` 将 `ResourceCalendar` 直接累加到 **ENT-SRP**，未显式 **ENT-PRP** |
| **决策** | **ENT-SR 1:N ENT-PR**；**ENT-SRP 1:N ENT-PRP**（PhysicalResourcePeriod，与同一 ENT-PER 对齐）；**日历仅在 ENT-PR 上生效**，展开为 **ENT-PRP**；**ENT-SRP 由 PRP 聚合**（DERIVE 或装载时 rollup，非第二套日历算法） |
| **聚合公式** | 见 **RULE-SUP-05**（修订）：`SRP.totalCapacity = Σ PRP.availableCapacityMinutes`（同 SR、同 Period 下全部 PR）；`SRP.reservedCapacity = Σ PRP.reservedCapacity = Σ ENT-RCA`（ADR-15） |
| **计划粒度** | **PROC-S04 主计划** 仍按 **StandardResource** 排产（ENT-OOSR / ENT-RCA）；**PhysicalResource** 仅产能供给与 **S05 细排** 使用，不引入平行 RCA |
| **备选** | 日历直写 SRP、无 PRP（否决：多 PR 并行产能不可见、与主数据 PR 映射脱节） |
| **后果** | §5.8.2 · ENT-PRP 术语 · `ont_physical_resource_period` · **TODO-24**；§11 `md_resource_calendar` 挂 PR |

---

## 假设

| ID | 假设 |
|----|------|
| ASM-01 | 单工厂单日历锚点；无跨厂调拨 |
| ASM-02 | 默认成品库位 DEFAULT-FG |
| ASM-03 | 同工单工序默认串行（并行通过 parallelGroup 扩展） |
| ASM-04 | 销售订单数量单位与 BOM 一致（缩放后须同步 `master_plan_demand_scale`） |

---

## 风险

| ID | 风险 | 缓解 |
|----|------|------|
| RSK-01 | PATH-ENT 退役期回归缺口 | ~~TODO-08 分阶段迁移~~ **已收口 2026-07-01**；`PlanningOptimizerParityTest` 覆盖 PATH-ONT 引擎对等 |
| RSK-02 | 规范滞后代码 | PR 规范联动；§8 测试绑定 |
| RSK-03 | demand_scale 二次缩放 | 文档 + 参数检查清单 |
| RSK-04 | Session 内存压力 | TTL；并发 Session 上限；NFR 堆/集合计数；**不**以裁切并行 SoT 图缓解（ADR-07） |
| RSK-05 | 全量持久化写放大 / 表膨胀 | revision 归档策略；PISPP 按 horizon 裁剪；可选 snapshot 加速读 |
| RSK-06 | legacy 与 `ont_*` 双写期不一致 | ~~TODO-12~~ **已收口 2026-07-01**；对等测试 revision ↔ OG；legacy 写路径逐步退役见 TODO-13/14 |
| RSK-07 | ~~IAM 未落地前 WS id 泄露即越权~~ | **已缓解**：Filter 链 + 成员校验；生产禁 dev-mode · 见 §18 |

---

## 待办（规范层）

| ID | 项 | 负责人 |
|----|-----|--------|
| TODO-01 | 为现有测试补 `@SpecRef(AC-xx)` | 开发 |
| TODO-02 | OpenAPI 从 §6 生成契约骨架 | 开发 |
| TODO-03 | 多 COLD 批次场景 SCN 扩展 | 产品 |
| TODO-04 | ~~直驱默认签字~~ → 并入 TODO-08（ADR-08 废止 PATH-ENT） | PO |
| TODO-05 | 重生成演练：仅凭规范盲重建模块 | 架构 |
| TODO-06 | ~~对齐 ADR-07~~（2026-06-20 已实现） | 开发 |
| TODO-07 | S05/分切求解配置纳入统一配置或插件体系 | 架构+开发 |
| TODO-08 | ~~**PATH-ENT 代码退役（ADR-08）**~~ **已收口 2026-07-01**：Session `optimizeLegacy` / `ontology_direct_solve_enabled` 已删；`MasterPlanService` 统一 `MasterPlanOntologyScheduleBuilder`；AC-05 废止 | — |
| TODO-09 | SCN-02c/03b/04 跳转与试算页 UI 对齐 **[§17.8](../volumes/platform/17-ui-ux.md#178-跨页导航契约ui-nav-)** | 产品+前端 |
| TODO-10 | ~~SCN-01f：`CANCEL_PROMISE`；SCN-01e 与取消承诺解耦~~ **已收口 2026-07-02**（`OrderDemandCancelPromiseService` · `OrderDemandActionOntologyChainTest`） | — |
| TODO-11 | ~~SCN-07 供需平衡专页、PISPP period、MAT-02~08~~ **已收口 2026-07-02**（含 **SCN-07d OPTIMIZE**、多路径 ENT-RT · `routing_path_priority`） | — |
| TODO-12 | ~~**ADR-09 全量 Ontology 持久化**~~ **已收口 2026-07-01**（P0~P5 + Sprint 6A~6D）；loader 内部化文档化 · 后续扩展见 **TODO-24**（PRP）· **TODO-21**（`ont_period` DDL） | — |
| TODO-13 | **ADR-10 External_* 主数据**：staging、质检、sync、md_*、Projector 切读 | 架构+开发 · **M0–M5 已完成 2026-07-03** |
| TODO-14 | **ADR-11 外部交易数据**：external_* / txn_*、Firm WO 同步、质检、OG 装载切读 | 架构+开发 · **T0–T5 已完成 2026-07-03** |
| TODO-15 | **ADR-12 业务知识三层**：KnowledgeContext、Industry pack、overlay 表、引擎接 Effective | 架构+产品 · **K0–K2 已完成 2026-07-03** · K3/K4 待办 |
| TODO-16 | **§15 主计划 KPI 结构化**：`kpiBreakdown` API、求解器域分、B01~B10 面板 | 开发+产品 |
| TODO-17 | ~~**§16 供需知识 UI**~~ **已收口 2026-07-02**：BusinessRules 六 tab + 细排反馈只读页；`delivery-date-strategy` / `supply-quantity-rules` / `resource-efficiency` / `routing-step-*` API | — |
| TODO-18 | ~~ADR-13 IAM~~：**已完成 2026-06** — M0–M4（Filter、JWT、OIDC 联调、Super Admin UI、侧栏 MOD） | 架构+前后端 |
| TODO-19 | **ADR-14 数据集成**：MOD-DI UI、ADP-ERP-SAP/MES/Excel SPI、external 浏览 API | 架构+前后端 |
| TODO-20 | **§5 Ontology 范围扩展**：现行 ENT-OG **仅覆盖订单协同计划**（原主计划 · **MOD-OCP** / PROC-S04）；**MOD-SCH 作业排程**、**MOD-SLT 分切排样** 的领域实体、装载路径与 `ont_*` 表族 **后续完善**（与 TODO-07 求解插件化协同，不阻塞当前 OCP 基线） | 架构+产品 |
| TODO-21 | ~~**§5 领域模型细化**~~ **已收口 2026-07-01**：§5.0/§5.19/§5.20 · `05-ont-schema` P0+V67 `ont_period` · Mapper/Restorer 持久化 | — |
| TODO-22 | ~~**ADR-15 ENT-RCA 本体化**~~ **已收口 2026-07-01**（R0~R5）：ENT-RCA SoT · `ont_resource_capacity_assignment` · `OntologyRcaProjector` | — |
| TODO-23 | ~~**ADR-16 Shift 级 Period**~~ **已收口 2026-07-01**（S0~S5）：shift-Period · leaf SRP/RCA · `PeriodTimeSlotDeriver` · ENT-SS 废止 | — |
| TODO-24 | ~~**ADR-17 PR/PRP 产能聚合**~~ **已收口 2026-06-28**（P0~P5）：ENT-PRP · 日历挂 ENT-PR · SRP=Σ PRP · 细排反馈写 PRP | — |
| TODO-25 | ~~IAM 规范残余~~ **已决策 2026-06**：v1 **首登手动建 WS**（改规范 · §18.3.1 / RULE-IAM-02）；`%prod` `dev-mode=false` 验收 | — |
| TODO-26 | **模块注册表一致性（§19 · AC-IAM-06）**：`workspace-modules.yaml` ↔ `WorkspaceModuleCatalog` 同步/校验（含 API 前缀漂移）；MOD-EXT / ADP-EXT 扩展契约机械化 | 架构+开发 |
| TODO-27 | ~~**SDD 文档债同步（RSK-02）**~~ **已收口 2026-07-01**：AC-17 API 路径 · UI-NAV 深链 `[GAP]` 标注 · `aps-planning-layer` 废止 diagnostics preview · §18 `local-login-enabled` · TODO-22/23 主表关闭 | — |
| TODO-28 | **CI 与 AC 测试基建**：Flyway demo 种子 vs Hibernate `*_SEQ` 基线（`db/test-migration`）；`@QuarkusTest` IAM 配置清单；补 AC-IAM-06 / AC-UI-* 自动化（配合 TODO-01 `@SpecRef`） | 开发 |

### 偏差 → TODO 映射（2026-06-29 审查）

> **来源：** 实现 vs SDD 持续验证 + AC 套件（37/37 已覆盖项通过）。**已有 TODO 不重复开项**；下表仅跟踪缺口与显式子项。

| 偏差 ID | 摘要 | 已有 TODO | 新增/备注 |
|---------|------|-----------|-----------|
| D-08 | ~~默认仍 PATH-ENT / `optimizeLegacy`~~ | **TODO-08** | **已解决 2026-07-01**：S04 统一 PATH-ONT（`MasterPlanOntologyScheduleBuilder`） |
| D-ENT-BOM | PATH-ENT 读 JPA BOM | **TODO-08** | **已解决 2026-07-01**：装载经 `BomDependencyDerivation`（AC-10） |
| D-09 | ~~confirm 写 legacy allocation，无 `ont_*`~~ | ~~TODO-12~~ | **已解决 2026-06-30**：`session-enabled` + `promoteDraftToCommitted`；legacy allocation 并行保留 |
| D-10 | ~~无 `CANCEL_PROMISE`~~ | ~~TODO-10~~ | **已解决 2026-07-02** |
| D-11 | ~~API-MAT-02~08 / SCN-07e~j~~ | ~~TODO-11~~ | **已解决 2026-07-02** |
| D-12 | 无 external/md/txn | ~~TODO-13/14~~ **已落地 2026-07-03**（V73/V74 · M0–M5 · T0–T5） | — |
| D-15 | 无 KnowledgeResolver | ~~TODO-15~~ **K0–K2 已落地 2026-07-03** | K3 引擎全量切读 |
| D-19 | MOD-DI mock，无 staging | **TODO-19** | — |
| D-22 | ~~ENT-RCA 未本体化~~ | ~~TODO-22~~ | **已解决 2026-07-01**（R0~R5） |
| D-23 | ~~Shift-Period / ENT-SS 双轨~~ | ~~TODO-23~~ | **已解决 2026-07-01**（S0~S5） |
| D-24 | ~~无 ENT-PRP / SRP≠Σ PRP~~ | ~~**TODO-24**~~ **已收口 2026-06-28** | — |
| D-CONFIRM | ~~confirm 非 ont revision~~ | ~~TODO-12~~ | **已解决 2026-06-30**：`OntologyConfirmIntegrationTest` · `MasterPlanOntologySessionPersistenceIntegrationTest` |
| D-TRACE | 无 `@SpecRef` | **TODO-01** | TODO-28 补 CI 清单 |
| D-IAM-01 | 注册无自动 PERSONAL WS | ~~TODO-18~~ | **已决策**：v1 手动建 WS · `IamAcTest#acIam01` |
| D-IAM-prod | 默认 dev-mode=true | ~~TODO-18~~ M4 | `%prod` 已 false；生产部署验收 |
| D-IAM-06 | MOD-EXT 未机械化 | — | **TODO-26** |
| D-YAML | YAML 与 Catalog API 前缀不一致 | — | **TODO-26** |
| D-DOC | ~~§8/§17/aps 文档与实现不一致~~ | ~~TODO-27~~ | **已解决 2026-07-01**（本轮同步）；后续 PR 仍须规范联动（RSK-02） |
| D-CI-SEQ | Flyway 种子 id 与 `*_SEQ` 冲突 | — | **TODO-28**（已实现 test-migration，待规范化） |
| D-CI-IAM | QuarkusTest 缺 IAM 配置致套件不可跑 | — | **TODO-28** |
| AC-23 | COLD 计划覆盖标记 | 分散 | 随 TODO-11/OCP 迭代补测（暂不单独 TODO） |
| AC-05 | 迁移期 PATH 对等 | **TODO-08** 后废止 | **已废止 2026-07-01** |

\* v1 IAM：**首登手动建 WS** 为定稿行为（非待收口偏差）。

### TODO-24 分阶段（ADR-17 · PRP → SRP）

| 阶段 | 交付 | 验收 |
|------|------|------|
| **P0 规范** | §5.8.2 · ADR-17 · RULE-SUP-05 修订 · ENT-PRP 术语 | 评审通过（**已完成 2026-06-21**） |
| **P1 本体类型** | `PhysicalResourcePeriod` + `OntologyGraph.prpById`；ENT-PR 主数据投影 | PR 1:N 校验 RULE-MD-12（**已完成 2026-06-28**） |
| **P2 日历装载** | `md_resource_calendar` / `ResourceCalendar` **按 physical_resource_code** → PRP | 单 SR 双 PR：SRP.total = PRP1+PRP2（**已完成 2026-06-28**） |
| **P3 SRP 聚合** | `StandardResourcePeriod` 由 PRP rollup；ROL 可写 PRP 明细（可选） | AC：SRP ≡ Σ PRP（**已完成 2026-06-28**） |
| **P4 持久化** | `ont_physical_resource_period`（TODO-12 扩展） | AC-PERS restore 含 PRP（**已完成 2026-06-28**） |
| **P5 细排反馈** | SchedulerFeedback 占用写入 **PRP**，rollup 至 SRP（RULE-SUP-05） | S05 confirm 后 SRP 可用分钟下降（**已完成 2026-06-28**） |

**依赖：** ADR-16/TODO-23（Period 展开）· TODO-22（RCA→SRP）· §11 日历主数据挂 PR。

### TODO-23 分阶段（ADR-16 · Shift-Period）

| 阶段 | 交付 | 验收 |
|------|------|------|
| **S0 规范** | §5.8.1 · ADR-16 · 序列语法 · ENT-SS 废止说明 | 评审通过（**已完成 2026-06-21**） |
| **S1 Period 模型** | `Period.granularity` · `shiftId` · `parentPeriodId` · `PeriodExpander` + `NxMshift` 语法 | 单元：`14x3shift` → 14 父日 + 42 leaf shift（**已完成 2026-07-01** · `PeriodExpanderTest`） |
| **S2 SRP/RCA** | shift-Period SRP 产能来自日历；ENT-RCA 仅挂 leaf SRP；日 SRP rollup | Σ child RCA = parent reserved（**已完成 2026-07-01** · `StandardResourcePeriodShiftIntegrationTest` · `StandardResourcePeriodRollupTest`） |
| **S3 规则/KPI** | RULE-MP-07/08 · KPI-MP-B05 主语改为 SRP@shift-Period | SCN-03a 产能页一致（**已完成 2026-07-01** · `SrpLoadBucketProjectorTest` · `ResourceCapacityAssignmentValidationTest`） |
| **S4 求解器** | `TimeSlot` 由 leaf Period DERIVE；移除 `OntologyGraph.schedulingSlotsOrdered` 生产路径 | TODO-22 R3 改挂 Period 投影（**已完成 2026-07-01** · `PeriodTimeSlotDeriverTest`） |
| **S5 退役 ENT-SS** | 删除/Deprecated `SchedulingSlot` 集合装载；`ont_scheduling_slot` 不写入 | 无 SS 依赖的 AC 绿（**已完成 2026-07-01** · `PeriodTimeSlotDeriverTest` · `OntologyLoaderSchedulingSlotTest`） |

**依赖：** TODO-22（RCA 本体化）· MOD-CAL 工厂日历 · 与 TODO-12 的 `ont_period` 列扩展联动。

### TODO-22 分阶段（ADR-15 · ENT-RCA）

| 阶段 | 交付 | 验收 |
|------|------|------|
| **R0 规范** | §5.5.1 · ADR-15 · ENT-RCA 术语 · `ont_*` 表名 | 评审通过（**已完成 2026-06-21**） |
| **R1 本体类型** | `ontology.supply.ResourceCapacityAssignment` + `OntologyGraph.resourceCapacityAssignmentsById` | 单元测试：OP×OOSR×SRP 键与守恒（**已完成 2026-07-01** · `ResourceCapacityAssignmentValidationTest`） |
| **R2 写回路径** | `PlanningResultApplicator` / ROL：optimize 写 ENT-RCA → rollup SRP | AC：Σ RCA = OP 总分钟；SRP.reserved 一致（**已完成 2026-07-01** · `ResourceCapacityAssignmentProjectionTest`） |
| **R3 求解投影** | `OntologyRcaProjector`：ENT-RCA ↔ solver RCA/`TimeSlot`（由 **leaf Period DERIVE**，ADR-16） | `OrtoolsResourceCapacityCpSolverTest` 绿（**已完成 2026-07-01** · `OntologyRcaProjectorTest`） |
| **R4 持久化** | `OntologyEntityMapper` / `OntologyRestorer` / `OntologyP0UpsertService` + `ont_resource_capacity_assignment` | AC-PERS：restore 含 RCA（**已完成 2026-07-01** · `OntologyPartialPersistenceH2IntegrationTest`） |
| **R5 退役** | `PlanVersionEntRcaOccupancy` + confirm 写 ENT-RCA SoT；跳过 legacy allocation 反灌 | confirm reload ≡ Session 图（**已完成 2026-07-01** · `MasterPlanOntologyConfirmRcaReloadIntegrationTest`） |

**依赖：** TODO-12（`ont_*`）· TODO-21 Phase 3（列级 DDL）· **TODO-23 S4**（Period→TimeSlot）· 与 TODO-08 无阻塞。

### TODO-12 分阶段（ADR-09）

| 阶段 | 交付 | 验收 |
|------|------|------|
| **P0 Schema** | Flyway：`ont_revision`、HEAD、需求/供应/FF 核心表；**含 `ont_resource_capacity_assignment`（TODO-22 R4）**；列级规范见 [`05-ont-schema.md`](../volumes/data/05-ont-schema.md) | **已完成 2026-06-30** · `V65__ont_p0.sql` · `OntP0SchemaMigrationTest` |
| **P1 Read path** | `OntologyRestorer` + JPA + `OntologyP0Overlay`；场景读经 `WorkspaceAuthoritativeOntologyGraphService` | **已完成** · AC-PERS-01 · `OntologyRestorerIntegrationTest` extended · `AuthoritativeOntologyReadPathIntegrationTest` |
| **P2 Write path** | `OntologySessionPersistenceService` + WAL；H2 dev / postgres / test 默认 `session-enabled=true` | **已完成** · AC-PERS-02 · `MasterPlanOntologySessionDraftRecoveryIntegrationTest` |
| **P3 Confirm** | `promoteDraftToCommitted` + WORKSPACE/`PLAN:{id}` HEAD；与 legacy `OntologyStatePersister` 并行 | **已完成** · AC-PERS-03 |
| **P4 Migration** | H2 `V66`；双写 + overlay + bootstrap；H2 dev 四开关默认开启；MRP → `OntologyLegacyMutationCoordinator` | **已完成** · AC-PERS-04 + Session E2E |
| **P5 Partial policy** | `ont_entity_policy` + DERIVE 装载；`OntologyPartialDeriver` | **已完成** · AC-PERS-05 |

### TODO-13 分阶段（ADR-10）

| 阶段 | 交付 | 验收 | 状态 |
|------|------|------|------|
| **M0 Schema** | Flyway `external_*` + `md_*` + 公共质量列 | 表与 §11 一致 | **已完成** · `V73` |
| **M1 Import** | 只写 external；Excel/API 导入 | AC-MD-01 | **已完成** · `MasterDataExternalImportService` · `POST .../master-data/import/routing` |
| **M2 Quality** | `MasterDataQualityService` + issue_codes | AC-MD-02 | **已完成** · `POST .../master-data/quality/check` |
| **M3 Sync** | 有序 sync → md_* | AC-MD-03 | **已完成** · `MasterDataSyncService` · `POST .../master-data/sync` |
| **M4 Projector** | `MasterPlanRoutingProjector` 读 md_* | AC-MD-04 · RULE-MD-01 | **已完成** · `MdRoutingReadService` |
| **M5 Legacy 退役** | 停止直写 legacy 作计划源 | AC-MD-05 | **已完成** · Projector 仅读 md_* · 停用 `MasterDataLegacyBridge` |

### TODO-14 分阶段（ADR-11）

| 阶段 | 交付 | 验收 | 状态 |
|------|------|------|------|
| **T0 Schema** | Flyway `external_*` 交易表 + `txn_*` + Firm 字段 | 与 §12 一致 | **已完成** · `V74` |
| **T1 Import** | 订单/工单/库存/PO 只写 external | AC-TX-01 | **已完成** · `TransactionalDataExternalImportService` |
| **T2 Quality** | `TransactionalDataQualityService` + TX-Q-* | AC-TX-02 | **已完成** |
| **T3 Sync** | Firm WO → txn_supply_order + PU/OP/OSR 树 | AC-TX-03 · RULE-TX-04 | **已完成** · `TransactionalDataSyncService` |
| **T4 OG Load** | OntologyRestorer 读 txn_* + md_* | AC-TX-04 | **已完成** · `TxnOntologyLoadContributor` |
| **T5 Legacy 退役** | 停止直写 sales_order_line / work_order | AC-TX-05 | **已完成** · 停用 `TransactionalDataLegacyBridge` · OG 读 txn |

---

**回指：** [07-standardization.md](./07-standardization.md) · [08-acceptance.md](./08-acceptance.md)
