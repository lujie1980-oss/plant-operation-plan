# M5 — 本体优先 + 可插拔求解器

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.  
> **设计文档：** [ontology-optimizer-plugin.md](../../ontology-optimizer-plugin.md)

**Goal:** 前端只读本体满足链；Timefold 降为 `PlanningOptimizer` 插件；单交付 FinitePlan 求解回写内存图并投影到 `OrderFulfillmentChainDto`；为 OR-Tools 预留同一 Problem/Result 契约。

**Tech Stack:** Java 21, Quarkus, Timefold 3.x, JUnit 5, React + `frontend/src`.

---

## Phase 1 — 插件 + 单交付 Sandbox（当前）

- [ ] **1.1** 新建 `com.plantops.scenario.planning.optimizer`：`PlanningOptimizer`, `PlanningProblem`, `PlanningAssignment`, `OptimizerResult`, `PlanningDiagnostic`
- [ ] **1.2** `OntologyToPlanningProblemMapper`（先支持 scoped `MasterPlanPlanningContext` 桥接，再扩展纯图投影）
- [ ] **1.3** `TimefoldPlanningOptimizer` + `PlanningOptimizerRegistry`（读 `planning_optimizer_engine`）
- [ ] **1.4** `PlanningResultApplicator`（`OperationPlannedTimeProjection` + `OntologyTimefoldMapper` + `RolTransaction`）
- [ ] **1.5** `DeliveryPlanningSandbox` + `DeliveryPlanningSandboxStore` + `DeliveryPlanningSandboxService`
- [ ] **1.6** `OrderDemandActionService.finitePlanForDelivery` → Sandbox.optimize；返回 `fulfillmentChain`
- [ ] **1.7** `OntologyFulfillmentChainProjector`：SUPPLY_ORDER 节点 planned rollup；diagnostics 注入
- [ ] **1.8** 前端 `DemandPage`：FinitePlan 后 `setChain(result.fulfillmentChain)`，去掉 `planningChain` 依赖
- [ ] **1.9** 测试：`TimefoldPlanningOptimizerTest`, `DeliveryPlanningSandboxServiceTest`, 更新 `OrderDemandActionOntologyChainTest`

**Phase 1 验收：**

1. JIT 后 FinitePlan，满足链甘特显示 Timefold 时间窗（非 JIT 占位）
2. 刷新 `fulfillment-chain` 与动作返回一致（sandbox 存活期内）
3. `mvnw test` 通过

---

## Phase 2 — DTO 合并

- [x] **2.1** 扩展 `FulfillmentChainNodeDto` / TS 类型（planningSignals, trialRevision, solverEngine）
- [x] **2.2** Deprecate `OrderPlanningChainDto`, `OrderPlanningChainProjector`, `OrderPlanningChainService.preview`
- [x] **2.3** 移除 `DemandPage` `viewMode: 'planning'`；删除或降级 `OrderPlanningChainPage`
- [x] **2.4** `CONFIRM_PROMISE_DATE` 从 fulfillment 链推断 promiseDate
- [x] **2.5** 移除 `OrderDemandActionResult.planningChain`

---

## Phase 3 — Session + 持久化

- [x] **3.1** `MasterPlanOntologySessionService.optimize` → `PlanningOptimizerRegistry`
- [x] **3.2** `OntologyStatePersister` confirm 写 Operation planned + audit allocation
- [x] **3.3** `OntologyLoader` 反灌 published planned 到图
- [x] **3.4** confirm 禁止无条件 `MasterPlanService.solve()` 重扫 DB（D25）

---

## Phase 4 — OR-Tools

- [x] **4.1** `OrtoolsPlanningOptimizer implements PlanningOptimizer`
- [x] **4.2** 对等性测试套件（hard + assignment 键）
- [x] **4.3** 文档更新 OR-Tools 切换说明

---

## 文件清单（Phase 1 新增/修改）

| 操作 | 路径 |
|------|------|
| new | `scenario/planning/optimizer/*.java` |
| new | `scenario/planning/optimizer/timefold/TimefoldPlanningOptimizer.java` |
| new | `scenario/planning/delivery/DeliveryPlanningSandbox.java` |
| new | `scenario/planning/delivery/DeliveryPlanningSandboxStore.java` |
| new | `scenario/planning/delivery/DeliveryPlanningSandboxService.java` |
| modify | `OrderDemandActionService.java` |
| modify | `OntologyFulfillmentChainProjector.java` |
| modify | `frontend/src/pages/DemandPage.tsx` |
| modify | `ParameterRegistry.java`（`planning_optimizer_engine`） |

---

*Checkbox 随实施更新。*
