# Plant Operation Plan (Timefold 2 + Quarkus)

单工厂运营计划 APS，覆盖场景 S01–S07。系统以工作区数据集为边界，将主数据、业务规则、主计划、生产排程、执行反馈与 KPI 串成可运行的计划链路；核心优化由 Timefold 驱动（S04 主计划、S05 详细排程）。

## 技术栈

- Java 21
- Quarkus 3.17.5
- Timefold Solver 2.0.0 (Community)
- H2 + Flyway

## 启动

本项目已包含 **Maven Wrapper**，无需全局安装 `mvn`。在 PowerShell 或 CMD 中：

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
.\mvnw.cmd quarkus:dev
```

Linux/macOS：

```bash
cd plant-operation-plan
./mvnw quarkus:dev
```

若已安装 Maven 并加入 PATH，也可使用 `mvn quarkus:dev`。

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/q/swagger-ui

## 业务前端 (React)

多页面 React 应用，含 **主计划** 与 **详细排程** 甘特图（`gantt-task-react`）。

### 开发模式（推荐）

两个终端：

```powershell
# 终端 1：后端
cd d:\AILab\PlantOperationPlan\plant-operation-plan
.\mvnw.cmd quarkus:dev

# 终端 2：前端（代理 /api → 8080）
cd d:\AILab\PlantOperationPlan\plant-operation-plan\frontend
.\install.cmd
.\dev.cmd
```

浏览器打开 http://localhost:5173 ，侧栏按「数据管理 / 业务规则 / 主计划 / 生产排程」分组；顶部可切换或管理工作区数据集。

**PowerShell 报「禁止运行脚本」时**：不要用 `npm`，改用 `npm.cmd` 或上面的 `.cmd` 脚本，例如：

```powershell
npm.cmd run dev
# 或
.\dev.cmd
```

若希望 PowerShell 里直接 `npm` 可用，可对当前用户放宽策略（可选）：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### 生产一体部署

```powershell
cd frontend
.\build.cmd
cd ..
.\mvnw.cmd quarkus:dev
```

构建产物输出到 `src/main/resources/META-INF/resources`，访问 http://localhost:8080/#/ 使用业务界面。

打包 JAR：`.\mvnw.cmd package -DskipTests`，运行 `java -jar target\quarkus-app\quarkus-run.jar`。

### Docker 部署

```powershell
docker compose up -d --build
```

访问 http://localhost:8080/#/ 。详细说明见 [docs/docker-deploy.md](docs/docker-deploy.md)。

| 路由 | 模块 |
|------|------|
| `/#/` | 工作台与关键指标 |
| `/#/workspaces` | 工作区 / 数据集管理 |
| `/#/master-data` | 主数据：产品、BOM、工艺、资源、日历、规则基础数据 |
| `/#/business-data` | 业务数据：销售订单、库存、工单等 |
| `/#/factory-calendar` | 工厂日历与资源工作时间 |
| `/#/business-rules/production` | 业务规则维护（生产、产能、物料、人力、需求分类） |
| `/#/master-plan/parameters` | 主计划参数 |
| `/#/master-plan/objectives` | 主计划策略与优化目标 |
| `/#/master-plan/plan-run` | 主计划流水线运行 |
| `/#/master-plan/analysis/demand` | S01 需求满足与满足链 |
| `/#/master-plan/analysis/material` | S02 物料需求 / 齐套 |
| `/#/master-plan/analysis/capacity` | S03 产能平衡 |
| `/#/master-plan/analysis/work-orders` | S04 工单与计划结果 |
| `/#/master-plan/analysis/diagnostics` | 主计划 / 排程推演诊断 |
| `/#/master-plan/analysis/order-chain` | 订单计划链预览 |
| `/#/master-plan/scenario-comparison` | 主计划场景对比 |
| `/#/scheduling/parameters` | 生产排程参数 |
| `/#/scheduling/pending-work-orders` | 待排工单池 |
| `/#/scheduling/batch-plan` | 生产批次拆分与批次计划 |
| `/#/scheduling/kitting` | 批次齐套 |
| `/#/scheduling/detail-schedule` | S05 详细排程 Session 推演 / 优化 / 确认 |
| `/#/scheduling/version-comparison` | 排程版本对比 |
| `/#/demand-tracking` | S07 需求交付跟踪与 KPI |

旧路由（如 `/#/demand`、`/#/pipeline`、`/#/detail-schedule`）仍由前端重定向到新路径。

## 示例调用

```bash
# 需求满足
curl http://localhost:8080/api/v1/demand/demand-pool

# 工作区数据集
curl http://localhost:8080/api/v1/workspaces

# 主计划物料齐套 / MRP
curl -X POST http://localhost:8080/api/v1/kitting/compute
curl -X POST http://localhost:8080/api/v1/material-requirements/compute

# 主计划求解
curl -X POST http://localhost:8080/api/v1/planning/master-plan/solve

# 详细排程 Timefold 求解（可带 masterPlanVersionId）
curl -X POST "http://localhost:8080/api/v1/planning/detail-schedule/solve"

# 详细排程 Session：创建 → 推演 → 确认
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions \
  -H "Content-Type: application/json" \
  -d '{"masterPlanVersionId":"{masterPlanVersionId}","simulationProfileId":"SP-DEFAULT"}'
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions/{sessionId}/simulate \
  -H "Content-Type: application/json" \
  -d '{"fullReschedule":false,"simulationProfileId":"SP-DEFAULT","feedbackCutoff":"2026-06-02"}'
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions/{sessionId}/confirm

# 生产批次与批次齐套
curl http://localhost:8080/api/v1/scheduling/batches/work-orders
curl -X POST http://localhost:8080/api/v1/scheduling/batches/split/auto-all
curl -X POST http://localhost:8080/api/v1/scheduling/batches/kitting/compute

# 车间执行任务
curl http://localhost:8080/api/v1/production-tasks

# 主计划流水线；需要连带详细排程求解时显式打开 includeDetailSchedule
curl -X POST "http://localhost:8080/api/v1/planning/run-full-pipeline?includeDetailSchedule=true"

# KPI
curl http://localhost:8080/api/v1/kpi/report
```

## 场景映射

| 场景 | 关键服务 / API | 求解或推演方式 |
|------|---------------|----------------|
| S01 需求满足 | `DemandService`、`/api/v1/demand/*` | 规则 + 满足链追溯 |
| S02 物料齐套 / MRP | `KittingService`、`MaterialRequirementResource` | BOM 展开、库存扣减、缺料分析 |
| S03 产能平衡 | `CapacityService` | 资源×班次负荷分析 |
| S04 主计划 | `MasterPlanService`、`PlanningResource` | Timefold 2.0 主计划求解 + 场景对比 |
| S05 生产排程 | `DetailScheduleService`、`DetailScheduleSessionService`、`SchedulingBatchResource` | Timefold 排程、批次拆分、Session 推演/优化/确认 |
| S06 执行闭环 | `ProductionTaskService`、`ScheduleFeedbackService` | 排程确认发布 `production_task`，执行反馈冻结 RUNNING/COMPLETED 工序 |
| S07 KPI | `KpiService`、场景/版本对比 API | 指标汇总与版本对比 |

## 测试

```powershell
.\mvnw.cmd test
```

## 演示数据（标准 Demo Excel）

多层级 BOM 与工单树来自 `演示需求-数据准备.xlsx`，由脚本转换：

```powershell
pip install pandas openpyxl
python -X utf8 tools/parse_demo_excel.py
```

详见 [tools/README.md](tools/README.md)。重启 `quarkus:dev` 后生效（H2 内存库需空库启动）。

## 文档

| 文档 | 说明 |
|------|------|
| **[docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md)** | **完整项目文档**（业务蓝图、功能设计、技术方案、部署） |
| [docs/architecture.md](docs/architecture.md) | 架构摘要 |
| [docs/aps-planning-layer.md](docs/aps-planning-layer.md) | 主计划 / 详细排程分层推演与 Timefold 边界 |
| [docs/detail-schedule-simulation-layer.md](docs/detail-schedule-simulation-layer.md) | 详细排程 Session 推演、规则、确认发布 Runbook |
| [docs/master-plan-bom-routing.md](docs/master-plan-bom-routing.md) | BOM、工艺路线、MRP 与工单链路 |
| [docs/timefold-2-upgrade.md](docs/timefold-2-upgrade.md) | Timefold 2.0 升级说明 |
| [docs/docker-deploy.md](docs/docker-deploy.md) | Docker 部署说明 |
| 工作区根目录 `工厂计划*.md` | 业务方法论文档（场景卡片） |
