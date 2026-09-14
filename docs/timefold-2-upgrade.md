# Timefold Solver 2.0 升级说明

本项目已从 **Timefold 1.15.0** 升级至 **2.0.0**（2026-05）。

## 环境要求

- **Java 21+**（`pom.xml` 中 `maven.compiler.release=21`）
- Maven 构建

## 一键迁移命令（其它分支/项目复用）

在项目根目录 `plant-operation-plan` 下执行（Linux/macOS 或 CI）：

```bash
bash ./mvnw org.openrewrite.maven:rewrite-maven-plugin:6.28.1:run \
  "-Drewrite.recipeArtifactCoordinates=ai.timefold.solver:timefold-solver-migration:2.0.0" \
  "-Drewrite.activeRecipes=ai.timefold.solver.migration.ToLatest"
```

Windows PowerShell 使用 `.\mvnw.cmd`，其余参数相同（单行）。

迁移后仍需手动处理：

- `VariableListener` → `@ShadowSources` + `@ShadowVariable(supplierName=...)`
- `@PlanningScore` 字段保留 **public `getScore()`**（2.0 校验要求）
- `ScoreExplanation` → `ScoreAnalysis`（`SolutionManager.analyze()`）
- `ConstraintCollectors.sum` 返回 `long`

## 本项目主要变更

| 区域 | 说明 |
|------|------|
| 依赖 | `timefold.version=2.0.0`；移除 `timefold-solver-test` |
| 细排程 | Planning List Variable + declarative shadow（`startMinuteSupplier`） |
| 主/细排 | `HardSoftScore` 新包名；`SolverManager<Solution>` 单泛型 |
| 得分解释 | `PlanningScoreExplainService` 使用 `analyze()` |
| 工序衔接 | V45 迁移：min/max 流转、衔接模式 |

## 验证

```bash
bash ./mvnw compile
bash ./mvnw test
```

集成测试 `PlantOperationPlanResourceTest.fullPipeline_succeeds` 调用默认主计划流水线（S01/S02/S03 准备 + S04 求解）并断言返回主计划版本；需要覆盖 S05 时显式传 `includeDetailSchedule=true` 或走 Session 流程。

## 参考

- [Upgrade from 1.x to 2.0](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1)
- [Variable listeners → custom shadow variables](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/migration-guides/variable-listeners-to-custom-shadow-variables)
