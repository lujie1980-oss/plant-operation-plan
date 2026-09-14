# Plant Operation Plan (Timefold + Quarkus)

单工厂运营计划 APS，覆盖场景 S01–S07，核心优化由 Timefold 驱动（S04 主计划、S05 详细排程）。

## 技术栈

- Java 21
- Quarkus 3.17
- Timefold Solver 2.0 (Community)
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
bash ./mvnw quarkus:dev
```

若已安装 Maven 并加入 PATH，也可使用 `mvn quarkus:dev`。
若本地 checkout 中 `mvnw` 没有执行位，Linux/macOS 可直接使用 `bash ./mvnw ...`，无需修改文件权限。

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

浏览器打开 http://localhost:5173 ，侧栏可进入 S01–S07 各页面。

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

打包 JAR：`.\mvnw.cmd package -DskipTests`（Linux/macOS：`bash ./mvnw package -DskipTests`），运行 `java -jar target\quarkus-app\quarkus-run.jar`。

### Docker 部署

```powershell
docker compose up -d --build
```

访问 http://localhost:8080/#/ 。详细说明见 [docs/docker-deploy.md](docs/docker-deploy.md)。

| 路由 | 场景 |
|------|------|
| `/#/` | 首页工作台 |
| `/#/master-data`、`/#/business-data`、`/#/factory-calendar`、`/#/workspaces` | 数据、日历与工作区管理 |
| `/#/business-rules/production`、`/#/business-rules/capacity`、`/#/business-rules/material`、`/#/business-rules/labor`、`/#/business-rules/demand` | 业务规则 |
| `/#/master-plan/parameters`、`/#/master-plan/objectives`、`/#/master-plan/plan-run` | 主计划配置与运行 |
| `/#/master-plan/analysis/demand` | S01 需求满足（KPI + 订单列表 + 满足链甘特图） |
| `/#/master-plan/analysis/material` | S02 物料需求 |
| `/#/master-plan/analysis/capacity` | S03 产能平衡 |
| `/#/master-plan/analysis/work-orders` | 生产工单 |
| `/#/master-plan/analysis/diagnostics`、`/#/master-plan/analysis/order-chain` | 推演诊断与订单推演 |
| `/#/master-plan/scenario-comparison` | 主计划场景对比 |
| `/#/scheduling/parameters`、`/#/scheduling/pending-work-orders`、`/#/scheduling/batch-plan`、`/#/scheduling/kitting` | 生产排程准备 |
| `/#/scheduling/detail-schedule`、`/#/scheduling/version-comparison` | S05 生产排程与版本对比 |
| `/#/demand-tracking` | S07 需求跟踪/KPI |

旧路径（如 `/#/demand`、`/#/detail-schedule`、`/#/pipeline`）会自动重定向到当前分组路由。

## 示例调用

```bash
# 需求满足
curl http://localhost:8080/api/v1/demand/demand-pool

# 齐套
curl -X POST http://localhost:8080/api/v1/kitting/compute

# 主计划求解
curl -X POST http://localhost:8080/api/v1/planning/master-plan/solve

# 详细排程（可带 masterPlanVersionId）
curl -X POST "http://localhost:8080/api/v1/planning/detail-schedule/solve"

# 主计划流水线（默认执行到 S04 主计划；不自动排程/下发）
curl -X POST http://localhost:8080/api/v1/planning/run-full-pipeline

# 需要连带详细排程时显式打开；排程发布生产任务走 Session confirm
curl -X POST "http://localhost:8080/api/v1/planning/run-full-pipeline?includeDetailSchedule=true"

# 创建详细排程工作副本并推演/确认
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions \
  -H "Content-Type: application/json" \
  -d '{"masterPlanVersionId":"MP-...","seedInitialQueues":true}'
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions/{sessionId}/simulate \
  -H "Content-Type: application/json" \
  -d '{"fullReschedule":true}'
curl -X POST http://localhost:8080/api/v1/planning/schedule-sessions/{sessionId}/confirm

# KPI
curl http://localhost:8080/api/v1/kpi/report
```

## 场景映射

| 场景 | 服务 | 求解 |
|------|------|------|
| S01 需求满足 | DemandService | 规则 |
| S02 齐套 | KittingService | 规则 |
| S03 产能平衡 | CapacityService | 利用率甘特+区间工单 |
| S04 主计划 | MasterPlanService | Timefold |
| S05 排程 | DetailScheduleService | Timefold |
| S06 执行闭环 | ExecutionService | 事件+R0–R3 |
| S07 KPI | KpiService | 指标汇总 |

## 测试

```powershell
.\mvnw.cmd test
```

Linux/macOS 或 CI 中可用：

```bash
bash ./mvnw test
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
| [docs/aps-planning-layer.md](docs/aps-planning-layer.md) | S04/S05 推演层与 Timefold 边界 |
| [docs/detail-schedule-simulation-layer.md](docs/detail-schedule-simulation-layer.md) | 详细排程 Session、增量推演、规则配置 |
| [docs/timefold-2-upgrade.md](docs/timefold-2-upgrade.md) | Timefold Solver 2.0 升级与验证要点 |
| 工作区根目录 `工厂计划*.md` | 业务方法论文档（场景卡片） |
