# §2 统一术语表

> **规则：** 全文仅使用下表术语；禁止同义词混用。AI 生成代码时必须引用本表 ID。

| ID | 术语 | 定义 | 禁止混用 |
|----|------|------|----------|
| **ENT-OG** | OntologyGraph | 内存聚合图；**每个 ENT-WS 一张权威图**（ADR-07），承载全厂供需/工序/满足/PISPP/SRP/Slot | 图、内存模型、投影图 |
| **ENT-CO** | CustomerOrder | 销售订单头；`customerOrderNo`；齐套 RULE-DEM-05 | 订单头 |
| **Delivery Tolerance** | — | RULE-DEM-02：target/min/max 交付容差带 | 容差 |
| **PPQ** | Preferred Pack Quantity | RULE-DEM-04：满足量须为 PPQ 倍数 | 最小包装 |
| **ResourceEfficiency** | — | RULE-SUP-05：有效产能 = (日历−反馈)×效率 | 设备效率 |
| **SchedulerFeedback** | — | 细排已占用产能分钟；扣减主计划可用时间 | 排程反馈 |
| **ResourceUsageType** | SINGLE \| BATCH | RULE-SUP-03：单件 vs 批次炉/罐 | 设备类型 |
| **ENT-COLD** | CustomerOrderLineDelivery | 销售订单行交付批次；**前端主粒度**；ID `COLD-{so}-{line}-{seq}`；含 **confirmedDeliveryDate**（SCN-01d 确认交期；SCN-01f 可清空）；**容差/PPQ/交期策略**见 RULE-DEM-02~04 | 订单行、SO 行 |
| **ENT-COL** | CustomerOrderLine | 销售订单行；ID `COL-{so}-{line}` | SalesOrderLine |
| **ENT-DEM** | Demand | 统一需求锚点；来源类型 CUSTOMER_DELIVERY / FORECAST / BOM_COMPONENT；**满足动机**见 RULE-FF-09（`unpeggedQty > 0` → 寻 Supply / 驱动 PISPP 建 SO） | 需求、订单需求 |
| **ENT-SO** | SupplyOrder | 计划供应订单；`id = workOrderNo`；与工单同一业务对象 | 工单、WO |
| **ENT-PU** | PlanUnit | 计划单元；默认每 SupplyOrder 1:1 | 批次、lot |
| **ENT-OP** | Operation | 工序实例；由工艺模板物化 | 工序、operation |
| **ENT-OOSR** | OperationOnStandardResource | 工序×标准资源候选 | 资源行、product_resource |
| **ENT-RCA** | ResourceCapacityAssignment | OP 经 OOSR 在 **ENT-SRP** 上的占用分钟；optimize 写回本体 · **§5.5.1** | 产能分配、RCA |
| **ENT-OIM** | OperationInputMaterial | 工序投料 | 组件需求 |
| **ENT-OOM** | OperationOutputMaterial | 工序产出 | 产出 |
| **ENT-SUP** | Supply | 供应（工单产出/库存/缺口）；**分配动机**见 RULE-FF-10（`unpeggedQty > 0` → 经 PISPP 寻 Demand）；`SUP-SHORT-*` 不参与分配动机 | 供给 |
| **ENT-FF** | Fulfillment | Demand 与 Supply 的挂接边；含量与类型；**物料预留**在 PISPP 页手工/自动创建（SCN-07g~i） | peg、pegging、预留 |
| **ENT-BD** | BomDependency | 父子 SupplyOrder BOM 依赖；**由 Fulfillment 派生** | BOM 边、WOBom |
| **ENT-PISP** | ProductInStockingPoint | 产品×库存点；ID `PISP-{product}-{stockingPoint}` | 物料点 |
| **ENT-PISPP** | ProductInStockingPointPeriod | 物料期间平衡（onHand、供需合计、缺口）；**消缺动机**见 RULE-MRP-05 | 物料期间 |
| **ENT-SRP** | StandardResourcePeriod | 标准资源×Period 产能；**= Σ ENT-PRP**（ADR-17 · §5.8.2） | 产能期间 |
| **ENT-PRP** | PhysicalResourcePeriod | 物理资源×Period 产能；**日历真相源** | 设备期间 |
| **ENT-PER** | Period | 有序时间桶；由 `ontology_period_sequence` 展开；**可含 shift 粒度**（ADR-16 · §5.8.1） | 时段 |
| **ENT-SS** | SchedulingSlot | **已废止**（@Deprecated · S5）；`TimeSlot` 按需 DERIVE | 时间槽、TimeSlot |
| **ENT-RT** | Routing | 工艺路线头；挂 ENT-PISP；ID `RT-{pispId}` 或 `RT-{pispId}-{seq}`；**一个 PISP 可有多条路径**，含 **pathPriority**（SCN-07b~d） | 路线 |
| **ENT-RS** | RoutingStep | 工艺工序模板；ID `RS-{pispId}-{seq}` | 工艺步骤、OperationDefinition |
| **ENT-RSOSR** | RoutingStepOnStandardResource | 模板层资源候选 | — |
| **ENT-RSIM** | RoutingStepInputMaterial | 模板层投料（通常首道工序） | — |
| **ENT-RSOM** | RoutingStepOutputMaterial | 模板层产出（通常末道工序） | — |
| **ENT-SP** | StockingPoint | 库存点；默认 `DEFAULT-FG`（v1 可 CFG） | 库位 |
| **ENT-RG** | ResourceGroup | 标准资源分组；产能视图维度 | 工作中心组 |
| **ENT-SR** | StandardResource | 标准资源；主计划槽位与 SRP 资源 ID | 工作中心 |
| **ENT-PR** | PhysicalResource | 物理设备/产线；**N:1** 映射 **ENT-SR**；日历生效层（ADR-17） | 设备 |
| **EXT-SP** | external_stocking_point | 上游库存点 staging；质检后 sync → md_stocking_point | 库位表 |
| **EXT-PISP** | external_product_in_stocking_point | 上游产品×库存点 staging | 物料工厂视图 |
| **EXT-RT** | external_routing | 上游工艺路线 staging | 路线表 |
| **EXT-RS** | external_routing_step | 上游工序 staging | 工序表 |
| **EXT-RSOSR** | external_routing_step_on_standard_resource | 上游工序资源 staging | 工艺资源 |
| **EXT-RSIM** | external_routing_step_input_material | 上游工序投料 staging | 组件 |
| **EXT-RSOM** | external_routing_step_output_material | 上游工序产出 staging | 产出 |
| **EXT-RG** | external_resource_group | 上游资源组 staging | 资源组 |
| **EXT-SR** | external_standard_resource | 上游标准资源 staging | 工作中心 |
| **EXT-PR** | external_physical_resource | 上游物理资源/设备 staging | 设备、产线 |
| **EXT-CO** | external_customer_order | 上游销售订单头 staging | 销售订单 |
| **EXT-COL** | external_customer_order_line | 上游订单行 staging | SO 行 |
| **EXT-COLD** | external_customer_order_line_delivery | 上游交付批次 staging | 交货批次 |
| **EXT-WO** | external_work_order | 上游工单 staging → **FIRM** SupplyOrder | 工单 |
| **EXT-WOO** | external_work_order_operation | 上游工单工序 staging | 工序 |
| **EXT-WOOR** | external_work_order_operation_resource | 上游工序资源 staging | 工艺资源 |
| **EXT-INV** | external_inventory | 上游库存快照 staging | 库存 |
| **EXT-PO** | external_purchase_order | 上游采购订单 staging | PO |
| **MD-*** | Internal Master | 质检通过的 canonical 主数据表（`md_*`）；**计划唯一读源**（RULE-MD-01） | 内部主数据 |
| **TXN-*** | Internal Transactional | 质检通过的交易表（`txn_*`）；**OG 需求/供应输入**（RULE-TX-01） | 内部交易 |
| **KN-STD** | StandardKnowledge | 产品内置业务知识；§4 RULE-*、§3 SCN、默认 CFG；**类型目录** §14 | 默认规则 |
| **KN-TYPE-INV** | 不变量 hard | Standard RULE；不可 overlay | hard 规则 |
| **KN-TYPE-OPT** | 优化目标 soft | soft RULE 权重 | soft 惩罚 |
| **KN-TYPE-MOT** | 行为动机 | Demand/Supply/PISPP 寻供寻需 | 动机 |
| **KN-TYPE-EXM** | 豁免 | PLAN-01-E* 等 | 豁免 |
| **KN-TYPE-PAR** | 参数默认 | defaults/parameters.yaml | CFG 默认 |
| **KN-TYPE-INT** | 集成门禁 | MD/TX sync | 同步规则 |
| **KN-TYPE-STR** | 结构约束 | MD-07~13 | 主数据结构 |
| **KN-TYPE-PLT** | 平台 | WS/Session/OG | 平台 |
| **KN-TYPE-SCN** | 场景 | §3 GWT | 场景 |
| **KN-TYPE-VAL** | 价值/KPI | §1 · §15 KPI-MP-* | KPI |
| **KPI-MP-S*** | 主计划评分 KPI | §15 · CP 评分类 8 项 | 交付/效率/偏好 soft |
| **KPI-MP-C*** | 主计划约束 KPI | §15 · CP 约束类 10 项 | 物料/产能 hard+soft |
| **KPI-MP-B*** | 主计划业务 KPI | §15 · 计划员可读 | OTIF、overload 等 |
| **KPI-MP-TOT** | 主计划 Total KPI | §15 六域聚合 | 方案总分 |
| **KN-TYPE-AC** | 验收 | §8 | AC |
| **UI-P-*** | UI 设计原则 | §17 | — |
| **UI-COMP-*** | 标准组件 | §17.5 | 组件 |
| **UI-NAV-*** | 跨页导航契约 | §17.8 | 深链 |
| **DOM-*** | 业务领域 | FF/MRP/MP/RT/MD/TX/PLT/PER | 领域 |
| **KN-IND** | IndustrySpecificKnowledge | 行业 Knowledge Pack；叠加于 Standard | 行业包 |
| **KN-CUS** | CustomizedKnowledge | Workspace overlay + BusinessRules；客户/项目定制 | 客户配置 |
| **Effective Knowledge** | — | merge(STD, IND, CUS) 运行时有效知识 | 有效规则 |
| **quality_issue_codes** | — | external 行质量问题码（MD-Q-*）；见 §11.4.2 | 错误码 |
| **ENT-PV** | PlanVersion | 一次主计划求解产生的场景版本；`planVersionId` | 场景、scenario · **§5.19.4** |
| **ENT-USR** | AppUser | 平台用户；IAM · §18 | 用户 |
| **ENT-WS** | Workspace | 数据集隔离单元；`ownerUserId` · `workspaceType`；成员与 MOD 开关 · §18 | 租户、tenant |
| **MOD-DI** | DataIntegration | 数据集成；External_* + ADP-* · §19 | 集成 |
| **PROC-S04** | 主计划求解 | 工序级时间槽分配与 **PlanningOptimizer** 求解过程（SCN-06）；**≠** 模块 UI 名称 | MPS、master plan 求解 |
| **MOD-OCP** | OrderCollaborationPlanning | **订单协同计划** Workspace 模块（原 MP）；路由 `/master-plan/*` | OCP、订单协同 |
| **MOD-SCH** | Scheduling | 作业排程模块 | 排程 |
| **MOD-SLT** | Slitting | 分切排样模块 | 分切 |
| **MOD-CAL** | FactoryCalendar | 工厂日历（归属数据集成分类） | 日历 |
| **ADP-ERP-SAP** | ERPAdapterSAP | SAP ERP → external_* | SAP |
| **ADP-MES** | MESAdapter | MES → external_* | MES |
| **ADP-EXCEL** | ExcelDataAdapter | Excel → external_* | Excel |
| **PERM-VIEW** | — | 模块只读权限 | 查看 |
| **PERM-EDIT** | — | 模块修改/运行权限 | 修改 |
| **ROLE-SUPER-ADMIN** | — | 平台超级管理员 · RULE-IAM-05 | 超管 |
| **ENT-SES** | MasterPlanOntologySession | 全厂本体沙盘；create→simulate→optimize→confirm | Session、沙盘 · **§5.19.2** |
| **ENT-SBX** | DeliveryPlanningSandbox | 单 COLD **scope** 的 trial 会话；**引用**与 ENT-SES 相同的权威 ENT-OG，非独立装载图 | 交付沙盘、单订单图 · **§5.19.3** |
| **PROC-S01** | 需求满足 | 展示 COLD 满足链与 KPI | 需求池 |
| **PROC-S05** | 详细排程 | 产线分钟级排程 | 细排、detail schedule |
| **ENT-OP-SCH** | OperationSchedule | MOD-SCH 分钟级工序排程；SCH-P0 只读投影自 `detail_schedule_operation` | 细排工序 |
| **ENT-RCA-SCH** | PhysicalResourceCapacityAssignmentSchedule | MOD-SCH 物理资源分钟占用；挂 ENT-PR 非 ENT-SRP | 细排 RCA |
| **PEG-INV** | INVENTORY_PEG | Fulfillment 类型：库存优先满足 | 库存 peg |
| **PEG-WO** | WORK_ORDER_PEG | Fulfillment 类型：工单产出满足 | 工单 peg |
| **PEG-SH** | SHORTAGE_PEG | Fulfillment 类型：缺口 | 缺料 peg |
| **PATH-ENT** | 实体路径 | ~~S04 经 `MasterPlanPlanningContextBuilder` 扫描 JPA~~ **已废止**（ADR-08 · TODO-08 收口 2026-07-01） | 传统路径 |
| **PATH-ONT** | 本体直驱路径 | **S04 唯一规范路径**：ENT-OG → `OntologyToMasterPlanScheduleMapper` → `PlanningOptimizer` | 直驱、direct solve |
| **SOL-TF** | Timefold | 约束求解引擎；**主计划**仅当 `planning_optimizer_engine=timefold` 时使用；S05 细排与分切当前仍直连（配置化见 TODO-07） | 默认求解器 |
| **SOL-ORT** | OR-Tools | CP-SAT；**主计划默认**引擎（`planning_optimizer_engine=ortools`） | 有限能力 trial 插件 |
| **CapacityOverloadCost** | — | **leaf ENT-SRP** 上 `Σ ENT-RCA.assignedMinutes` 超出 `availableCapacity` 的 soft 惩罚；见 RULE-MP-07 | 超载成本 |
| **ROL** | RolEngine | 本体变更传播引擎；simulate 应用 ChangeSet | 传播 |
| **DTO-FC** | OrderFulfillmentChainDto | 满足链 API 响应；COLD 根 | 订单链、PlanningChain |
| **ATP** | Available to Promise | 基于库存与满足链的可承诺量/交期评估（通常较快） | 可承诺、现货承诺 |
| **CTP** | Capable to Promise | 在物料与产能约束下经 optimize 的可承诺交期 | 能力承诺、有限能力 ATP |
| **DTO-MBP** | MaterialRequirementReportDto | 供需平衡 / 物料计划 API 响应；含 ENT-PISPP 投影行（MaterialBalanceRowDto × period） | 物料平衡表 |
| **DTO-PDL** | PeriodDemandListDto | 某 PISP×period 区间内 Demand 列表（SCN-07e） | 区间需求 |
| **DTO-PSM** | EligibleSupplyListDto | 可满足指定 Demand 的 Supply 候选（SCN-07f） | 可匹配供应 |
| **DTO-PRA** | ReservationAlertDto | 预留预警：未分配 Demand/Supply、时间偏差（SCN-07j） | 预留风险 |
| **EAT** | Earliest Achievable Time | 选定工艺路径下，供应计划最早可完成时间（SCN-07c/d） | 最早完工、ATP date |
| **PROC-S02** | 供需平衡 | PISPP 二维表与按路径创建供应计划 | 物料计划、MRP 页 |

---

**回指：** [00-meta.md](./00-meta.md) · 下游全文章节
