# Workspace 多数据集隔离设计（方案 A）

## 目标

- 引入 **Workspace**：一个 workspace = 一套完整、互不可见的业务数据集。
- 同一应用、同一数据库；通过 **`workspace_id` 行级隔离**。
- 切换 workspace 后，主数据、工单、主计划、排程、策略、运行记录等全部只看到当前空间的数据。

## 非目标（首期）

- 多用户 / 权限 / 登录（workspace 仅作数据集边界，不做 RBAC）。
- 跨 workspace 数据复制（可后续加「从模板克隆」）。

---

## 核心模型

### 表 `workspace`

| 字段 | 说明 |
|------|------|
| `workspace_id` | 主键，如 `default`、`dunan-lite`（slug，URL/API 友好） |
| `name` | 显示名，如「盾安演示」 |
| `description` | 可选说明 |
| `created_at` | 创建时间 |
| `is_default` | 是否默认空间（全局至多一个 true） |

### 业务表

所有业务表增加：

```sql
workspace_id VARCHAR(64) NOT NULL REFERENCES workspace(workspace_id)
```

涉及表（约 20 张）：`sales_order_line`、`bom_component`、`inventory`、`production_resource`、`product_resource`、`resource_calendar`、`shift_headcount`、`production_line`、`changeover_matrix`、`work_order`、`plan_version`、`master_plan_allocation`、`line_opening_decision`、`detail_schedule_operation`、`kitting_result`、`shortage_recommendation`、`planning_event`、`plan_dispatch`、`system_parameter`、`planning_pipeline_run`。

### 唯一约束调整

原全局唯一改为 **(workspace_id, …)** 组合唯一，例如：

- `sales_order_line`: `(workspace_id, sales_order_no, sales_order_line_no)`
- `production_resource`: `(workspace_id, resource_id)`
- `work_order`: `(workspace_id, work_order_no)`
- `plan_version`: `(workspace_id, plan_version_id)`
- `system_parameter`: `(workspace_id, param_id)`（策略 JSON 按空间隔离）
- `planning_pipeline_run`: `(workspace_id, run_id)`

不同 workspace 可使用相同订单号、资源号、工单号，互不影响。

---

## 运行时：当前 Workspace 上下文

### 传递方式

- HTTP Header：**`X-Workspace-Id: <workspace_id>`**（推荐，与 REST 路径解耦）。
- 未传或非法 id → 使用 **默认 workspace**（`is_default=true` 或 `default`）。

### 后端

1. **`WorkspaceContext`**（`@RequestScoped`）：`getWorkspaceId()` / `requireWorkspaceId()`。
2. **`WorkspaceRequestFilter`**（JAX-RS）：解析 Header，校验 workspace 存在，写入 Context；非法返回 400。
3. **`WorkspaceAwareRepository` 辅助**或 Panache 基类：
   - `listForWorkspace()` → `list("workspaceId", ctx.getWorkspaceId())`
   - 新建实体时自动 `entity.workspaceId = ctx.getWorkspaceId()`
4. **Hibernate `@Filter`（可选二期）**：防漏网 `listAll()`；首期以改查询 + Code review 为主。

### 前端

1. **`WorkspaceProvider`**：当前 workspaceId、列表、切换方法。
2. **顶栏 Workspace 下拉框**（Layout）：切换后刷新页面数据。
3. **`localStorage`**：`plantops.workspaceId` 记住上次选择。
4. **`api/client.ts`**：所有 `fetch` / `request` 自动带 `X-Workspace-Id`。

---

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/workspaces` | 列表 |
| POST | `/api/v1/workspaces` | 创建（body: id, name, description?） |
| GET | `/api/v1/workspaces/{id}` | 详情 |
| DELETE | `/api/v1/workspaces/{id}` | 删除（禁止删 default；级联删该空间全部业务数据） |

现有 `/api/v1/**` 行为不变，但必须带 workspace Header（浏览器 UI 自动带）。

管理接口：

- `POST /api/v1/admin/reload-sample-data?dataset=...` → 仅作用于 **当前 workspace**。
- 计划运行、主计划、工单生成等流水线天然限定在当前 workspace。

---

## 数据迁移（Flyway V11）

1. 创建 `workspace` 表。
2. 插入默认行：`workspace_id='default'`, `name='默认数据集'`, `is_default=true`。
3. 各业务表 `ADD COLUMN workspace_id`，默认填 `'default'`，再 `SET NOT NULL`。
4. 删除旧唯一索引，创建 `(workspace_id, …)` 新唯一索引。

现有 `./data/plantops.mv.db` 数据归入 **default** workspace，升级后行为与现在一致。

---

## 演示 / 盾安数据

- 新建 workspace：`dunan-lite`，名称「盾安 Lite」。
- 在该空间内调用 `reload-sample-data?dataset=dunan-lite`。
- 与 `default`（马勒 demo）完全隔离。

`application.properties` 的 `plantops.sample-data.resource` 仅作 **新 workspace 首次初始化** 可选默认，不再全局灌库；启动时只对 **空 workspace** 或显式 reload 灌数。

---

## 校验与隔离保证

- `MasterDataValidationService.validateAll()`：仅校验当前 workspace 数据。
- `PlanningOrchestrator`：blocked 订单、工单重建、主计划均在当前 workspace。
- 场景对比、需求池、产能分析：查询均带 `workspace_id`。

---

## 实现分期（建议）

| 阶段 | 内容 |
|------|------|
| **P1** | Flyway V11、`workspace` CRUD、Context + Filter、迁移 default 数据 |
| **P2** | 所有 Entity/Service/Resource 查询与写入接 workspace |
| **P3** | 前端 Workspace 选择器 + API Header |
| **P4** | Workspace 管理页（新建/删除/说明）、删除空间二次确认 |

---

## 风险与对策

| 风险 | 对策 |
|------|------|
| 漏改 `listAll()` 导致串数据 | P2 清单逐表改；关键路径集成测试（两空间各建订单，切换 Header 互不可见） |
| 唯一约束迁移失败 | 先 backfill default，再建组合唯一索引 |
| 前端忘带 Header | client 统一封装，禁止裸 fetch |

---

## 验收标准

1. 创建 workspace A、B，在 A 导入盾安、B 导入马勒，互不可见。
2. 在 A 跑计划运行，B 的工单/主计划版本不变。
3. 重启服务后，workspace 选择与数据仍在。
4. 删除 workspace B 后，A 不受影响。
