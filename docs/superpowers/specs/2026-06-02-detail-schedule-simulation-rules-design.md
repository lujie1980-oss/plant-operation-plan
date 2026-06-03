# 详细排程推演规则可定制 — 设计规格（审阅稿）

> **状态**：Phase 1 已落地（管道 + 内置规则拆分）；Phase 2+ 待做  
> **日期**：2026-06-02  
> **关联**：[detail-schedule-simulation-layer.md](../../detail-schedule-simulation-layer.md)、[aps-planning-layer.md](../../aps-planning-layer.md) §5.6  
> **前置**：Session 推演、链式赋时、校验、生产排程页交互已落地

---

## 1. 背景与目标

### 1.1 问题

当前 S05 Session **推演层**（`DetailScheduleSessionService.simulate`）行为正确但**高度硬编码**：

- 赋时集中在 `LineChainTimingUtil`（换型、工艺链、并行对、契约、迭代抬升）。
- 校验集中在 `ScheduleValidationService`（9 种固定 `ruleCode`）。
- 增量闭包固定在 `DetailScheduleSimulationEngine.expandAffectedClosure`。
- Timefold 求解路径另有 `OperationStartTimeCalculator` / Shadow，与 Session 链式赋时**逻辑重复**。

新增或调整一条推演规则（例如新换型维度、不同 earliest 策略、定制波及范围）需要改多个类，且难以按场景/客户**开关或参数化**。

### 1.2 目标

1. **整理**推演计算为清晰、可文档化的**管道（Pipeline）**。
2. **支持可定制推演规则**：在不改核心编排的前提下，通过注册/配置启用、排序、参数化行为规则。
3. **保持行为兼容**：Phase 1 重构后，现有测试与生产排程页行为不变。
4. **与现有主数据规则体系对齐**：换型矩阵、工序衔接、业务规则页开关等继续复用。

### 1.3 非目标（本规格不包含）

- 不在第一版引入脚本/DSL 规则引擎（Drools、Groovy 等）。
- 不改变 Timefold 选优算法本身（`DetailScheduleConstraintProvider` 权重调参另议）。
- 不做「真·局部赋时」（仅对闭包工序重算时间）；仍保持**全局链式收敛**以保证时间一致。
- 不将赋时/校验逻辑下沉到前端（甘特仅展示后端衍生字段，如 `changeoverMinutesBefore`）。

---

## 2. 现状：推演流水线

```text
createSession / get
  └─ DetailSchedulePlanningContextBuilder (P0–P4)
  └─ DetailScheduleProblemMapper → DetailSchedule + ProblemFacts 快照

simulate(request)
  1. DetailScheduleSessionMutation.applyPatches(stepPatches) → patchTouched
  2. seeds = patchTouched ∪ affectedOperationIds
  3. if fullReschedule || seeds.isEmpty → FULL else INCREMENTAL（闭包仅标记 recalculatedIds）
  4. LineChainTimingUtil.applyAllStartTimes(schedule)
  5. ScheduleValidationService.validate(schedule)
  6. buildSessionDto → preview + violations + simulationMode
```

### 2.1 已有「规则」分层

| 层 | 机制 | 示例 |
|----|------|------|
| **数据规则** | 主数据表 + `DetailScheduleProblemFacts` 快照 | 换型矩阵、工序衔接、并行对清单 |
| **范围规则** | `BusinessRuleScopeService` + `BusinessRuleTypeIds` | 某 ruleType 在 S05 是否启用 |
| **行为规则** | 硬编码 Java | 如何累加 cursor、如何校验、如何扩闭包 |

**可定制性缺口在「行为规则」**：数据与开关已有，缺统一插件点与场景参数。

### 2.2 关键类职责（重构前）

| 类 | 包 | 职责 |
|----|-----|------|
| `DetailScheduleSessionMutation` | scenario | patch → 产线 queue |
| `DetailScheduleSimulationEngine` | scenario/planning | FULL/INCREMENTAL 编排 |
| `LineChainTimingUtil` | solver/detailschedule | 链式赋时 |
| `ScheduleValidationService` | scenario/planning | 推演后校验 |
| `DetailScheduleSessionService` | scenario | REST 编排、preview 构建 |
| `BusinessRuleScopeService` | masterdata | 规则项目启用范围 |
| `DetailScheduleProblemFacts` | solver/detailschedule | 换型/衔接/契约快照 |

---

## 3. 方案对比

### 方案 A：管道 + Java 插件（推荐）

将赋时/校验/闭包拆为 **CDI 可发现** 的 `TimingRule` / `ValidationRule` / `AffectedClosureRule` 实现；`SimulationPipeline` 按 `SimulationRuleContext`（facts + 启用集合 + 场景参数）调度。

| 优点 | 缺点 |
|------|------|
| 类型安全、易单测、与 Quarkus 一致 | 每条新规则需发版 |
| 可渐进从 `LineChainTimingUtil` 迁移 | 初期重构量中等 |
| 与 `BusinessRuleTypeIds` 自然对齐 | — |

### 方案 B：配置驱动表达式（JSON + 简单表达式）

规则逻辑写在外部配置，运行时解释。

| 优点 | 缺点 |
|------|------|
| 不改代码即可调参 | 调试难、与工艺链/并行对强逻辑不匹配 |
| — | 双路径（Session/Timefold）更难保证一致 |

### 方案 C：仅文档整理 + 继续硬编码

| 优点 | 缺点 |
|------|------|
| 零重构 | 无法满足「可定制推演规则」目标 |

**推荐：方案 A**。Phase 1 只做管道与内置规则拆分（行为不变）；Phase 2 加 `SimulationProfile` 参数；Phase 3 按需新增插件类。

---

## 4. 目标架构

### 4.1 推演管道

```text
SimulationPipeline.run(ctx, schedule, request):

  Phase 1  ApplyMutations
           DetailScheduleSessionMutation.applyPatches (不变)

  Phase 2  ResolveMode
           fullReschedule || empty seeds → FULL
           else → INCREMENTAL + expandAffectedClosure(rules...)

  Phase 3  BuildRuleContext
           schedule.problemFacts
           + enabledRuleTypes (BusinessRuleScope ∩ Profile)
           + ruleParams (SimulationProfile)
           + mode, seedOperationIds

  Phase 4  TimingPhase
           DetailScheduleTimingKernel.applyAll(ctx, schedule)
           └─ 对每条产线 queue：按 order 调用 enabled TimingRules

  Phase 5  ValidationPhase
           ValidationPipeline.validate(ctx, schedule)
           └─ 聚合 enabled ValidationRules 的 violations

  Phase 6  EnrichmentPhase
           changeoverMinutesBefore、KPI 输入字段等（只读衍生）

  Phase 7  BuildResult
           preview DTO + violations + simulationMode + recalculatedIds + appliedRules
```

### 4.2 核心接口（Java）

包建议：`com.plantops.scenario.planning.simulation`

```java
/** 一次 simulate 共享上下文 */
public record SimulationRuleContext(
    DetailSchedule schedule,
    DetailScheduleProblemFacts facts,
    Set<String> enabledRuleTypes,
    Map<String, JsonNode> ruleParams,
    SimulationMode mode,
    Set<String> seedOperationIds,
    LocalDate planningAnchorDate
) {}

/** 赋时：在同线队列扫描中贡献 cursor 增量或特殊处理 */
public interface TimingRule {
    String ruleTypeId();
    int order();
    boolean enabled(SimulationRuleContext ctx);
    /** 上一道 → 下一道 之间规则要求的分钟增量（如换型） */
    int gapBeforeNext(SimulationRuleContext ctx,
                      OperationAssignment previous,
                      OperationAssignment next,
                      ScheduleLine line);
    /** 可选：earliest 抬升、并行对同起同止等特殊逻辑 */
    default void contributeEarliestFloor(SimulationRuleContext ctx, OperationAssignment op) {}
    default void afterLineQueuePass(SimulationRuleContext ctx, ScheduleLine line) {}
}

/** 校验：与赋时解耦，共用 ruleTypeId / enabled */
public interface ValidationRule {
    String ruleTypeId();
    boolean enabled(SimulationRuleContext ctx);
    List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op);
}

/** 增量闭包扩展 */
public interface AffectedClosureRule {
    String ruleTypeId();
    boolean enabled(SimulationRuleContext ctx);
    void expand(SimulationRuleContext ctx,
                Map<String, OperationAssignment> byId,
                Set<String> affected,
                Deque<String> pending);
}

@ApplicationScoped
public class SimulationRuleRegistry {
    List<TimingRule> enabledTimingRules(SimulationRuleContext ctx);
    List<ValidationRule> enabledValidationRules(SimulationRuleContext ctx);
    List<AffectedClosureRule> enabledClosureRules(SimulationRuleContext ctx);
}
```

**启用逻辑：**

```text
enabled(rule) =
  BusinessRuleScopeService.isDetailScheduleEnabled(rule.ruleTypeId())
  AND SimulationProfile.isEnabled(rule.ruleTypeId())  // 可选场景覆盖
  AND rule.enabled(ctx)  // 规则自身前置条件
```

### 4.3 内置规则映射（Phase 1 拆分，零行为变化）

| ruleTypeId | TimingRule | ValidationRule | ClosureRule |
|------------|------------|----------------|-------------|
| `changeover` | `ChangeoverTimingRule` | — | — |
| `operation-transfer-time` | `RoutingChainTimingRule` | `RoutingPrecedenceValidationRule` | `RoutingSuccessorClosureRule` |
| `parallel-operations` | `ParallelPairTimingRule` | `ParallelPairValidationRule` | `ParallelMateClosureRule` |
| `continuous-production` | — | `ContinuousProductionValidationRule` | — |
| （契约/齐套，无独立 ruleType） | `ContractEarliestTimingRule` | `EarliestStartValidationRule` | — |
| （产线资源） | — | `ResourceMismatchValidationRule`, `LineOpenedValidationRule` | — |
| （齐套未排） | — | `UnassignedKittedValidationRule` | — |
| （同线后缀） | — | — | `SameLineSuffixClosureRule` |

`LineChainTimingUtil.applyAllStartTimes` 在 Phase 1 末尾变为 **委托** `DetailScheduleTimingKernel`；内核实现与现逻辑等价（含 16 轮迭代、`clampAssignedStartsToRoutingChain`）。

### 4.4 与 Timefold 路径统一

**原则：** Session 与求解后赋时共用 `DetailScheduleTimingKernel`（或共用 `TimingRule` 列表）。

| 路径 | 今天 | 目标 |
|------|------|------|
| Session simulate | `LineChainTimingUtil` | `TimingKernel` |
| Timefold 后 `assignStartTimes` | `LineChainTimingUtil` | 同上 |
| Shadow `OperationStartTimeCalculator` | 独立实现 | 委托 kernel 或共享 `gapBeforeNext` 计算 |

**验收：** 同一 schedule 队列顺序下，Session 赋时与 `assignStartTimes` 的 `startMinute` 一致（允许已有测试扩展断言）。

---

## 5. 配置模型：SimulationProfile

在 `BusinessRuleScope`（开/关）之上，增加**场景级推演配置**（可选，Phase 2）。

### 5.1 存储

- 表：`simulation_profile`（workspace 级，可挂 `planning_scenario_id` 或 `master_plan_version_id`）。
- 字段：`profile_id`, `name`, `layer`（`DETAIL_SCHEDULE`）, `config_json`, `active`, `updated_ts`。
- Session 创建时：加载 active profile（或请求指定 `simulationProfileId`）→ 与 ProblemFacts **一并快照**到 Session（避免推演中途主数据变更导致不一致）。

### 5.2 config_json 示例

```json
{
  "timing": {
    "maxRoutingIterations": 16,
    "rules": {
      "changeover": { "enabled": true },
      "operation-transfer-time": { "enabled": true },
      "parallel-operations": { "enabled": true }
    }
  },
  "incremental": {
    "rules": {
      "routing-successor": { "enabled": true },
      "parallel-mate": { "enabled": true },
      "same-line-suffix": { "enabled": true }
    }
  },
  "validation": {
    "blockConfirmOnHard": true,
    "earliestViolationLevel": "MEDIUM"
  }
}
```

### 5.3 API 扩展（Phase 2）

```text
POST /api/v1/planning/schedule-sessions/{id}/simulate
  body: {
    stepPatches?: [...],
    fullReschedule?: boolean,
    simulationProfileId?: string,
    ruleOverrides?: { "changeover": { "enabled": false } }  // 仅当前 Session，不持久化
  }

  response 附加:
    appliedRules: string[]
    simulationProfileId: string | null
```

---

## 6. 前端边界

| 后端 | 前端 |
|------|------|
| 赋时、校验、闭包、Profile | patch 构造、插位启发式 |
| `changeoverMinutesBefore` 等衍生字段 | 甘特着色、图例 |
| `violations`, `simulationMode` | 展示与确认发布提示 |
| `appliedRules`（Phase 2） | 诊断面板可选展示 |

**禁止：** 在前端复刻换型矩阵匹配或工艺链赋时（已用后端 `changeoverMinutesBefore` 修正过此类问题）。

---

## 7. 迁移计划

### Phase 1 — 管道 + 内置规则拆分（行为不变）

**交付：**

- 新增 `SimulationPipeline`、`SimulationRuleRegistry`、`DetailScheduleTimingKernel`。
- 从 `LineChainTimingUtil` / `ScheduleValidationService` / `DetailScheduleSimulationEngine` 抽出上表内置规则类。
- `DetailScheduleSimulationEngine` 变薄，仅调 Pipeline。
- 全量测试绿：`DetailScheduleSessionMutationTest`、`DetailScheduleSimulationEngineTest`、`DetailScheduleSessionServiceTest` 等。
- 更新 [detail-schedule-simulation-layer.md](../../detail-schedule-simulation-layer.md) §7–§9 为管道描述。

**不含：** SimulationProfile 表、UI、新规则类型。

### Phase 2 — SimulationProfile + API

**交付：**

- DB 迁移 + CRUD API（或挂现有 scenario 配置）。
- Session 创建/snapshot profile；simulate 支持 override。
- 响应 `appliedRules`；诊断/推演文档补充。

### Phase 3 — 扩展规则（按需）

**交付：**

- 新增 `TimingRule` / `ValidationRule` 实现 + 主数据（若有）。
- 示例候选：班次日历赋时、反馈冻结窗口、批次内连续排产策略。

### Phase 4 — Timefold Shadow 对齐（可与 Phase 1 并行验证）

**交付：**

- `OperationStartTimeCalculator` 委托 kernel。
- 回归：求解后赋时 vs Session simulate 一致。

---

## 8. 测试策略

| 层级 | 内容 |
|------|------|
| 单元 | 每个 `TimingRule` / `ValidationRule` 独立测试（换型 0/正数、并行对、工艺链违反分钟） |
| 集成 | `SimulationPipeline` 全链路：patch → simulate → violations |
| 回归 | 现有 Session 测试套件全部通过 |
| 对照 | 可选：`LineChainTimingUtil` 旧实现 vs Kernel 同输入输出 diff（Phase 1 过渡期） |
| 配置 | Profile 启用/禁用某 ruleType 后 violations 条数变化 |

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 重构引入赋时回归 | Phase 1 对照测试；不合并 Phase 2 直到 Phase 1 绿 |
| 双路径不一致 | Phase 4 明确 kernel 统一；CI 加对照用例 |
| Profile 与 ProblemFacts 快照不一致 | Session 创建时一次快照；文档说明 refresh 需 recreate Session |
| 规则顺序影响结果 | `TimingRule.order()` 文档化；内置规则 order 与现 `LineChainTimingUtil` 一致 |
| 过度设计 | 禁止 Phase 1 上 DSL；YAGNI |

---

## 10. 开放问题（审阅时请确认）

1. **SimulationProfile 挂载点**：挂 `planning_scenario`、独立 profile 表、还是先只用 `BusinessRuleScope` 不加 Profile（Phase 2 再做）？
2. **确认发布策略**：HARD violation 是否硬拦 `confirm`（今天仅 UI 警示）？是否纳入 Profile `blockConfirmOnHard`？
3. **增量闭包可关**：是否需要「仅重算种子工序 id、不扩工艺后继」的场景？若需要，Phase 2 用 ClosureRule 开关实现。
4. **优先级**：Phase 1 与 Phase 4（Timefold 对齐）是否必须同里程碑，还是 Phase 4 可后续？

---

## 11. 审阅检查清单

- [ ] 管道阶段划分是否清晰、与现有 `simulate` 一致？
- [ ] `TimingRule` / `ValidationRule` 接口粒度是否合适？
- [ ] 内置规则映射是否覆盖现有 9 种 violation + 换型/工艺链/并行？
- [ ] SimulationProfile 范围（Phase 2）是否同意延后？
- [ ] 非目标是否接受（无 DSL、无真局部赋时）？
- [ ] 开放问题 1–4 的选择？

---

## 12. 批准后下一步

1. 审阅人确认本规格（或标注修改意见）。
2. 使用 **writing-plans** 技能编写 `docs/superpowers/plans/2026-06-02-detail-schedule-simulation-rules.md`（Phase 1 任务分解）。
3. Phase 1 在独立分支/worktree 执行，TDD + 现有测试回归。
