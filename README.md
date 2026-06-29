# Plant Operation Plan (Timefold + Quarkus)

单工厂运营计划 APS，覆盖场景 S01–S07，核心优化由 Timefold 驱动（S04 主计划、S05 详细排程）。

## 技术栈

- Java 21
- Quarkus 3.17.5
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

浏览器打开 http://localhost:5173 ，侧栏可进入数据管理、主计划、生产排程等页面。

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

| 路由 | 场景 |
|------|------|
| `/#/` | 工作台 |
| `/#/master-data` | 主数据 |
| `/#/business-data` | 业务数据 |
| `/#/workspaces` | 数据集 / 工作区管理 |
| `/#/factory-calendar` | 工厂日历 |
| `/#/business-rules/{categoryId}` | 生产、产能、物料、人力、需求规则 |
| `/#/master-plan/parameters` | 主计划参数 |
| `/#/master-plan/objectives` | 主计划优化目标 |
| `/#/master-plan/plan-run` | 全链路编排 / 计划运行 |
| `/#/master-plan/analysis/demand` | S01 需求满足 |
| `/#/master-plan/analysis/capacity` | S03 产能平衡 |
| `/#/master-plan/analysis/material` | S02 / MRP 物料需求 |
| `/#/master-plan/analysis/work-orders` | 生产工单 / 下发 |
| `/#/master-plan/analysis/diagnostics` | 推演诊断 |
| `/#/master-plan/analysis/order-chain` | 订单推演 |
| `/#/master-plan/scenario-comparison` | 主计划场景对比 |
| `/#/scheduling/parameters` | 生产排程参数 |
| `/#/scheduling/pending-work-orders` | S05 待排工单 / 批次 |
| `/#/scheduling/batch-plan` | 批次计划 |
| `/#/scheduling/kitting` | 排程齐套 |
| `/#/scheduling/detail-schedule` | S05 详细排程 |
| `/#/scheduling/version-comparison` | 排程版本对比 |
| `/#/demand-tracking` | 需求跟踪 / KPI |

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

# 批次计划：查看可拆批工单
curl http://localhost:8080/api/v1/scheduling/batches/work-orders

# 批次计划：按当前策略自动拆批（需先将 batch_split_mode 设为 FIXED_QTY / KITTING / AUTO）
curl -X POST http://localhost:8080/api/v1/scheduling/batches/split/auto \
  -H "Content-Type: application/json" \
  -d '{"workOrderNo":"WO-001","quantity":null}'

# 全链路 S01→S07
curl -X POST http://localhost:8080/api/v1/planning/run-full-pipeline

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
| [docs/aps-planning-layer.md](docs/aps-planning-layer.md) | S04/S05 推演层与 Timefold 选优边界 |
| [docs/detail-schedule-simulation-layer.md](docs/detail-schedule-simulation-layer.md) | 详细排程 Session、仿真、校验与 Timefold 边界 |
| [docs/batch-scheduling.md](docs/batch-scheduling.md) | 批次计划、拆批策略、S05 候选与排程 API |
| [docs/master-plan-bom-routing.md](docs/master-plan-bom-routing.md) | 主计划、BOM、工艺路线与工单生成 |
| [docs/timefold-2-upgrade.md](docs/timefold-2-upgrade.md) | Timefold Solver 2.0 升级说明 |
| [docs/docker-deploy.md](docs/docker-deploy.md) | Docker 部署说明 |
| 工作区根目录 `工厂计划*.md` | 业务方法论文档（场景卡片） |
