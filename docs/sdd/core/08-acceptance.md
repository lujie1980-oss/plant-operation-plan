# §8 验收标准

> **原则：** 验收锚定 **场景与规则**，不锚定具体类名/算法实现。  
> **基础规则**（§4.0，如 RULE-WS-01）通过 AC 验收，不纳入 VAL KPI。  
> 测试目标：`@SpecRef("AC-xx")`（逐步补齐）。

---

## AC-01 满足链完整性

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01c · RULE-FF-01 · API-FC-01 |
| **Given** | 黄金 Workspace 含已知 COLD 与 peg 结构 |
| **When** | 调用 API-FC-01 |
| **Then** | DTO-FC 根 id = deliveryId |
| **And** | 库存/工单/缺口三类 peg 总量与 DEM 数量一致 |
| **测试** | `OntologyFulfillmentChainProjectionTest` |

---

## AC-02 simulate 不求解

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01a, SCN-03c, SCN-04c · RULE-SES-01 · API-SES-02 |
| **When** | POST simulate |
| **Then** | 无 Timefold/OR-Tools 求解器调用（可 mock 验证） |
| **And** | simulate 结果含 applied ChangeSet 摘要 |

---

## AC-03 optimize 后 Operation 时间一致

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01b · API-SES-03, API-FC-01 |
| **When** | optimize 成功后刷新 fulfillment-chain |
| **Then** | 链上工单节点计划时间与 API-SES-05 快照一致 |

---

## AC-04 主计划 hard 约束

| 项 | 值 |
|----|-----|
| **追溯** | SCN-06 · RULE-MP-01,03,04,06,08 · API-MP-01 |
| **When** | 流水线 S04 成功完成 |
| **Then** | hardScore = 0（不含产能超载；超载见 RULE-MP-07 soft） |
| **测试** | 流水线集成测试 / 代表性 workspace |

---

## AC-05 直驱对等性（迁移期，TODO-08 后废止）

| 项 | 值 |
|----|-----|
| **追溯** | SCN-T01 · ADR-08 迁移期 |
| **状态** | **过渡** — PATH-ENT 代码移除后本 AC 废止 |
| **When** | 同一 workspace 分别 PATH-ENT 与 PATH-ONT（迁移期对照） |
| **Then** | hard score 相等；allocation 键 Jaccard ≥ 0.95 |
| **测试** | `OntologyDirectSolveParityTest` |

---

## AC-06 confirm 产生 planVersion

| 项 | 值 |
|----|-----|
| **追溯** | SCN-T02 · RULE-SES-02 · API-SES-04 |
| **When** | optimize 后 confirm |
| **Then** | 返回非空 planVersionId |
| **And** | GET master-plan/result/{versionId} 可查询 |

---

## AC-07 confirm 未 optimize 拒绝

| 项 | 值 |
|----|-----|
| **追溯** | SCN-T02-E1 |
| **When** | create 后直接 confirm |
| **Then** | 4xx 错误，无 planVersion 写入 |

---

## AC-08 Workspace 隔离

| 项 | 值 |
|----|-----|
| **追溯** | SCN-T03 · RULE-WS-01 |
| **When** | 错误/其他 workspace header 查询已知 id |
| **Then** | 404 或空，不泄漏 |

---

## AC-09 Routing 投影结构

| 项 | 值 |
|----|-----|
| **追溯** | SCN-T04 · RULE-RT-01,02 · API-RT-01 |
| **When** | 查询有工艺的产品 |
| **Then** | steps 非空；每 step 含 standardResources |
| **And** | 首 step 有 inputMaterials；末 step 有 outputMaterials |
| **测试** | `MasterPlanRoutingProjectorTest` |

---

## AC-10 BomDependency 派生

| 项 | 值 |
|----|-----|
| **追溯** | RULE-FF-02 |
| **When** | 装载含父子工单的图 |
| **Then** | ENT-BD 边与 Fulfillment 追溯一致 |
| **And** | 不直接读取 WorkOrderBomDependencyEntity 作为装载源 |
| **测试** | `BomDependencyDerivationTest` |

---

## AC-11 物料硬约束（PATH-ONT）

| 项 | 值 |
|----|-----|
| **追溯** | RULE-MP-04 |
| **When** | 直驱求解含 BOM 多阶工单 |
| **Then** | 违反闭合库存的分配 hard score > 0 或不可行 |
| **测试** | `ResourceCapacityAssignmentBuilderDbTest`, OR-Tools 约束测试 |

---

## AC-12 已废止验收

| ID | 说明 |
|----|------|
| AC-DIAG-01 | 实体路径 diagnostics preview — **废止**（ADR-03） |

---

## AC-13 单一权威 OntologyGraph

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01b · ADR-07 · RULE-SES-04 |
| **Given** | 同一 ENT-WS 内 ENT-SBX 与 ENT-SES 均已 create |
| **When** | 在 ENT-SBX 对 COLD 执行 simulate 修改 SRP |
| **Then** | 同一 Workspace 的 ENT-SES 可见等价 SRP 变更（共享权威图或等价装载） |
| **And** | `buildDeliveryFulfillmentProjectionGraph` 路径**未**用于 optimize / confirm |
| **测试** | `AuthoritativeOntologyGraphAc13Test` |

---

## AC-14 取消订单计划

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01e · RULE-FF-03 · API-DEM-01 |
| **Given** | COLD 存在本行专属 pegging 与 MRP 工单 |
| **When** | POST `CANCEL_PLAN` |
| **Then** | 专属工单删除；DTO-FC 无 SUPPLY_ORDER 节点 |
| **And** | 共享工单保留时仅解除本行 pegging |
| **测试** | `OrderDemandActionOntologyChainTest` |

---

## AC-15 JIT 手工建链

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01g · RULE-FF-01, FF-04 · API-DEM-01 |
| **Given** | COLD 有工艺路线 |
| **When** | POST `INFINITE_PLAN_JIT` |
| **Then** | DTO-FC 含 SUPPLY_ORDER 节点；JPA 存在 WO-MRP-* 与 pegging |
| **测试** | `OrderDemandActionOntologyChainTest` |

---

## AC-16 取消订单承诺（待实现）

| 项 | 值 |
|----|-----|
| **追溯** | SCN-01f · RULE-FF-03 · API-DEM-01 |
| **Given** | COLD/COL 已写入承诺交期且存在 pegging |
| **When** | POST `CANCEL_PROMISE` |
| **Then** | confirmedDeliveryDate / promiseDate 清空 |
| **And** | pegging 与 ENT-SO **不变** |
| **测试** | 待 TODO-10 |

---

## AC-17 PISPP 供需平衡表

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07a · API-MAT-01 |
| **Given** | 黄金 Workspace 含已知 ENT-PISPP |
| **When** | GET `/material-planning/balance` |
| **Then** | DTO-MBP 含 PISP 行；period/日列与 opening/demand/supply/closing/shortage 可核对 |
| **And** | 合计 shortage 与 ENT-PISPP.stockShortageQuantity 一致 |
| **测试** | `OntologyMaterialBalanceProjector` 单元/集成测试（过渡：日粒度行） |

---

## AC-18 按路径创建供应计划（待实现）

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07b~d · RULE-MRP-01~03 · API-MAT-02, API-MAT-03 |
| **Given** | PISP 有 ≥2 条 ENT-RT 且区间存在缺口 |
| **When** | AUTO / MANUAL / OPTIMIZE 创建 |
| **Then** | 落库 ENT-SO；PISPP plannedSupplyTotal 更新；MANUAL 尊重 routingId；AUTO 选最高优先级路径 |
| **测试** | 待 TODO-11 |

---

## AC-19 区间 Demand 与可匹配 Supply

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07e, SCN-07f · API-MAT-04, API-MAT-05 |
| **Given** | PISP×period 含已知 Demand 与 Supply |
| **When** | GET period-demands → GET eligible-supplies |
| **Then** | unpeggedQty 与 ENT-OG 一致；eligible 列表不含物料不匹配项 |
| **测试** | 待 TODO-11 |

---

## AC-20 拖拽手工预留

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07g · RULE-FF-05 · API-MAT-06 |
| **When** | Demand→Supply 或 Supply→Demand 创建 ENT-FF |
| **Then** | quantity 守恒；超额/错料拒绝；PISPP 刷新 |
| **测试** | 待 TODO-11 |

---

## AC-21 自动预留

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07h, SCN-07i · RULE-FF-06 · API-MAT-07 |
| **When** | anchorType=DEMAND 或 SUPPLY |
| **Then** | 选供/选需符合默认策略；unpeggedQty 下降 |
| **测试** | 待 TODO-11 |

---

## AC-22 预留预警

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07j · RULE-FF-07 · API-MAT-08 |
| **Then** | 未分配 Demand/Supply 与 TIME_MISMATCH 均可查询 |
| **测试** | 待 TODO-11 |

---

## AC-23 COLD 计划覆盖与豁免

| 项 | 值 |
|----|-----|
| **追溯** | SCN-06, SCN-02a · RULE-PLAN-01 · RULE-MRP-04 |
| **Then** | 纳入计划 COLD 均有满足链或明确 MASTER_DATA_GAP / MATERIAL_SHORTAGE_BOUNDED 标记 |
| **And** | 短缺组件 Supply 上界 = needDate − 最长采购提前期（material-lead-time） |
| **测试** | 待补 |

---

## AC-24 预留时间 hard

| 项 | 值 |
|----|-----|
| **追溯** | SCN-07g~h · RULE-FF-08 · API-MAT-06/07 |
| **When** | availableDate > needDate |
| **Then** | 拒绝创建 ENT-FF |
| **测试** | 待 TODO-11 |

---

## AC-25 工序 Routing 顺序

| 项 | 值 |
|----|-----|
| **追溯** | SCN-06, SCN-T05 · RULE-MP-06 |
| **Then** | 同 SO 内 OP 按 RS sequenceNo 单调可行 |
| **测试** | 主计划/细排约束测试 |

---

## AC-26 并行工序同区间

| 项 | 值 |
|----|-----|
| **追溯** | SCN-06, SCN-T05 · RULE-MP-08 · BusinessRules parallel-operations |
| **Then** | 配对 OP 同槽位/同起止；违反则 hard > 0 |
| **测试** | `DetailScheduleConstraintProvider` / 并行绑定测试 |

---

## AC-27 产能超载 soft

| 项 | 值 |
|----|-----|
| **追溯** | SCN-03a, SCN-06 · RULE-MP-02,07 |
| **When** | 某 **leaf ENT-SRP** 上 `Σ ENT-RCA.assignedMinutes > availableCapacity`（或 legacy DERIVE 槽位等价条件） |
| **Then** | hardScore = 0 仍可接受；soft / CapacityOverloadCost 升高（RULE-MP-07 · ADR-15） |
| **测试** | `OrtoolsResourceCapacityCpSolverTest` |

---

## 验收矩阵（摘要）

| AC | SCN | RULE | 自动化测试 |
|----|-----|------|------------|
| AC-01 | SCN-01c | FF-01 | OntologyFulfillmentChainProjectionTest |
| AC-05 | SCN-T01（迁移期） | MP-* | OntologyDirectSolveParityTest |
| AC-09 | SCN-T04 | RT-* | MasterPlanRoutingProjectorTest |
| AC-10 | — | FF-02 | BomDependencyDerivationTest |
| AC-13 | SCN-01b | SES-04 | AuthoritativeOntologyGraphAc13Test |
| AC-14 | SCN-01e | FF-03, DEM-01 | `OrderDemandActionOntologyChainTest#cancelPlanRemovesExclusiveWorkOrdersAndReturnsScopedChain` |
| AC-15 | SCN-01g | FF-01, FF-04, DEM-01 | `OrderDemandActionOntologyChainTest#buildUpstreamChainReturnsOntologyProjectedFulfillmentChain` |
| AC-16 | SCN-01f | FF-03, DEM-01 | 待实现（TODO-10） |
| AC-17 | SCN-07a | MAT-01 | OntologyMaterialBalanceProjector 测试 |
| AC-18 | SCN-07b~d | MRP-01~03, MAT-02/03 | 待 TODO-11 |
| AC-19 | SCN-07e~f | MAT-04, MAT-05 | 待 TODO-11 |
| AC-20 | SCN-07g | FF-05, MAT-06 | 待 TODO-11 |
| AC-21 | SCN-07h~i | FF-06, MAT-07 | 待 TODO-11 |
| AC-22 | SCN-07j | FF-07, MAT-08 | 待 TODO-11 |
| AC-23 | SCN-06, SCN-02a | PLAN-01, MRP-04 | 待补 |
| AC-24 | SCN-07g~h | FF-08 | 待 TODO-11 |
| AC-25 | SCN-06, SCN-T05 | MP-06 | 约束测试 |
| AC-26 | SCN-06, SCN-T05 | MP-08 | 并行约束测试 |
| AC-27 | SCN-03a, SCN-06 | MP-02,07 | OrtoolsResourceCapacityCpSolverTest |

---

## AC-PERS：Ontology 全量持久化（ADR-09 · TODO-12）

| ID | 陈述 | RULE | 自动化 |
|----|------|------|--------|
| AC-PERS-S0 | PostgreSQL Flyway `V65__ont_p0.sql` 迁移成功；P0 表（revision/WAL/session + 7 张核心实体）与索引存在 | — | `OntP0SchemaMigrationTest`（需 PG :5432） |
| AC-PERS-01 | 给定 COMMITTED `revision_id`，`OntologyRestorer.load` 与迁移前 `OntologyLoader.build` 在 FF/SO/Operation/PISPP 关键字段 **对等** | PERS-01 | 待 P1 |
| AC-PERS-02 | simulate 后 **模拟进程 kill**，重启 load 同一 `session_id` DRAFT revision，图状态与 kill 前最后一次 **成功 API** 一致 | PERS-04 | 待 P2 |
| AC-PERS-03 | confirm 成功后 `ont_revision_head(WORKSPACE)` 指向新 revision；`plan_version_id` 可追溯到 allocation 等价数据 | PERS-03 | 待 P3 |
| AC-PERS-04 | legacy 双写期：`ont_supply_order` 与 `work_order` 在 confirm 后 1:1 对齐 | PERS-02 | 待 P4 |
| AC-PERS-05 | Partial policy 下 DERIVE 实体不落库，reload 后与 FULL 重算结果一致 | PERS-05 | 待 P5 |

**P0 联调：** [ont-postgres-dev.md](../../ont-postgres-dev.md) · `tools/start-postgres-dev.ps1`

---

## AC-MD：外部主数据（ADR-10 · TODO-13）

| ID | 陈述 | RULE |
|----|------|------|
| AC-MD-01 | 导入只写 `external_*`；`quality_status=PENDING` | MD-02 |
| AC-MD-02 | 缺 FK / 重复 key 行 `FAILED` + `quality_issue_codes` 非空 | MD-04 |
| AC-MD-03 | `FAILED`/`is_blocked` 行 sync 后 `md_*` 无对应行 | MD-03 |
| AC-MD-04 | sync 后 `MasterPlanRoutingProjector` 与 md 一致；不读 external | MD-01 |
| AC-MD-05 | legacy 直写计划源路径关闭后，主计划仍可通过 md 运行 | MD-01 |
| AC-MD-06 | 违反 RULE-MD-07~13 的 batch 质检 FAILED，且 `quality_issue_codes` 含对应 MD-Q-* | MD-07~13 |

---

## AC-TX：外部交易数据（ADR-11 · TODO-14）

| ID | 陈述 | RULE |
|----|------|------|
| AC-TX-01 | 交易导入只写 `external_*`；初始 `quality_status=PENDING` | TX-02 |
| AC-TX-02 | Firm WO 缺 OSR / 重复 operation_seq → FAILED + TX-Q-* | TX-07~09 |
| AC-TX-03 | sync 后 `txn_supply_order.firm_status=FIRM`；含 PU/OP/OSR 树 | TX-04 |
| AC-TX-04 | OG 装载自 `txn_*`+`md_*`；不读 external 交易表 | TX-01 |
| AC-TX-05 | COLD sync 后 1:1 `txn_demand` CUSTOMER_DELIVERY | TX-06 |

---

## AC-UI：界面验收（§17）

> 正文与组件映射见 [§17.13](../volumes/platform/17-ui-ux.md#1713-ui-验收ac-ui)。

| ID | 陈述 | 追溯 |
|----|------|------|
| AC-UI-01 | 需求满足页选中 COLD 后 3s 内展示 DTO-FC 或 loading | SCN-01c · UI-COMP-04 |
| AC-UI-02 | Workspace 切换后列表无上一 WS 数据残留 | SCN-T03 · RULE-WS-01 |
| AC-UI-03 | 产能页 SRP 超载 period 可识别 | SCN-03a · RULE-MP-07 |
| AC-UI-04 | PlanningSignalBadge 覆盖主数据缺口/WARNING | ADR-03 · RULE-MD-06 |
| AC-UI-05 | 旧 URL redirect 可达 canonical 页 | §17.2.2 |
| AC-UI-06 | SCN-02c 深链跳转后目标页筛选生效 | §17.8 · TODO-09 |

---

## AC-IAM：用户与权限（§18 · ADR-13）

| ID | 陈述 | RULE / SCN | 自动化 |
|----|------|------------|--------|
| AC-IAM-01 | 新用户首登后 **无** 自动 WS；手动创建后 `workspace_member` 含 OWNER 且 `ownerUserId` 正确 | RULE-IAM-02 · SCN-T06a · §18.3.1 | `IamAcTest#acIam01_*` |
| AC-IAM-02 | 非成员 X-Workspace-Id → 403，无业务体 | RULE-IAM-01 · SCN-T06-E1 | `IamAcTest#acIam02_*` |
| AC-IAM-03 | 关闭 MOD-SLT 后侧栏无分切入口且 slitting API 403 | RULE-IAM-03 | `IamAcTest#acIam03_*` · 侧栏手工 |
| AC-IAM-04 | MOD-OCP=VIEW 用户 optimize → 403 MODULE_FORBIDDEN | RULE-IAM-04 · SCN-T06b | `IamAcTest#acIam04_*` |
| AC-IAM-05 | SUPER_ADMIN 可管理任意用户；操作有 audit | RULE-IAM-05 · SCN-T06c | `IamAcTest#acIam05_*` |
| AC-IAM-06 | 新增计划能力未注册 MOD-* 不得单独上线模块开关 | RULE-IAM-06 | — |

**P1 联调：** [iam-p1-runbook.md](../../iam-p1-runbook.md) · OIDC 可选 `OidcLiveIntegrationTest`（Keycloak :8081）

---

## AC-INT：数据集成（§19 · MOD-DI · §6 API-INT-*）

| ID | 陈述 | SCN · API |
|----|------|-----------|
| AC-INT-01 | Excel 导入仅写 external_*，初始 PENDING | SCN-T07a · RULE-MD-02 · **API-INT-07** |
| AC-INT-02 | ERP SAP adapter 写入 source_system=ERP_SAP | SCN-T07b · **API-INT-05** |
| AC-INT-03 | 计划路径不读 external_* 直驱 | RULE-MD-01 · SCN-T07b · **API-INT-03** |
| AC-INT-04 | 关闭 MOD-DI 后 /integration 403 | RULE-IAM-03 |

---

**回指：** [03-scenarios.md](./03-scenarios.md) · [04-business-rules.md](./04-business-rules.md) · [06-api-contracts.md](./06-api-contracts.md#api-int-01-导入批次列表) · [17-ui-ux.md](../volumes/platform/17-ui-ux.md) · [18-identity-access-management.md](../volumes/platform/18-19-workspace-platform.md) · [19-workspace-modules-and-adapters.md](../volumes/platform/18-19-workspace-platform.md)
