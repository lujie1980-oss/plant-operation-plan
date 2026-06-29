# §3 角色与场景（Given-When-Then）

> 每个 **SCN** 挂在 **VAL** 或 **§4.0 基础规则** 下。`Given`/`When`/`Then` 使用 §2 术语。  
> **UI 行为：** 页面、组件、导航契约见 **[§17 UI/UX 规范](../volumes/platform/17-ui-ux.md)**。  
> **编号规则：** `SCN-{分类序号}{子字母}` = 业务场景；`SCN-T{序号}` = 平台/技术场景。

---

## §3.0 场景分类目录

| 分类 | ID 段 | 说明 | 主要 VAL |
|------|--------|------|----------|
| **1. 新订单接收** | SCN-01a~h | ATP / CTP / 满足链 / 确认交期 / 取消计划·承诺 / 手工建链 | VAL-01, VAL-02, VAL-06 |
| **2. 需求满足分析** | SCN-02a~c | OTIF 预警、根因、跳转产能/物料 | VAL-01, VAL-06 |
| **3. 产能平衡分析** | SCN-03a~c | 瓶颈 KPI、工单钻取、能力试算 | VAL-05, VAL-06 |
| **4. 物料满足分析** | SCN-04a~c | 短缺识别、供应计划、供应试算 | VAL-04, VAL-01 |
| **5. 工单管理** | SCN-05a~d | 计划一览、Firm 预警、下发排程 | VAL-03, VAL-05 |
| **6. 主计划自动制定** | SCN-06 | PlanningRun / S04 产出 ENT-PV（**模块 UI：MOD-OCP 订单协同计划**） | VAL-01~05 |
| **7. 供需平衡** | SCN-07a~j | PISPP 表、建供应、物料预留与预警 | VAL-04, VAL-01, VAL-06 |
| **平台与技术** | SCN-T01~T07 | PATH-ONT、Session、隔离、工艺、细排、IAM、**数据集成** | ADR/RULE |

### 旧 ID 对照（迁移期）

| 旧 ID | 新 ID |
|-------|--------|
| SCN-01 查看满足链 | **SCN-01c** |
| SCN-02 交期试算 | **SCN-01a**（ATP）+ **SCN-01b**（CTP） |
| SCN-03 制定主计划 | **SCN-06** |
| SCN-04 PATH-ONT | **SCN-T01** |
| SCN-05 Session confirm | **SCN-T02**（与 SCN-01d 业务重叠） |
| SCN-06 Workspace | **SCN-T03** |
| SCN-07 工艺主数据 | **SCN-T04** |
| SCN-08 详细排程 | **SCN-T05** |
| SCN-09 IAM | **SCN-T06** |
| SCN-10 数据集成 | **SCN-T07** |

---

## 1. 新订单接收

**角色：** 计划员 · **标签：** `[STD][COMMON]`

### SCN-01a ATP（库存可承诺交期）

**价值：** VAL-02, VAL-06（计划制定效率）

**说明：** 根据订单需求，沿工艺路径查找可分配库存（PEG-INV），计算**最早可满足时间**（基于库存可用时间）。**不含**有限能力 optimize（RULE-SES-01）。

```gherkin
Given 新销售订单行已生成 ENT-COLD
  And 产品工艺路径（ENT-RT/ENT-RS）与库存（PEG-INV）可装载
When 计划员发起 ATP 评估
Then 系统沿工艺路径识别可 peg 库存
  And 返回最早可满足日期（ATP date）
  And P95 端到端 ≤ 30s（NFR-01 / VAL-06）
  And 未调用 SOL-TF / SOL-ORT
```

---

### SCN-01b CTP（有限能力建议交期）

**价值：** VAL-01, VAL-02, VAL-06

**说明：** 在同一权威 ENT-OG 上对单 COLD 做 scoped optimize，给出**建议交期**（CTP）。

```gherkin
Given 已选中 ENT-COLD
  And ENT-WS 权威 ENT-OG 已装载（ADR-07）
  And ENT-SBX 或 ENT-SES 引用同一张图
When 计划员对该 COLD 执行 scoped optimize（CTP）
Then 求解器在同一 ENT-OG 上分配 **ENT-RCA**（挂 **leaf ENT-SRP** · ADR-15；迁移期经 DERIVE **TimeSlot** · ADR-16）
  And DTO-FC / Operation 回写建议 promiseDate
  And CTP P95 端到端 ≤ 120s（NFR-01 / VAL-06）
```

**异常 SCN-01b-E1 无 SupplyOrder**

```gherkin
Given COLD 满足链尚无 ENT-SO（未 JIT 展开）
When 发起 CTP optimize
Then 拒绝或提示先执行无限能力计划（JIT）
```

**异常 SCN-01b-E2 Session 过期**

```gherkin
Given ENT-SES / ENT-SBX 已超过 TTL
When simulate 或 optimize
Then 返回 Session 过期并提示重新 create
```

---

### SCN-01c 查看交付批次满足链

**价值：** VAL-01, VAL-04, VAL-06（风险可视化）

```gherkin
Given Workspace 存在 ENT-COLD 且已关联 ENT-DEM / ENT-FF
When GET /api/v1/ontology/fulfillment/deliveries/{deliveryId}/fulfillment-chain
Then 响应 DTO-FC，根节点 = deliveryId
  And 链上 PEG-INV → PEG-WO → PEG-SH 可辨认
  And ENT-SO.id = workOrderNo
```

**异常 SCN-01c-E1 交付不存在**

```gherkin
Given deliveryId 不存在于当前 ENT-WS
When 请求 fulfillment-chain
Then HTTP 404；不得泄漏其他 Workspace 数据
```

---

### SCN-01d 确认交期

**价值：** VAL-01, VAL-02

**说明：** 弹出确认窗口；**默认**采用 CTP 建议交期；计划员可手工调整；确认后更新 COLD 的 **confirmedDeliveryDate**（及关联 COL 承诺字段）。

**动作：** `CONFIRM_PROMISE_DATE` · API-DEM-01

```gherkin
Given 已完成 SCN-01b CTP 或 SCN-01h 有限能力计划
  And 系统展示建议交期（默认 = CTP promiseDate）
When 计划员在确认窗口接受或调整交期并确认
Then 持久化 confirmedDeliveryDate 至 ENT-COLD（及销售订单行 promiseDate）
  And DTO-FC / 需求池列表刷新承诺状态
  And 不写 ENT-PV（仅更新承诺字段）
```

**异常 SCN-01d-E1 未试算即确认**

```gherkin
Given 无有效 CTP / ATP 结果
When 强制确认交期
Then 警告或拒绝（按策略配置）
```

---

### SCN-01e 取消订单计划

**价值：** VAL-02

**说明：** 撤销本 COLD/订单行因建链或 MRP 产生的**计划工单与 pegging**；共享或已下发工单仅解除本行 pegging 并保留。与 **SCN-01f**（仅清承诺）分离。

**动作：** `CANCEL_PLAN` · API-DEM-01

```gherkin
Given ENT-COLD 已存在 ENT-FF / ENT-SO 或 JPA pegging
When 计划员执行「取消订单计划」
Then 移除本订单行专属可重建工单的 pegging 并删除专属 ENT-SO（JPA: WO-MRP-*）
  And 共享或已下发工单保留，仅解除本行 pegging（RULE-FF-03）
  And 失效本交付 Sandbox / 权威 ENT-OG 缓存并刷新 DTO-FC
  And 满足链可回到「仅需求、无上游 SO」或库存 peg 状态
  And 不得删除 ENT-COLD / ENT-DEM 本身
```

**异常 SCN-01e-E1 无计划可取消**

```gherkin
Given 本订单行无 pegging 且无专属工单
When 执行 CANCEL_PLAN
Then 返回提示「无计划工单，无需取消」；HTTP 200
```

---

### SCN-01f 取消订单承诺

**价值：** VAL-01, VAL-02

**说明：** 清除已确认/已写入的**承诺交期**，不改变计划工单与满足链结构。与 SCN-01e 独立：可仅取消承诺而保留计划，或先取消承诺再取消计划。

**动作：** `CANCEL_PROMISE` · API-DEM-01

```gherkin
Given ENT-COLD 或 ENT-COL 已写入 confirmedDeliveryDate 和/或 promiseDate
When 计划员执行「取消订单承诺」
Then 清空 COLD.confirmedDeliveryDate 与 COL.promiseDate（及前端承诺状态）
  And ENT-FF / ENT-SO / pegging **保持不变**
  And DTO-FC / 需求池列表刷新为「未承诺」状态
  And 不触发 Session confirm、不写 ENT-PV
```

**异常 SCN-01f-E1 本无承诺**

```gherkin
Given confirmedDeliveryDate 与 promiseDate 均为空
When 执行 CANCEL_PROMISE
Then 返回提示「当前无承诺交期」；HTTP 200
```

> **实现注记：** 现行 `CANCEL_PLAN` 实现会一并清空 `promiseDate`；规范要求与 SCN-01f 解耦，见 §10 TODO-10。

---

### SCN-01g 手工创建满足链（无限能力 / JIT）

**价值：** VAL-02, VAL-04, VAL-06

**说明：** 计划员手工触发 **无限能力 JIT 建链**：沿 BOM 倒排创建/挂接上游 ENT-SO，库存优先 peg，缺口生成 MRP 工单并**实时落库**（非 Session confirm）。为 SCN-01b CTP 之前置步骤。

**动作：** `INFINITE_PLAN_JIT`（别名 `BUILD_UPSTREAM_CHAIN`）· API-DEM-01

```gherkin
Given ENT-COLD 已存在且工艺路线（ENT-RT/ENT-RS）可装载
  And 尚无 ENT-SO 或需重建上游链
When 计划员执行「无限能力计划（JIT）」/ 手工建链
Then OntologyUpstreamFulfillmentBuilder 沿 Demand 递归展开
  And PEG-INV → PEG-WO → PEG-SH 按 RULE-FF-01 挂接
  And 新建专属 ENT-SO 写入 JPA（WO-MRP-*）及 pegging / BOM 依赖
  And 工序时间窗按 needDate JIT 倒排（无限产能，不调用 optimize）
  And 返回 DTO-FC 含 SUPPLY_ORDER 节点
  And 重建前可先 SCN-01e 清理旧专属工单
```

**异常 SCN-01g-E1 无工艺路线**

```gherkin
Given 产品无 ENT-RS
When 执行 INFINITE_PLAN_JIT
Then 拒绝并提示维护工艺主数据（SCN-T04）
```

---

### SCN-01h 手工创建满足链（有限能力）

**价值：** VAL-01, VAL-02, VAL-06

**说明：** 在权威 ENT-OG 上对单 COLD 做 **scoped optimize**（等同 SCN-01b CTP），刷新满足链预览与建议交期；不改动其他 COLD 已排结果（单交付 Sandbox）。

**动作：** `FINITE_PLAN`（别名 `PLAN_FINITE`）· API-DEM-01

```gherkin
Given 已完成 SCN-01g 或 ENT-COLD 满足链已有 ENT-SO
  And ENT-WS 权威 ENT-OG 已装载（ADR-07）
When 计划员执行「有限能力计划」/ 手工建链（有限能力）
Then 单交付 Sandbox scoped optimize（strategy = finite-capacity）
  And DTO-FC 回写 Operation 时间与 promiseDate 建议
  And CTP P95 端到端 ≤ 120s（NFR-01）
  And 未 confirm 前不写 ENT-PV
```

**异常 SCN-01h-E1 无 SupplyOrder**

```gherkin
Given COLD 满足链尚无 ENT-SO
When 执行 FINITE_PLAN
Then 拒绝或提示先执行 SCN-01g（JIT 建链）
```

---

## 2. 需求满足分析

**角色：** 计划员 · **标签：** `[STD][COMMON]`

### SCN-02a 按时满足率与预警

**价值：** VAL-01, VAL-06（风险可视化）

```gherkin
Given 需求池含多个开放 ENT-COLD
When 计划员打开需求满足分析总览
Then 展示 OTIF / 按时满足率 KPI
  And 预警未按时满足或 AT_RISK 的 COLD 列表
  And 每条预警含摘要原因（物料 / 产能 / 交期）
```

---

### SCN-02b 满足链根因与工序/齐套联动

**价值：** VAL-01, VAL-06

```gherkin
Given 选中延期或 AT_RISK 的 ENT-COLD
When 查看 DTO-FC 满足链
Then 可定位延期环节（PEG-SH、SRP 超载、ENT-OP 越界）
  And 可联动工序甘特与工序物料预齐套视图
  And 系统提示不满足原因（关键机台产能 / 关键物料短缺）
```

---

### SCN-02c 从根因跳转产能或物料计划

**价值：** VAL-04, VAL-05, VAL-06

```gherkin
Given SCN-02b 已识别根因资源或物料
When 计划员点击「查看产能计划」或「查看物料计划」
Then 跳转对应页面并自动筛选定位机台或物料
  And 产能平衡页展示该机台分配工单及优先级差异
  And 物料计划页展示供应计划与分配计划
```

> **实现状态：** 跳转与自动筛选为 **v1 目标**；规范见 **§17.8 UI-NAV-01~03**（TODO-09）。

---

## 3. 产能平衡分析

**角色：** 计划员 · **标签：** `[STD][COMMON]`

### SCN-03a 瓶颈工序 KPI 识别

**价值：** VAL-05

```gherkin
Given 当前 ENT-PV 或 ENT-OG 已装载 SRP 数据
When 打开产能平衡 KPI 面板
Then 识别瓶颈工序 / 资源（如 ENT-SRP overload 最高）
  And 展示超载 period 占比等指标（KPI-MP-B04~B05）
```

---

### SCN-03b 瓶颈工单钻取

**价值：** VAL-05, VAL-03

```gherkin
Given SCN-03a 已定位瓶颈资源
When 在产能平衡页查看该机台分配工单
  And 点击某工单
Then 跳转工单管理并自动筛选该 ENT-SO
  And 展示工单计划信息与优先级
```

---

### SCN-03c 可用能力上限试算

**价值：** VAL-05, VAL-06

```gherkin
Given 产能平衡甘特已展示某区间负载
When 计划员手工调整某区间可用能力上限（simulate SRP / 日历）
Then ROL 或重算更新产能平衡甘特
  And 关联 COLD / ENT-OP 时间推演可见（与 SCN-02b 联动）
  And 未 confirm 前不写 ENT-PV
```

---

## 4. 物料满足分析

**角色：** 计划员 · **标签：** `[STD][COMMON]`

### SCN-04a 短缺物料与延期影响

**价值：** VAL-04, VAL-01

```gherkin
Given ENT-PISPP 已反映 MRP 缺口
When 打开物料满足分析总览
Then 列出短缺物料及短缺程度
  And 关联导致最大需求延期的 COLD / ENT-SO（按影响排序）
```

---

### SCN-04b 瓶颈物料供应与分配计划

**价值：** VAL-04

```gherkin
Given 选中瓶颈物料（SCN-04a）
When 打开物料计划页或跳转 SCN-07a 供需平衡页
Then 展示该物料供应计划与分配计划（PEG / PISPP period）
```

---

### SCN-04c 供应时间/供应量试算

**价值：** VAL-04, VAL-06

```gherkin
Given 选中瓶颈物料
When 计划员调整供应时间或供应量并 simulate
Then 系统推演受影响 ENT-SO 与 ENT-COLD 时间
  And 满足链 DTO-FC 刷新用于评估应对措施
  And 未调用主计划 optimize（RULE-SES-01）除非用户显式 CTP
```

---

## 5. 工单管理

**角色：** 计划员 / 排程员 · **标签：** `[STD][COMMON]`

### SCN-05a 工单计划一览

**价值：** VAL-03

```gherkin
Given 存在 ENT-SO 与主计划分配
When 打开工单管理列表
Then 展示计划开始/结束、资源、优先级
  And 高亮未来数日内需投产的工单
```

---

### SCN-05b Firm 工单风险预警

**价值：** VAL-01, VAL-03

```gherkin
Given 存在 FirmWorkOrder（或等价锁定工单）
When 系统扫描计划状态
Then 预警：未排产、未满足、或无下游满足链
  And 每条预警含可读原因
```

---

### SCN-05c 按展望期自动下发排程

**价值：** VAL-03

```gherkin
Given 配置固定展望期（如 7d）
  And 工单满足下发条件
When 定时或手动触发自动下发
Then 符合条件的 ENT-SO 进入 S05 排程队列
```

---

### SCN-05d 手工选定工单下发排程

**价值：** VAL-03

```gherkin
Given 计划员在工单列表多选 ENT-SO
When 执行「下发到排程」
Then 选中工单进入 S05 待排状态
  And 可在 SCN-T05 细排 Session 中继续
```

---

## 6. 主计划自动制定（PlanningRun）

### SCN-06 主计划自动制定

**价值：** VAL-01, VAL-02, VAL-03, VAL-04, VAL-05  
**角色：** 计划员 · **标签：** `[STD][COMMON]`

**说明：** 全厂 **PROC-S04** 自动制定主计划，产出 ENT-PV。入口：`PlanningRun` / `POST .../planning/run-full-pipeline` / API-MP-02。  
**KPI：** 求解 Total/评分/约束见 **[§15](../volumes/knowledge/15-16-planning-knowledge.md)**；结果页展示 **KPI-MP-B01~B10**。

```gherkin
Given Workspace 存在开放需求与可排程 ENT-SO
  And 已配置主计划策略
  And PATH-ONT 求解（ADR-08）
When 触发 PlanningRun / 制定主计划
Then 完成 S04 选优并产生 ENT-PV
  And hard score = 0（成功时）
  And 结果供 SCN-02~07 分析页消费
```

**异常 SCN-06-E1 无工艺路线**

```gherkin
Given ENT-SO 无 ENT-RS
When 执行 S04
Then 跳过或标记不可排；流程可继续；原因可识别
```

**异常 SCN-06-E2 产能不足**

```gherkin
Given FINITE_CAPACITY 且 ENT-SRP 不足
When S04 optimize
Then 仍产出 planVersion（软约束允许延期）；hard = 0；延期工单可识别
```

---

## 7. 供需平衡

**角色：** 计划员 · **标签：** `[STD][COMMON]`  
**页面：** **供需平衡**（PISPP；路由 `/master-plan/analysis/supply-demand-balance`，与物料计划页区分）

> 与 **SCN-04 物料满足分析** 互补：SCN-04 侧重短缺 KPI 与试算；SCN-07 以 **ENT-PISPP 期间桶** 为主视图，支持按工艺路径创建供应计划（SCN-07b~d）与 **物料预留**（SCN-07e~j：Demand↔Supply 挂接 ENT-FF）。

### SCN-07a PISPP 供需平衡二维表

**价值：** VAL-04, VAL-01

**说明：** 新增供需平衡页；对每个 **ENT-PISP** 展示其 **ENT-PISPP** 期间明细。列 = ENT-PER；行 = PISPP 度量（期初 onHand、计划需求、计划供应、期末库存、缺口等）。

```gherkin
Given 权威 ENT-OG 已装载 PISPP（或指定 masterPlanVersionId）
When 计划员打开供需平衡页
Then GET API-MAT-01 返回 DTO-MBP（MaterialRequirementReportDto）
  And 选中某 PISP 时展开二维表：列 = period / 日期；行 = opening / demand / supply / closing / shortage
  And 单元格数值与 ENT-PISPP 字段一致（含 plannedSupplyTotalMrp / plannedSupplyTotalOptimized 对照时可切换视图）
  And 缺口 period 高亮；可下钻至 SCN-07e 区间 Demand 或 SCN-07b~d 创建供应
```

**异常 SCN-07a-E1 无期间序列**

```gherkin
Given ontology_period_sequence 未配置或 ENT-PER 为空
When 打开供需平衡页
Then 提示配置期间序列；表格为空或仅展示 horizon 占位
```

---

### SCN-07b 自动创建供应计划（最高优先级路径）

**价值：** VAL-04, VAL-02

**说明：** 针对某 **ENT-PISP** 在指定 **ENT-PER 区间**，系统自动选取该 PISP 下 **pathPriority 最高** 的 ENT-RT，按区间缺口数量创建 ENT-SO（MRP 工单）并写入 PISPP 计划供应。

**动作：** `POST .../supply-plans` · `mode=AUTO` · API-MAT-03

```gherkin
Given SCN-07a 已选中 PISP 与 period 区间
  And 该 PISP 存在 ≥1 条 ENT-RT（每条含 ENT-RS 序列）
When 计划员执行「自动创建供应计划」
Then 系统选择 pathPriority 最小的 ENT-RT（数值越小优先级越高，RULE-MRP-01）
  And 按区间 net 缺口量（stockShortageQuantity 或等价净需求）创建 ENT-SO
  And JIT 展开工序并落库 pegging（RULE-FF-04）
  And 刷新该 PISP 对应 PISPP 行的 plannedSupplyTotal / closing / shortage
  And 返回新建 ENT-SO 列表摘要
```

**异常 SCN-07b-E1 无可用路径**

```gherkin
Given PISP 无 ENT-RT 或 RS 不完整
When 自动创建
Then 拒绝并提示维护工艺主数据（SCN-T04）
```

**异常 SCN-07b-E2 区间无缺口**

```gherkin
Given 选定区间 stockShortageQuantity = 0
When 自动创建
Then 警告「无需补货」或拒绝（按策略）
```

---

### SCN-07c 手工创建供应计划（路径选择）

**价值：** VAL-04, VAL-06

**说明：** 弹出路径选择对话框：列出该 PISP 全部可选 **ENT-RT**、各 **ENT-RS** 摘要，以及选择该路径时的 **最早可完成时间（EAT）**；计划员选定路径后创建计划工单。

**动作：** `GET API-MAT-02` 预览 · `POST API-MAT-03` · `mode=MANUAL` · `routingId`

```gherkin
Given SCN-07a 已选中 PISP 与 period 区间
When 计划员打开「手工创建供应计划」对话框
Then GET API-MAT-02 返回每条 ENT-RT：routingId、pathPriority、RS 列表、EAT（基于当前 SRP/Slot 与 JIT 正排/倒排试算）
  And 计划员选择一条 ENT-RT 并确认数量/交期锚点
When 提交创建
Then 按所选路径物化 ENT-SO 与 ENT-OP 时间窗
  And 更新区间 PISPP 计划供应
  And 对话框关闭后二维表刷新
```

**异常 SCN-07c-E1 路径 EAT 晚于需求日**

```gherkin
Given 所选路径 EAT > 区间 demand 最晚 needDate
When 确认创建
Then 警告延期风险；允许计划员强制继续或改选路径
```

---

### SCN-07d 优化创建供应计划（最优路径）

**价值：** VAL-04, VAL-01, VAL-06

**说明：** 针对当前 PISP 区间内的未满足需求，调用 **PlanningOptimizer**（或路径选择子问题）在多条 ENT-RT 间选优，使目标（如最小延期、最小产能占用）最优后创建 ENT-SO。

**动作：** `POST API-MAT-03` · `mode=OPTIMIZE`

```gherkin
Given SCN-07a 已选中 PISP 与 period 区间
  And 存在多条 ENT-RT 与未满足 Demand
When 计划员执行「优化创建供应计划」
Then 构建 scoped 子问题：候选 = 各 RT 物化的 SupplyOrder + Operation + SRP 约束
  And 调用 SOL-ORT（或配置引擎）选优路径与槽位分配
  And hard score = 0 时落库 ENT-SO 并回写 PISPP
  And 返回选中 routingId、EAT、optimize 得分摘要
  And P95 响应 ≤ 120s（与 CTP 同级，NFR-01）
```

**异常 SCN-07d-E1 优化不可行**

```gherkin
Given 所有路径均 hard 不可行
When 优化创建
Then 返回不可行原因（产能 / 组件 / 工艺缺失）；不得部分落库
```

> **实现状态：** 供需平衡专页、API-MAT-02/03 及多路径 ENT-RT 为 **v1 目标**；现行物料计划页（API-MAT-01 日粒度表）为过渡 UI。见 §10 TODO-11。

---

### SCN-07e 选中区间查看 Demand

**价值：** VAL-04, VAL-06

**说明：** 在供需平衡页选中某 **ENT-PISP** 的 **ENT-PER 区间**（或 period 列），列出落在该区间内的全部 **ENT-DEM** 及未预留量。

```gherkin
Given SCN-07a 已选中 PISP 与 period 区间
When 计划员选中区间单元格或列头
Then GET API-MAT-04 返回 DTO-PDL（PeriodDemandListDto）
  And 每条含 demandId、sourceType、needDate、quantity、peggedQty、unpeggedQty、所属 PISPP/period
  And 列表与 ENT-PISPP.plannedDemandQuantityTotal 可核对
```

**异常 SCN-07e-E1 区间无 Demand**

```gherkin
Given 选定区间无 Demand 落桶
When 展开 Demand 面板
Then 展示空列表；不报错
```

---

### SCN-07f 选中 Demand 查看可匹配 Supply

**价值：** VAL-04

**说明：** 选中一条 ENT-DEM 后，展示可用于满足该 Demand 的全部 **ENT-SUP**（库存、工单产出、在途等），含可 peg 余量与时间。

```gherkin
Given SCN-07e 已选中 demandId
When 计划员选中该 Demand
Then GET API-MAT-05 返回 DTO-PSM（EligibleSupplyListDto）
  And 每条含 supplyId、类型（库存/工单产出等）、availableDate、availableQty、已 peg 余量
  And 仅包含与 Demand 同 PISP / 物料可替换规则允许的 Supply（RULE-FF-05）
```

---

### SCN-07g 拖拽手工预留（创建 Fulfillment）

**价值：** VAL-04, VAL-06

**说明：** 计划员通过拖拽在 Demand 与 Supply 间手工创建 **ENT-FF**（物料预留 / pegging）。

**动作：** `POST API-MAT-06` · `source=DRAG`

```gherkin
Given SCN-07e/f 已展示 Demand 与 Supply 列表
When 计划员将 Demand 拖拽到 Supply 上
  Or 将 Supply 拖拽到 Demand 上
Then POST API-MAT-06 创建 ENT-FF（quantity ≤ min(unpeggedDemand, unpeggedSupply)）
  And 遵守 RULE-FF-05；ROL 更新 PISPP 与 Demand/SUP 未预留量
  And UI 刷新 peg 状态与 SCN-07j 预警
```

**异常 SCN-07g-E1 数量或物料不匹配**

```gherkin
Given Supply 与 Demand 物料/PISP 不匹配或 quantity 超额
When 提交拖拽 peg
Then 拒绝并提示原因；不得创建 ENT-FF
```

**异常 SCN-07g-E2 Supply 时间晚于 Demand**

```gherkin
Given Supply.availableDate > Demand.needDate
When 拖拽创建
Then 拒绝创建 ENT-FF（RULE-FF-08 hard）
  And 提示 Supply 不得晚于 Demand 要求交期
```

---

### SCN-07h Demand 自动预留

**价值：** VAL-04, VAL-06

**说明：** 对选中 Demand 右键「自动预留」；系统按 RULE-FF-06 挑选最合适 Supply 并批量创建 ENT-FF。

**动作：** `POST API-MAT-07` · `anchorType=DEMAND`

```gherkin
Given SCN-07e 已选中 demandId 且 unpeggedQty > 0
When 计划员选择「自动预留」
Then 系统按 needDate 就近、PEG-INV 优先于 PEG-WO（对齐 RULE-FF-01  spirit）、可用量最大等规则选 Supply
  And 创建 ENT-FF 直至 Demand 满足或 Supply 用尽
  And 返回 reservedQty 与剩余 unpeggedQty
```

**异常 SCN-07h-E1 无可用 Supply**

```gherkin
Given 无 eligible Supply
When 自动预留
Then 提示「无可用供应」；可选生成 PEG-SH 或引导 SCN-07b 建供应
```

---

### SCN-07i Supply 自动预留

**价值：** VAL-04

**说明：** 对选中 Supply 右键「自动预留」；系统将可 peg 量自动分配给最合适的 open Demand（同 PISP 区间内，优先 needDate 早、优先级高）。

**动作：** `POST API-MAT-07` · `anchorType=SUPPLY`

```gherkin
Given SCN-07f 已选中 supplyId 且 unpeggedQty > 0
When 计划员选择「自动预留」
Then 系统按 RULE-FF-06 将 Supply 分配给 eligible Demand 列表
  And 创建 ENT-FF；更新双方 unpeggedQty
```

---

### SCN-07j 预留风险预警

**价值：** VAL-01, VAL-04, VAL-06（风险可视化）

**说明：** 对区间内未完全预留的 Demand/Supply 及供需时间错配进行预警。

```gherkin
Given SCN-07a 已选定 PISP 与 period 区间
When 页面加载或 peg 变更后
Then GET API-MAT-08 返回 DTO-PRA（ReservationAlertDto[]）
  And 预警类型包括：
    | UNALLOCATED_DEMAND | Demand.unpeggedQty > 0 |
    | UNALLOCATED_SUPPLY | Supply 可 peg 余量 > 0 且无对应 Demand |
    | TIME_MISMATCH | Supply.availableDate > Demand.needDate 的 ENT-FF 或候选对 |
  And 预警行可定位到 demandId / supplyId / fulfillmentId
  And KPI 面板展示区间内未预留 Demand 数、时间偏差条数
```

**异常 SCN-07j-E1 预警仅提示不阻断**

```gherkin
Given 存在 TIME_MISMATCH 预警
When 计划员继续手工 peg
Then 允许操作；预警持续展示直至时间对齐或 peg 移除
```

> **实现状态：** SCN-07e~j、API-MAT-04~08 为 **v1 目标**；与 TODO-11 一并交付。

---

## §3.9 平台与技术场景

### SCN-T01 主计划 PATH-ONT optimize

**价值：** VAL-02, VAL-06 · **标签：** ADR-08

```gherkin
Given ENT-SES 已 create，权威 ENT-OG 已装载
When POST optimize（PATH-ONT）
Then 输入来自 Session ENT-OG（OntologyToMasterPlanScheduleMapper）
  And 结果写回同一 ENT-OG；hard = 0 时可 confirm
```

> 迁移期 TODO-08：退役 PATH-ENT 前保留 AC-05 parity 回归。

**异常 SCN-T01-E1 optimize 未反映 simulate**

```gherkin
Given simulate 已改 PISPP
When optimize
Then 结果须反映 simulate（禁止 JPA 重建问题体）
```

---

### SCN-T02 Session confirm 落库

**价值：** VAL-02 · **规则：** RULE-SES-02

```gherkin
Given ENT-SES 已 optimize 且 hard = 0
When POST .../sessions/{id}/confirm
Then 产生 ENT-PV 并持久化主计划分配
```

**异常 SCN-T02-E1 未 optimize 即 confirm**

```gherkin
When create 后直接 confirm
Then 4xx；不产生空 planVersion
```

---

### SCN-T03 Workspace 隔离

**基础规则：** RULE-WS-01 · **验收：** AC-08

```gherkin
Given Workspace "te" 与 "default" 数据隔离
When X-Workspace-Id: te
Then 仅返回 te 数据
```

---

### SCN-T04 工艺主数据投影

**价值：** VAL-03 · **规则：** RULE-RT-01,02

```gherkin
Given 产品已有 ProductResource / BOM
When GET API-RT-01
Then 返回 ENT-RT / ENT-RS 及 RSOSR/RSIM/RSOM 结构
```

---

### SCN-T05 详细排程手动调序确认

**价值：** VAL-03, VAL-05 · **标签：** S05

```gherkin
Given 工单已下发并创建 S05 Session
When 甘特调序 simulate → confirm
Then 持久化 detail_schedule 版本
```

**异常 SCN-T05-E1 齐套不足**

```gherkin
Given 批次 SHORTAGE
When confirm（策略要求齐套）
Then 拒绝或警告
```

---

### SCN-T06 用户与权限管理

**基础规则：** RULE-IAM-01~06 · **验收：** AC-IAM-* · **规范：** [§18 IAM](../volumes/platform/18-19-workspace-platform.md)

**角色：** 计划员 · Workspace 管理员 · 超级管理员

#### SCN-T06a 用户登录与 Workspace 列表

```gherkin
Given 用户 u1 已注册且 **尚未** 加入任何 workspace
When u1 登录
Then 显示 CreateWorkspacePage，须 **手动** 创建工作区
  And GET /api/v1/iam/me 返回 hasWorkspaces=false

Given u1 已创建 workspace "my-project" 且 workspace_member 角色 OWNER
When u1 登录
Then WorkspaceSelector 仅展示 "my-project"
  And 默认选中 localStorage 中合法 workspaceId

Given dev 用户在 dev-mode 下登录且无 workspace_member
When 进入应用
Then **不强制** CreateWorkspacePage
  And 可通过顶栏「管理数据集」手动创建
```

#### SCN-T06b Workspace 成员与模块权限

```gherkin
Given WS_ADMIN 在 workspace "project-a" 配置
  And 启用模块 MOD-OCP、MOD-SCH；关闭 MOD-SLT
  And 成员 u2 对 MOD-OCP=VIEW、MOD-SCH=EDIT
When u2 访问订单协同计划分析页
Then 可查看需求满足与产能页
When u2 对 COLD 发起 CTP optimize
Then HTTP 403 MODULE_FORBIDDEN（MOD-OCP 仅 VIEW）
When u2 打开侧栏
Then 不显示「分切排样」导航
```

#### SCN-T06c 超级管理员

```gherkin
Given 用户 sa 为 SUPER_ADMIN
When sa 打开 /admin/users
Then 可创建/禁用任意用户并授予 Super Admin
  And 可配置任意 Workspace 的成员与模块
  And 操作写入 iam_audit_log
```

**异常 SCN-T06-E1 非成员访问 WS**

```gherkin
Given 用户 u3 非 workspace "project-a" 成员
When 请求 X-Workspace-Id: project-a
Then HTTP 403 WORKSPACE_FORBIDDEN
  And 不得返回任何业务数据
```

---

### SCN-T07 数据集成（External → 质检 → Sync）

**规范：** [§19](../volumes/platform/18-19-workspace-platform.md) · **模块：** MOD-DI

#### SCN-T07a Excel 导入 External

```gherkin
Given Workspace 启用 MOD-DI 与 ADP-EXCEL
When 计划员上传符合 §11 列模板的 Excel
Then 行写入 external_* 且 quality_status=PENDING
  And 生成 import_batch_id=IMP-*
When 执行 checkBatch
Then PASSED 行可 sync 至 md_* / txn_*
  And FAILED 行带 quality_issue_codes 可在 /integration/quality 查看
```

#### SCN-T07b ERP SAP 适配器（Phase 1）

```gherkin
Given ADP-ERP-SAP 已配置且 Workspace 启用
When 触发 adapter run
Then 数据写入 external_* 且 source_system=ERP_SAP
  And 主计划仍只读 md_*（RULE-MD-01）
```

#### SCN-T07c MES 反馈

```gherkin
Given ADP-MES 同步 SchedulerFeedback
When 写入 external_scheduler_feedback
Then MOD-OCP 产能计算扣减 feedback 分钟（RULE-SUP-05）
```

---

**回指：** [01-value-goals.md](./01-value-goals.md) · [04-business-rules.md](./04-business-rules.md) · [08-acceptance.md](./08-acceptance.md) · [18-identity-access-management.md](../volumes/platform/18-19-workspace-platform.md) · [19-workspace-modules-and-adapters.md](../volumes/platform/18-19-workspace-platform.md)
