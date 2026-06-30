# §0 元信息

| 字段 | 值 |
|------|-----|
| **蓝图 ID** | `BP-PlantOps-v1` |
| **产品名称** | Plant Operation Plan（工厂运营计划系统） |
| **文档类型** | SDD — Specification-Driven Development 规范 |
| **版本** | 1.0.0-SNAPSHOT |
| **架构基线** | Plant Operation Ontology（本体直驱主计划） |
| **状态** | 开发中（合同后精化版） |
| **文档日期** | 2026-06-30 |
| **主笔** | 产品 + 架构 + 业务顾问 |
| **评审闸** | ① 蓝图评审 ② 客户场景确认 ③ 技术契约评审 |

## 架构基线定义

**Plant Operation Ontology** 是本产品的**计划领域本体架构**：用内存中的 **ENT-OG**（`OntologyGraph`）表达供需、工序、满足与期间约束，作为沙盘 Session 内的**运行时**业务真相源；simulate / optimize / confirm 按 **ADR-09** 持久化到 **`ont_*`**（§5.14），legacy JPA 在迁移期仍作装载边界（§5.10 · TODO-12）。

### 包含什么

| 维度 | 内容 | 规范锚点 |
|------|------|----------|
| **聚合根** | ENT-OG：每个 ENT-WS **一张权威图**（ADR-07）；COLD 为视图/scope，非并行真相源 | §2 · §5 · ADR-01 · ADR-07 |
| **前端主粒度** | ENT-COLD（交付批次）为满足链与 ATP/CTP 的根 | §2 · SCN-01a~h · ADR-06 |
| **IAM** | 用户↔WS 成员 · MOD 开关 · VIEW/EDIT · Super Admin · OIDC 联调 | §18 · ADR-13 · **已落地** |
| **需求语义链** | ENT-COL → ENT-COLD → ENT-DEM | §5.1 |
| **供应语义链** | ENT-SO → ENT-PU → ENT-OP → OIM/OOM/OOSR → ENT-SUP | §5.1 |
| **满足与派生** | ENT-FF（PEG-INV / PEG-WO / PEG-SH）；ENT-BD 由 Fulfillment **派生** | §4 · ADR-04 |
| **工艺模板** | ENT-PISP → Routing 族；物化为 Operation 族（API 投影，非 ENT-OG 常驻集合） | §5.1 · SCN-T04 |
| **期间与 MRP** | ENT-PER → ENT-PISPP / ENT-SRP；ROL 传播 simulate 变更 | §4 · SCN-01a/b · SCN-07a |
| **Session 生命周期** | create → simulate（ROL）→ optimize → confirm；TTL ~8h | §3 SCN-T02 · SCN-01d · ADR-01 |
| **Ontology 持久化（P0）** | PostgreSQL `ont_*` Flyway V65（revision/WAL/核心实体）；H2 legacy 仍 V1–V64 | §5.14 · ADR-09 · [05-ont-schema](../volumes/data/05-ont-schema.md) · **P0 已落地 2026-06-30** |
| **主计划路径** | **PATH-ONT** 唯一规范路径（ADR-08 废止 PATH-ENT）；JPA 仅装载/confirm 边界 | §2 · SCN-T01 · SCN-06 · ADR-01 · ADR-08 |
| **求解器边界** | 主计划经 `PlanningOptimizer` + `planning_optimizer_engine`（默认 SOL-ORT）；S05 细排与分切仍直连 SOL-TF，配置化见 §10 TODO-07 | §10 ADR-05 · [ontology-optimizer-plugin.md](../../ontology-optimizer-plugin.md) |
| **Ontology 范围（现行）** | ENT-OG **仅覆盖 MOD-OCP**；**shift-Period / ENT-PRP** 为 **ADR-16/17 规范目标**（代码见 TODO-23/24）；MOD-SCH/SLT 见 TODO-20 | §5 · **TODO-20** · **TODO-23/24** |

### 不包含什么

- **不是**外部 OTD 产品的全模块复刻；§7 Gap 分析仅作对标参考。
- **尚未**将作业排程、分切计划纳入 ENT-OG 规范正文（见 **TODO-20**）；现行细排/分切见 `aps-planning-layer.md` 与独立 JPA。
- **SHIFT 级 Period** 与 **ENT-SS 废止** 见 **ADR-16** · **TODO-23**（规范已采纳，代码未收敛）。
- **不是** JPA 实体即规划模型；已移除实体路径推演诊断 UI（ADR-03）。
- **不含**已定稿的架构演进路线图（待重新制定）；本节只描述**现行基线**能力边界。

### 与实现文档的关系

| 文档 | 用途 |
|------|------|
| [05-domain-model.md](./05-domain-model.md) | **Plant Operation Ontology** 领域模型（§5 唯一正文） |
| [ontology-domain-model.drawio](../../ontology-domain-model.drawio) | Draw.io 图源 |
| [otd-ontology-mapping.md](../../otd-ontology-mapping.md) | 历史映射与装载实现参考（术语以 §2 为准） |
| [aps-planning-layer.md](../../aps-planning-layer.md) | 推演层与 Timefold 分工 |

**下游：** 行为与契约以 §2–§8 为准；技术选型与备选方案以 §10 ADR 为准。

## 读者

| 角色 | 必读章节 |
|------|----------|
| 业务顾问 / 客户 | §1 §2 §3 §7 §8 |
| 产品经理 | §1 §3 §7 §8 §10 |
| 开发人员 / AI | §2 §3 §4 §5 §6 §8 §9 |
| 测试 | §2 §3 §4 §8 |
| 运维 | §6 §9 §10 |

## 变更记录

| 日期 | 版本 | 变更摘要 |
|------|------|----------|
| 2026-06-02 | 0.9 | 初版 product-documentation.html（实体路径为主） |
| 2026-06-10 | 1.0-draft | 按 sdd-spec-authoring 重构；Plant Operation Ontology、COLD 主粒度、移除实体诊断 |
| 2026-06-10 | 1.0 | 历史单体文档归档至 `docs/archive/`；现行 SDD 为 `docs/sdd/` |
| 2026-06-20 | 1.0.0-SNAPSHOT | §3 **七类**业务场景 SCN-01~07 + 平台 SCN-T01~T07；ADR-07/08 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §3 SCN-01e~h：取消计划/承诺、手工建链；API-DEM-01；RULE-FF-03/04 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §4 RULE-PLAN-01、FF-08、MP-06/07/08、MRP-04；产能超载改 soft；BusinessRules 映射 §4.6 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §5 合并 `ontology-domain-model.md` → `05-domain-model.md`（Ontology 领域唯一正文） |
| 2026-06-20 | 1.0.0-SNAPSHOT | ADR-09 全量 `ont_*` 持久化；§5.14–§5.18；RULE-PERS-01~05；TODO-12 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §4 RULE-MRP-05：PISPP 期间供需平衡、缺口量、消缺动机（建 SO） |
| 2026-06-20 | 1.0.0-SNAPSHOT | §4 RULE-FF-09/10：Demand 满足动机、Supply 分配动机；§4.1.1 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §11 External_* → md_* 主数据；ADR-10；RULE-MD-01~06；TODO-13 |
| 2026-06-20 | 1.0.0-SNAPSHOT | RULE-MD-07~13：PISP/RT/RS/RSOSR/SR/PR/RG 主数据结构基本规则 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §12 外部交易 External_* → txn_*；ADR-11；RULE-TX-01~10；Firm WO |
| 2026-06-20 | 1.0.0-SNAPSHOT | §13 业务知识三层 Standard/Industry/Customized；ADR-12 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §19 Workspace 模块 MOD-DI/OCP/SCH/SLT · ADP；ADR-14 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §17 UI/UX · §18 IAM · ADR-13；TODO-18 |
| 2026-06-20 | 1.0.0-SNAPSHOT | §15~§16 KPI 与 Standard 供需知识 |
| 2026-06-20 | 1.0.0-SNAPSHOT | MOD-BRULES 废止；业务规则内嵌 MOD-OCP/MOD-SCH（§19.4.5） |
| 2026-06-20 | 1.0.0-SNAPSHOT | **MOD-MP → MOD-OCP**（订单协同计划）；§6 补 API-INT-* |
| 2026-06-20 | 1.0.0-SNAPSHOT | **PDD.md** 同步 MOD-OCP/MOD-DI 导航、模块内业务规则、PROC-S04 术语 |
| 2026-06-20 | 1.0.0-SNAPSHOT | **三卷整理**：`core/` §0–§10 · `volumes/data|knowledge|platform`；§ 编号不变 |
| 2026-06-21 | 1.0.0-SNAPSHOT | **TODO-20**：明确 ENT-OG 现行范围仅 MOD-OCP；MOD-SCH/MOD-SLT 后续纳入 §5 |
| 2026-06-21 | 1.0.0-SNAPSHOT | **§5.0** 迁移期阅读指引；§5.10 标过渡章节（TODO-21 Phase 0） |
| 2026-06-21 | 1.0.0-SNAPSHOT | **§5.19** 平台 Session 模型（ENT-SES/SBX/PV）；§2/§6 回指（TODO-21 Phase 1） |
| 2026-06-21 | 1.0.0-SNAPSHOT | **ADR-15 / §5.5.1 ENT-RCA**：产能占用纳入 Ontology（OP×OOSR×SRP）；**TODO-22** 及 R0~R5 分阶段 |
| 2026-06-21 | 1.0.0-SNAPSHOT | **ADR-16 / §5.8.1 Shift-Period**：班次纳入 ENT-PER；废止 ENT-SS 本体地位；**TODO-23** S0~S5 |
| 2026-06-21 | 1.0.0-SNAPSHOT | **ADR-17 / §5.8.2 PRP→SRP**：日历挂 PR；SRP=Σ PRP；**TODO-24** P0~P5 |
| 2026-06-21 | 1.0.0-SNAPSHOT | **SDD 一致性修复**：§11 资源日历 · §4 RULE-MP 迁 SRP/RCA · 链接/锚点 · `05-ont-schema` 占位 |
| 2026-06-29 | 1.0.0-SNAPSHOT | **TODO-18 完成**：§18 IAM M0–M4 落地；手动建 WS · dev 不强制首登；OIDC 联调文档 |
| 2026-06-29 | 1.0.0-SNAPSHOT | **实现偏差审查**：§10 增 **TODO-25~28**（IAM 残余 · 模块注册表 · SDD 文档债 · CI/AC 基建）及偏差→TODO 映射表 |
| 2026-06-30 | 1.0.0-SNAPSHOT | **TODO-12 P0**：PostgreSQL profile + Flyway `V65__ont_p0.sql`（11 张 P0 表）；[`05-ont-schema`](../volumes/data/05-ont-schema.md) 列级规范；`OntP0SchemaMigrationTest` |
| 2026-06-30 | 1.0.0-SNAPSHOT | **IAM P1**：AC-IAM-01~05 自动化（`IamAcTest`）+ OIDC live 联调测试 · [iam-p1-runbook](../../iam-p1-runbook.md) |
| 2026-06-30 | 1.0.0-SNAPSHOT | **TODO-12 P1~P3（骨架）**：`OntologyRestorer`/Session WAL/`promoteDraftToCommitted`；`MasterPlanOntologySessionService` 可选 `session-enabled` 写路径；AC-PERS-01/02/03 集成测试 |

## 治理约定

- 规范目录：`docs/sdd/`（`core/` + `volumes/`）；实现变更若影响契约或行为，须同步更新对应章节。
- **基础规则**（Workspace 隔离等）定义于 §4.0，验收 AC-08；不写入 VAL KPI。
- 测试引用：`@SpecRef("AC-xx")` 绑定验收 ID（目标态，逐步补齐）。
- 破坏性 API 变更：须 ADR + 版本说明。

**上游：** [PDD.md](../../PDD.md)  
**下游：** §1–§10 分章
