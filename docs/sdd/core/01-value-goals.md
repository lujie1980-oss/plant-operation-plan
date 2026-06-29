# §1 价值与目标

> 本节定义**业务结果型**价值目标（VAL-01~06）。系统能力与场景（SCN）作为达成路径挂在各 VAL 下；**平台基础规则**（如 Workspace 隔离）见 [§4.0](./04-business-rules.md#40-基础规则平台)，不纳入 VAL KPI。KPI baseline 在客户场景确认闸（§0）落地时实测填入。  
> **场景分类**见 [§3.0](./03-scenarios.md#30-场景分类目录)。

---

## VAL-01 提高按时满足率

**陈述：** 在承诺交期内完成客户**交付批次**的比例持续提升；计划员能提前识别缺口（PEG-SH）并采取补救，减少被动延期。

| KPI | Baseline | Target | 度量方式 |
|-----|----------|--------|----------|
| 按时交付率（OTIF） | — | 较 Baseline ↑（合同签字） | 按期完成 COLD 数 / 应交付 COLD 数 |
| 承诺交期偏差（天）P95 | — | ≤ 合同目标 | confirmedDeliveryDate / promiseDate vs 需求日 |
| 缺口交付占比 | — | ↓ | 根 COLD 链上含 PEG-SH 的开放订单占比 |

**追溯：** SCN-01c~d, SCN-02a, SCN-04a, SCN-06 · ENT-COLD, ENT-FF, PEG-SH · PROC-S01, S04 · API-FC-01

---

## VAL-02 缩短订单响应周期

**陈述：** 从接收销售订单/交期变更到给出**可承诺交期**或确认主计划版本的周期缩短。

| KPI | Baseline | Target | 度量方式 |
|-----|----------|--------|----------|
| 订单到首次可承诺交期 | — | 较 Baseline ↓（合同签字） | 接单 → SCN-01a ATP / SCN-01b CTP 完成 |
| 变更到再确认交期 | — | ≤ 合同目标（如 4h） | 变更 → SCN-01d 确认 |
| PlanningRun 耗时 P95 | — | ≤ NFR-01 目标 | SCN-06 端到端计时 |

**追溯：** SCN-01a~h, SCN-06, SCN-T01~T02 · ENT-SES, ENT-SBX, ENT-PV · API-SES-*, API-DEM-01, API-MP-01

---

## VAL-03 缩短生产周期

**陈述：** 工单从计划投料到计划完工的整体周期缩短，减少工序等待与无效占用。

| KPI | Baseline | Target | 度量方式 |
|-----|----------|--------|----------|
| 制造周期 P95（天） | — | 较 Baseline ↓ | ENT-OP plannedEnd − plannedStart |
| 工序间等待占比 | — | ↓ | 相邻 ENT-OP 间隙 / Makespan |
| 细排 Makespan | — | ↓ | SCN-T05 confirm 后甘特跨度 |

**追溯：** SCN-05a~d, SCN-06, SCN-T04~T05 · ENT-OP, ENT-SO · PROC-S04, S05

---

## VAL-04 提升库存周转

**陈述：** 在保障交付前提下降低成品与关键料库存占用，提高周转效率。

| KPI | Baseline | Target | 度量方式 |
|-----|----------|--------|----------|
| 库存周转天数 | — | 较 Baseline ↓ | 平均库存 / 日出库 |
| 库存满足占比 | — | ↑ PEG-INV 合理占比 | COLD 链 PEG-INV vs PEG-WO |
| 物料缺口 period 数 | — | ↓ | ENT-PISPP 缺口合计 |

**追溯：** SCN-01a, SCN-01c, SCN-04a~c, SCN-07a~j, SCN-06 · ENT-PISPP, PEG-INV, ENT-FF · PROC-S01, S02, S04

---

## VAL-05 提高设备有效产出

**陈述：** 提高瓶颈资源与产线的**有效产出**时间占比，平衡负载、减少长期超载与闲置并存。

| KPI | Baseline | Target | 度量方式 |
|-----|----------|--------|----------|
| 瓶颈资源利用率 | — | 较 Baseline ↑ | ENT-SRP reserved / total |
| 超载 period 占比 | — | ↓ | SRP overload > 0 占比 |
| 产线计划稼动率 | — | ↑ | S05 有效加工 / 日历可用 |

**追溯：** SCN-03a~c, SCN-05a~d, SCN-06, SCN-T05 · ENT-SRP · ENT-RCA · PROC-S03, S04, S05

---

## VAL-06 提高计划制定效率

**陈述：** 计划员以更少步骤完成试算与确认；通过**订单交付过程可视化**前置识别风险；通过**多场景对比**获得可量化的决策依据。

### 能力支柱

| 支柱 | 说明 | 规范锚点 |
|------|------|----------|
| **计划制定效率** | SCN-01a ATP / SCN-01b CTP / SCN-01d 确认 / SCN-01g~h 手工建链；simulate 与 optimize 分层 | SCN-01a~h · ADR-01 |
| **风险可视化** | SCN-02 需求满足分析；SCN-07j 预留预警；DTO-FC 根因（物料/产能/工单） | SCN-02a~b · SCN-07j · DTO-FC |
| **决策可视化** | 多 ENT-PV 并排对比 | TODO-03 / 场景对比 API |

| KPI | Baseline | Target | 度量方式 | 支柱 |
|-----|----------|--------|----------|------|
| 单订单 ATP 评估时间 | — | **P95 ≤ 30s** | SCN-01a 完成 | 计划制定效率 |
| 单订单 CTP 评估时间 | — | **P95 ≤ 120s** | SCN-01b optimize 完成 | 计划制定效率 |
| 订单延期根因可识别率 | — | ↑ | SCN-02b 归因成功率 | 风险可视化 |
| 预留时间偏差可识别率 | — | ↑ | SCN-07j TIME_MISMATCH 覆盖率 | 风险可视化 |
| 满足链可视覆盖率 | — | 100% 开放 COLD | SCN-01c | 风险可视化 |
| 可对比 planVersion 数 | — | ≥2 | 场景对比 API | 决策可视化 |

> **ATP / CTP：** ATP = SCN-01a（库存路径，通常无 optimize）；CTP = SCN-01b（有限能力 optimize）。详见 §3 SCN-01a/b。

**追溯：** SCN-01~04, SCN-06 · ENT-COLD, ENT-OG, ENT-SRP, ENT-OP · RULE-SES-01 · ADR-01, ADR-07

> **主计划求解 KPI：** 评分/约束/业务三层定义见 **[§15 主计划核心 KPI](../volumes/knowledge/15-16-planning-knowledge.md)**（对齐 Company Planner CP v2）；§1 上表为 **业务结果型** 指标，§15 为 **优化器与计划面板** 指标。

---

## 范围

### 在范围（v1）

- 七类业务场景（§3.0）+ 平台场景 **SCN-T01~T07**
- 单工厂 APS：S01–S07
- Plant Operation Ontology、COLD 满足链、Session 沙盘
- 订单协同计划（**MOD-OCP** / PROC-S04）`PlanningOptimizer` 插件；S05/分切配置见 TODO-07
- Workspace 模块（§19）、数据集成（MOD-DI · TODO-19）、**IAM 运行时**（§18 · ADR-13 · **已落地 2026-06**）

### 非范围（v1 实现 · 规范已纳入）

- 多工厂、**生产级** ERP/MES 连接器（规范与 ADP 契约见 §19；实现 TODO-19）
- **自动 PERSONAL WS**（RULE-IAM-02 目标态）— v1 改为登录后 **手动** 创建工作区；dev 用户不强制首登建 WS

### 非范围（产品）

- 实体路径 PATH-ENT（ADR-08 废止）
- 移动端原生应用
- 字段级 ABAC / 行级订单 ACL（v2）

---

**回指：** [00-meta.md](./00-meta.md) · [03-scenarios.md](./03-scenarios.md)
