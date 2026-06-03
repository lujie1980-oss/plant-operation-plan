# 详细排程推演规则可定制 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Phase 1 — 将 Session 推演整理为 `SimulationPipeline` + 可发现内置规则，行为与现有测试一致。

**Architecture:** `com.plantops.scenario.planning.simulation` 包承载 `TimingRule` / `ValidationRule` / `AffectedClosureRule`；`DetailScheduleTimingKernel` 承接原 `LineChainTimingUtil` 赋时循环；`LineChainTimingUtil` 仅作静态门面委托。

**Tech Stack:** Quarkus CDI, Java 21, JUnit 5 `@QuarkusTest`

**Spec:** [2026-06-02-detail-schedule-simulation-rules-design.md](../specs/2026-06-02-detail-schedule-simulation-rules-design.md)

---

### Task 1: 核心接口与上下文

**Files:**
- Create: `scenario/planning/simulation/SimulationMode.java`
- Create: `scenario/planning/simulation/SimulationRuleContext.java`
- Create: `scenario/planning/simulation/SimulationRuleContextFactory.java`
- Create: `scenario/planning/simulation/TimingRule.java`
- Create: `scenario/planning/simulation/ValidationRule.java`
- Create: `scenario/planning/simulation/AffectedClosureRule.java`

- [ ] 定义 record `SimulationRuleContext` 与三接口
- [ ] Factory 从 `DetailSchedule` + mode + seeds 构建上下文

### Task 2: Registry + TimingKernel

**Files:**
- Create: `SimulationRuleRegistry.java`
- Create: `DetailScheduleTimingKernel.java`
- Modify: `LineChainTimingUtil.java` → 委托 kernel

- [ ] Registry 按 `order` 排序并过滤 `BusinessRuleScopeService`
- [ ] Kernel 迁移 16 轮迭代 + 产线队列扫描（等价原逻辑）
- [ ] `LineChainTimingUtil.applyAllStartTimes` 经 CDI 调 kernel

### Task 3: 内置 Timing / Closure 规则

**Files:**
- Create: `simulation/timing/*.java`
- Create: `simulation/closure/*.java`
- Create: `SimulationClosureExpander.java`

- [ ] `ChangeoverTimingRule`, `RoutingChainTimingRule`, `ContractEarliestTimingRule`
- [ ] 闭包三规则替代 `DetailScheduleSimulationEngine.expandAffectedClosure` 内联逻辑

### Task 4: 内置 Validation 规则 + Pipeline

**Files:**
- Create: `simulation/validation/*.java`
- Create: `ValidationPipeline.java`, `SimulationPipeline.java`
- Modify: `ScheduleValidationService.java`, `DetailScheduleSimulationEngine.java`

- [ ] 9 类 violation 拆为独立 `ValidationRule`
- [ ] Engine 仅调 `SimulationPipeline`

### Task 5: 文档与验证

**Files:**
- Modify: `docs/detail-schedule-simulation-layer.md` §7–§9
- Modify: spec status → Phase 1 进行中

- [ ] `mvn test` — `LineChainTimingUtilTest`, `DetailScheduleSimulationEngineTest`, `DetailScheduleSessionMutationTest`

---

## Phase 2+ (out of scope here)

- `simulation_profile` 表、API `appliedRules`、`ruleOverrides`
- Timefold `OperationStartTimeCalculator` 与 kernel 统一（Phase 4）
