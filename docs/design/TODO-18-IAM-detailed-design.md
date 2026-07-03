# TODO-18 IAM 详细实现设计

> **基准规范：** [§18 IAM](../sdd/volumes/platform/18-19-workspace-platform.md#s18-iam) · [§3 SCN-T06](../sdd/core/03-scenarios.md) · [§6 API-IAM-*](../sdd/core/06-api-contracts.md#api-iam-01-当前用户)  
> **架构决策：** [ADR-13](../sdd/core/10-decisions-risks.md)  
> **文档状态：** 设计草案 · 不包含代码  
> **日期：** 2026-06-24

---

## 1. 设计范围（四层控制）

```
认证（你是谁？） → 成员资格（你能进哪个 WS？） → 模块开关（WS 启用了哪些能力？） → 操作权限（你能读还是写？）
```

当前现状如图：

| 层 | 现状 | 缺口 |
|----|------|------|
| **认证** | 无。`WorkspaceRequestFilter` 只读 `X-Workspace-Id` 头，不验证用户身份 | 从零搭建：`app_user` 表、登录 API、JWT、前端登录页 |
| **成员资格** | 无。任何请求带任意 `X-Workspace-Id` 即可访问 | `workspace_member` 表、Filter 校验 |
| **模块开关** | `useEnabledModules` 返回硬编码 `DEFAULT_ENABLED_MODULES`；后端零控制 | `workspace_enabled_module` 表、API 层 403 |
| **操作权限** | 无。所有 API 等同全权限 | `workspace_member_module` 表、VIEW/EDIT 判定 |

---

## 2. 实施阶段（M0→M4）

遵循 §18.11 的四阶段迁移路径，每阶段可独立上线。

### M0: 开发模式（已存在，保留）

- 前端注入 `dev-mode` 固定用户，跳过登录
- 后端 `plantops.security.dev-mode=true` 注入硬编码 `CurrentUser（userId="dev", isSuperAdmin=true）`
- **dev 用户自动拥有 demo workspace（jinghua, te, slitting-demo）**——由 WorkspaceSeedService 创建，dev 为 OWNER
- 目标：零回归，现有 demo 功能不受影响

### M1: 表结构 + Filter + 用户自建 WS（本次设计主攻）

**核心原则：不做 default workspace。** 用户登录后 workspace 列表为空，须先创建 workspace 才能进入系统。M0 的 demo workspace 仅在 dev 用户名下可见。

- **后端 DB：**
  - 新建 Flyway 迁移 V63：`app_user`、扩 `workspace` 列（`owner_user_id`, `workspace_type`）、`workspace_member`、`workspace_enabled_module`、`workspace_enabled_adapter`、`iam_audit_log`
  - 种子数据：仅 `app_user` 插入 `dev`（is_super_admin=true）
  - **不**自动创建 `workspace_member` 行——demo WS 仍由 `WorkspaceSeedService` 负责，但需从 `app_user` 查 dev 的 user_id 后插入 member
  - **不**自动创建 `default` workspace
- **后端 Filter 链：**
  - 新增 `AuthenticationFilter`（priority=100）：dev-mode 注入 dev 到 `SecurityContext`；prod 解析 JWT
  - 扩展 `WorkspaceRequestFilter`（priority=200）：
    - 无 `X-Workspace-Id` 且用户 workspace 列表为空 → **放行**（用户需要创建第一个 WS，不能拦在门外）
    - 有 `X-Workspace-Id` → 查 `workspace_member(wsId, userId)`；不存在 → 403
    - dev-mode：跳过 member 检查
  - 新增 `AuthorizationFilter`（priority=300）：M1 空壳，M2 起校验模块开关
- **后端 API：**
  - `GET /api/v1/iam/me`：返回 `CurrentUserDto { userId, displayName, isSuperAdmin, hasWorkspaces, workspaces[] }`。`hasWorkspaces=false` 时 `workspaces=[]`
  - `GET /api/v1/iam/workspaces`：同上，按 user 过滤 member 表
  - `POST /api/v1/workspaces`（扩展）：创建 WS 时**三合一**——`workspace` 行 + `workspace_member`(OWNER) + `workspace_enabled_module`(默认全开)
- **前端：**
  - 新 `AuthContext`：`useAuth()` → `{ currentUser, workspaces, enabledModules, isLoading, hasWorkspaces }`
  - `AuthContext` 挂载时调 `GET /api/v1/iam/me`
  - `hasWorkspaces === false` → 显示 **"创建 Workspace" 页面**（替代侧栏/顶栏）
  - `hasWorkspaces === true` + 无选中 WS → 自动选中第一个
  - `useEnabledModules` 从 `AuthContext.enabledModules[workspaceId]` 取值
  - `WorkspaceSelector` 从 `AuthContext.workspaces` 取列表（替代全量 `/api/v1/workspaces`）
  - 侧栏过滤：`filterNavGroups(enabledModules)`
  - `WorkspaceProvider` 不再自己调 API，改为从 `AuthContext` 接收列表

### M2: 模块开关 + 侧栏过滤

- **后端：**
  - `workspace_enabled_module` 表启用
  - `AuthorizationFilter` 开放 MOD 开关检查：关闭的模块 → API 403 `MODULE_DISABLED`
  - `API-IAM-03`（PUT workspace modules）对接前端设置页
- **前端：**
  - Workspace 设置页 `/workspaces/{id}/settings`：checkbox 列表（来自 `workspace-modules.yaml` + `integration-adapters.yaml`）
  - 侧栏实时响应模块开关变更

### M3: 登录页 + 成员矩阵 + Super Admin

- **后端：**
  - `POST /api/v1/auth/login`：密码验证 → JWT
  - `RegistrationService`：`POST /api/v1/auth/register`（可选，由 admin 开关）
  - `workspace_member_module` 启用（VIEW/EDIT 判定）
  - Super Admin UI API：`/api/v1/admin/users`、`/api/v1/admin/workspaces`
- **前端：**
  - `LoginPage`：用户名/密码 → JWT 存 localStorage
  - `AuthContext` 从 JWT 解析 `/iam/me` 数据
  - `axios/fetch` 拦截器：自动附加 `Authorization: Bearer <token>` + `X-Workspace-Id`
  - Super Admin 页面：用户管理、Workspace 管理

### M4: 生产 IdP + dev-mode 关闭

- 替换本地密码为 OIDC（Keycloak/Auth0）
- 生产环境关闭 `plantops.security.dev-mode`
- CI 加安全扫描

> **本次设计聚焦 M1**，M2–M4 仅勾勒关键里程碑。

---

## 3. 数据模型详细设计

### 3.1 ER 图

```
app_user ──< workspace_member >── workspace
                │
                ├──< workspace_member_module >── workspace_module（逻辑表，见 workspace-modules.yaml）
                │
workspace ──< workspace_enabled_module >── workspace_module
               │
               └──< workspace_enabled_adapter >── integration_adapter（逻辑表）
```

### 3.2 表定义（M1）

#### `app_user`

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `user_id` | VARCHAR(50) | PK | 如 `dev`、`zhangsan` |
| `login_name` | VARCHAR(100) | UK, NOT NULL | 登录名 |
| `display_name` | VARCHAR(200) | NOT NULL | 展示名 |
| `password_hash` | VARCHAR(200) | NULL（M1） | bcrypt；M3 起使用 |
| `is_super_admin` | BOOLEAN | DEFAULT false | 平台超管标记 |
| `status` | VARCHAR(20) | DEFAULT 'ACTIVE' | ACTIVE / DISABLED |
| `last_login_at` | TIMESTAMP | NULL | — |
| `created_at` | TIMESTAMP | NOT NULL | — |

**M1 种子数据：** `INSERT INTO app_user (user_id, login_name, display_name, is_super_admin, status, created_at) VALUES ('dev', 'dev', '开发用户', true, 'ACTIVE', NOW())`

#### `workspace`（扩展列）

| 新增列 | 类型 | 说明 |
|--------|------|------|
| `owner_user_id` | VARCHAR(50) | FK → app_user.user_id |
| `workspace_type` | VARCHAR(20) | `PERSONAL` / `SHARED`，默认 `SHARED` |

**M1 处理：** Flyway 扩列，不强制 FK（M3 再加约束）。现有 demo WS（`jinghua`/`te`/`slitting-demo`）的 `owner_user_id` 设为 `dev`，`workspace_type=SHARED`。
**不做 default workspace。** 用户自行创建，创建时自动设 `owner_user_id` 为当前用户。

#### `workspace_member`

| 列 | 类型 | 约束 |
|----|------|------|
| `workspace_id` | VARCHAR(50) | PK, FK → workspace |
| `user_id` | VARCHAR(50) | PK, FK → app_user |
| `role` | VARCHAR(20) | `OWNER` / `WS_ADMIN` / `MEMBER` |

**唯一约束：** `(workspace_id, user_id)`  
**M1 种子：** 仅 `WorkspaceSeedService` 的 demo WS 插入 `(wsId, 'dev', 'OWNER')`。其他用户自行创建 WS 时自动加入。
**不做全用户全 WS 的盲目插入。**

#### `workspace_enabled_module`

| 列 | 类型 | 约束 |
|----|------|------|
| `workspace_id` | VARCHAR(50) | PK, FK → workspace |
| `module_id` | VARCHAR(20) | PK（MOD-DI/OCP/SCH/SLT/CAL） |
| `enabled` | BOOLEAN | DEFAULT true |

**M1 行为：** Workspace 创建时（`POST /api/v1/workspaces`）自动插入默认行：MOD-DI ✓、MOD-OCP ✓、MOD-SCH ✓、MOD-CAL ✓、MOD-SLT ✗。不盲目对所有 WS 批量插入。

#### `workspace_enabled_adapter`

| 列 | 类型 | 约束 |
|----|------|------|
| `workspace_id` | VARCHAR(50) | PK, FK |
| `adapter_id` | VARCHAR(30) | PK（ADP-ERP-SAP/MES/EXCEL） |
| `enabled` | BOOLEAN | DEFAULT false |

**M1 行为：** Workspace 创建时默认启用 ADP-EXCEL ✓。

#### `workspace_member_module`（M3 启用）

| 列 | 类型 | 约束 |
|----|------|------|
| `workspace_id` | VARCHAR(50) | PK, FK |
| `user_id` | VARCHAR(50) | PK, FK |
| `module_id` | VARCHAR(20) | PK |
| `access_level` | VARCHAR(10) | `NONE` / `VIEW` / `EDIT` |

#### `iam_audit_log`

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT | PK 自增 |
| `actor_user_id` | VARCHAR(50) | 操作者 |
| `action` | VARCHAR(100) | 如 `CREATE_USER`, `CHANGE_ROLE`, `DISABLE_MODULE` |
| `target_type` | VARCHAR(50) | USER / WORKSPACE / MODULE |
| `target_id` | VARCHAR(200) | 操作目标 ID |
| `payload_json` | TEXT | 变更详情 |
| `created_at` | TIMESTAMP | — |

---

## 4. 后端架构设计

### 4.1 包结构（新增 `iam/`）

```
com.plantops.iam/
├── entity/
│   ├── AppUserEntity.java              // app_user JPA
│   ├── WorkspaceMemberEntity.java       // workspace_member
│   ├── WorkspaceEnabledModuleEntity.java // workspace_enabled_module
│   ├── WorkspaceEnabledAdapterEntity.java // workspace_enabled_adapter
│   └── IamAuditLogEntity.java          // iam_audit_log
├── context/
│   └── SecurityContext.java            // @RequestScoped: currentUserId, isAuthenticated
├── filter/
│   ├── AuthenticationFilter.java       // 认证过滤器（ContainerRequestFilter）
│   └── AuthorizationFilter.java        // 授权过滤器（ContainerRequestFilter）
├── service/
│   ├── IamService.java                 // 用户/成员/权限 CRUD
│   └── IamWorkspaceService.java        // WS 模块开关、成员矩阵
├── api/
│   ├── IamResource.java                // REST: /iam/me, /iam/workspaces
│   ├── IamWorkspaceResource.java       // REST: /iam/workspaces/{id}/modules
│   └── AdminResource.java              // REST: /admin/users, /admin/workspaces（M3）
├── dto/
│   ├── CurrentUserDto.java             // userId, displayName, isSuperAdmin
│   ├── WorkspaceMembershipDto.java     // workspaceId, name, role, enabledModules[]
│   └── ModulePermissionDto.java        // moduleId, accessLevel
└── annotation/
    └── RequirePermission.java          // 自定义权限注解（M3）
```

### 4.2 请求过滤器链路（M1）

```
HTTP Request
    │
    ▼
[1] AuthenticationFilter (priority=100)
    ├─ dev-mode? → 注入 dev CurrentUser, chain to next
    ├─ prod?    → 解析 JWT from Authorization header
    └─ 无有效身份? → 401
    │
    ▼
[2] WorkspaceRequestFilter (priority=200) — 已有，扩展
    ├─ 跳过 /api/v1/iam/*, /api/v1/auth/*, /api/v1/admin/*
    ├─ 跳过 /api/v1/workspaces（创建/查询 WS 不依赖已选中 WS）
    ├─ 读 X-Workspace-Id
    │   ├─ 无 header → 用户无 WS ? → 放行（无 WS 状态由前端 /iam/me 的 hasWorkspaces 控制）
    │   └─ 有 header → 查 workspace_member(wsId, userId)
    │       └─ 不存在 → 403 WORKSPACE_FORBIDDEN
    └─ 写 WorkspaceContext.workspaceId + SecurityContext
    │
    ▼
[3] AuthorizationFilter (priority=300) — M1 空过，M2/M3 启用
    ├─ dev-mode? → skip
    ├─ 查 workspace_enabled_module → enabled=false → 403 MODULE_DISABLED
    ├─ 查 workspace_member_module → accessLevel 判定
    │   └─ NONE → 403 MODULE_FORBIDDEN
    │   └─ VIEW & POST/PUT/DELETE → 403
    └─ pass
    │
    ▼
Resource Method
```

### 4.3 SecurityContext 设计

```java
@RequestScoped
public class SecurityContext {
    String currentUserId;      // 由 AuthenticationFilter 填充
    String displayName;
    boolean isSuperAdmin;
    boolean isDevMode;

    // 便捷方法
    boolean isAuthenticated();
    boolean hasWorkspaceAccess(String wsId);
    boolean canEditModule(String moduleId);
    boolean canViewModule(String moduleId);
}
```

**与 `WorkspaceContext` 的关系：** 共存不替代。`WorkspaceContext` 仍负责 `getWorkspaceId()`，`SecurityContext` 负责用户身份和权限。避免全局改动所有 `@Inject WorkspaceContext` 的地方。

### 4.4 API 设计（M1）

| API | 方法 | 路径 | 权限 | 响应 |
|-----|------|------|------|------|
| 当前用户 | GET | `/api/v1/iam/me` | 已认证 | `CurrentUserDto { userId, displayName, isSuperAdmin, hasWorkspaces, workspaces[] }` |
| 可访问 WS | GET | `/api/v1/iam/workspaces` | 已认证 | `WorkspaceMembershipDto[] { workspaceId, name, role, enabledModules[] }` |
| 创建 WS | POST | `/api/v1/workspaces` | 已认证 | 三合一：workspace + member(OWNER) + enabled_modules（默认值） |
| WS 模块配置 | PUT | `/api/v1/iam/workspaces/{id}/modules` | WS_ADMIN+ | `{ modules: [{ moduleId, enabled }] }`（M2） |
| WS 适配器配置 | PUT | `/api/v1/iam/workspaces/{id}/adapters` | WS_ADMIN+ | `{ adapters: [{ adapterId, enabled }] }`（M2） |
| 成员权限矩阵 | PUT | `/api/v1/iam/workspaces/{id}/members/{uid}/permissions` | WS_ADMIN+ | `{ permissions: [{ moduleId, accessLevel }] }`（M3） |

### 4.5 M1 变更最小化原则

- **不删** `WorkspaceContext`，不删 `WorkspaceRequestFilter`，仅扩展
- **不强制 FK**（Flyway 先加列，M3 加约束）
- **不拦截无 WS 请求**：用户 workspace 为空时放行（让前端显示创建页）
- **没有 default workspace**：`WorkspaceConstants.DEFAULT_ID` 仅作 fallback（dev-mode demo），不自动为用户创建
- **创建 WS 时三合一**：`WorkspaceService.createWorkspace()` 在一个事务中创建 workspace + workspace_member(OWNER) + workspace_enabled_module(默认全开) + workspace_enabled_adapter(EXCEL)
- **不影响现有 API**：`/api/v1/workspaces` 等旧端点保留；新的 `/api/v1/iam/workspaces` 返回经过成员过滤的结果

---

## 5. 前端架构设计

### 5.1 组件树

```
App
├── AppProviders
│   ├── QueryClientProvider (TanStack Query)
│   ├── ConfigProvider (Ant Design)
│   └── AuthProvider           ← 新增（M1）
│       ├── [hasWorkspaces=false] → CreateWorkspacePage   ← 新增（M1）
│       └── [hasWorkspaces=true]
│           ├── WorkspaceProvider  ← 改造：取列表从 AuthContext
│           │   └── PlanProvider
│           └── Routes
│               ├── LoginPage       ← 新增（M3）
│               ├── Layout (sidebar + topbar)
│               │   ├── WorkspaceSelector  ← 从 AuthContext.workspaces
│               │   └── SidebarNav         ← filterNavGroups(enabledModules)
│               └── AdminPages            ← 新增（M3）
```

**CreateWorkspacePage（M1 新增）：**

```
┌──────────────────────────────────────────┐
│                                          │
│        Plant Operation Plan              │
│        工厂运营计划系统                    │
│                                          │
│    欢迎，dev！                            │
│    你还没有创建任何工作区（Workspace）。    │
│    创建一个数据集来开始计划。              │
│                                          │
│    ┌────────────────────────────────┐    │
│    │  工作区名称                     │    │
│    ├────────────────────────────────┤    │
│    │  描述（可选）                   │    │
│    └────────────────────────────────┘    │
│                                          │
│    [        创建工作区        ]          │
│                                          │
└──────────────────────────────────────────┘
```

色板与现有 Ant Design Shell 一致。`workspace_type` 默认 `PERSONAL`（M1 不暴露类型选择，M2 加 SHARED）。

### 5.2 AuthContext 设计（M1）

```typescript
// providers/AuthContext.tsx — 新增
type AuthState = {
  isAuthenticated: boolean;
  currentUser: { userId: string; displayName: string; isSuperAdmin: boolean } | null;
  hasWorkspaces: boolean;                     // true = 至少有一个 workspace
  workspaces: WorkspaceMembershipDto[];       // 用户有权限的 WS 列表
  enabledModules: Record<string, boolean>;    // 当前选中 WS 的 MOD enabled 映射
  isLoading: boolean;
};

type AuthContextValue = AuthState & {
  login: (name: string, password: string) => Promise<void>;   // M3
  logout: () => void;                                          // M3
  refresh: () => Promise<void>;
  createWorkspaceAndSelect: (payload: CreateWorkspacePayload) => Promise<void>;  // M1
};
```

**M1 数据流：**

```
AuthProvider mount
  → GET /api/v1/iam/me
  → 后端 dev-mode → { userId: "dev", isSuperAdmin: true, hasWorkspaces: true, workspaces: [...] }
  → 设 currentUser, workspaces, hasWorkspaces

  if (hasWorkspaces === false):
    → 渲染 <CreateWorkspacePage />（不显示侧栏/顶栏）
    → 用户填写名称 → createWorkspaceAndSelect()
      → POST /api/v1/workspaces → 返回 workspaceId
      → 写 localStorage: plantops.workspaceId
      → AuthContext.refresh() → hasWorkspaces=true
      → 渲染正常 Layout

  if (hasWorkspaces === true):
    → WorkspaceSelector 只渲染 workspaces 中的 WS
    → 当前选中 WS 不存在于 workspaces → 自动选中第一个
    → Layout 取 enabledModules → filterNavGroups() → 侧栏
```

### 5.3 与现有代码的集成点

| 现有代码 | 变更 |
|----------|------|
| `useEnabledModules()` | 从 `AuthContext.enabledModules[workspaceId]` 取值，不再硬编码（M0 行为不变，因为 dev 的 demo WS 全模块开启） |
| `WorkspaceProvider` | 从 `AuthContext` 接收 `workspaces` 列表，不再自己调 `GET /api/v1/workspaces` |
| `WorkspaceSelector` | WS 列表从 `AuthContext.workspaces` 取，而非全量 API |
| `App.tsx` | `<AuthProvider>` 包裹 `<WorkspaceProvider>`；`hasWorkspaces=false` 时渲染 `CreateWorkspacePage` |
| `api/client.ts` | `request()` 附加 `Authorization` 头（M3），401 拦截 |
| `WorkspaceService.createWorkspace()` | **三合一**：创建 WS 时同步写 member + modules + adapter |

### 5.4 侧栏模块过滤

已实现在 `workspaceNav.ts` 的 `filterNavGroups()`：

```
输入：enabledModules = { "MOD-DI": true, "MOD-OCP": false, ... }
输出：仅保留 moduleIds 全部 contained 的 group

示例：disabled MOD-OCP → MASTER_PLAN_GROUP 隐藏
```

---

## 6. 关键设计决策

### 6.0 不做 default workspace（2026-06-24 确认）

- 用户登录后 workspace 列表为空，须手工创建第一个 workspace
- `POST /api/v1/workspaces` 三合一创建（workspace + member OWNER + 默认模块全开）
- 前端 `hasWorkspaces=false` → 显示 CreateWorkspacePage；无侧栏/顶栏
- demo WS（jinghua, te, slitting-demo）仅 dev 用户名下可见（WorkspaceSeedService 为 dev 创建）
- `WorkspaceConstants.DEFAULT_ID = "default"` 降级为 dev-mode demo fallback，不用于新建用户
- 好处：干净的用户体验；每个 WS 明确归属；避免"全用户默认 WS"的安全风险

### 6.1 为什么本地密码而非 OIDC 先行

- M1–M3 为开发和内测阶段，用户 ≤ 20 人
- M4 上生产时再引入 Keycloak——JWT 机制完全兼容
- Quarkus `elytron-security` 对本地密码 + JWT 有原生支持

### 6.2 为什么 JWT 而非 Session Cookie

- SPA 前后端分离：`Authorization: Bearer <jwt>` 更简单
- 无 CSRF 顾虑
- JWT payload 可带 `userId`, `isSuperAdmin`，减少额外 API 调用

### 6.3 最小权限原则

- 新 MEMBER 对各模块 `accessLevel=NONE`
- OWNER/WS_ADMIN implicit EDIT（减少配置负担）
- 禁止「加人即全可见」

### 6.4 `workspace` 表扩展而非新表

- 现有 `workspace` 表已有 `workspace_id`, `name`；仅需加两列（`owner_user_id`, `workspace_type`）
- 避免迁移历史数据

---

## 7. 上下游依赖

| 依赖方 | 依赖项 | 影响 |
|--------|--------|------|
| **TODO-19 集成 API** | `AuthorizationFilter` 模块开关 | API-INT-05/06/07 需要 MOD-DI·EDIT |
| **BusinessRulesPage** | API-IAM 模块归属校验 | OCP/SCH 规则保存需对应模块 EDIT |
| **前端导航** | `AuthContext.enabledModules` | 已通过 `filterNavGroups()` 预留接口 |
| **WorkspaceAdminPage** | 扩展 owner/type 列 | UI 新增字段展示 |

---

## 8. M1 测试计划

| 测试类型 | 内容 |
|----------|------|
| **Flyway** | V63 执行成功；`app_user` 有 dev 行；`workspace` 新列非空；无 `default` workspace |
| **dev-mode 启动** | 启动后 `WorkspaceSeedService` 创建 demo WS + dev 为 OWNER；`/iam/me` 返回 `hasWorkspaces=true, workspaces=[jinghua,te,slitting-demo]` |
| **新用户无 WS** | `GET /api/v1/iam/me` → `hasWorkspaces=false, workspaces=[]` |
| **新用户创建 WS** | `POST /api/v1/workspaces { name, description }` → 201 + workspaceId；`workspace_member` 插入 OWNER；`workspace_enabled_module` 插入 5 行；`workspace_enabled_adapter` 插入 EXCEL |
| **无 WS 用户访问业务 API** | 不带 `X-Workspace-Id` → API 放行（返回空/提示无 WS）；带不存在的 `X-Workspace-Id` → 403 |
| **有 WS 用户访问业务 API** | 带有效 `X-Workspace-Id` + 是 member → 正常；不是 member → 403 |
| **前端无 WS 态** | `hasWorkspaces=false` → 显示 CreateWorkspacePage；创建后自动跳转 |
| **前端回归（dev-mode）** | dev 用户显示 3 个 demo WS；侧栏无变化；所有页面正常渲染 |

---

## 9. 工作量估算

| 阶段 | 后端 | 前端 | DB | 合计 |
|------|------|------|----|------|
| **M1** | 3d | 2d | 0.5d | **5.5d** |
| M2 | 1d | 1.5d | — | 2.5d |
| M3 | 2d | 2d | 0.5d | 4.5d |
| M4 | 1d | 0.5d | — | 1.5d |
| **总计** | **7d** | **5.5d** | **1d** | **13.5d** |

---

## 修正后的 M1 任务清单

| # | 内容 | 说明 |
|---|------|------|
| 1 | Flyway V63 | 6 张表；种子仅 `app_user`(dev)；不创建 `default` workspace |
| 2 | `WorkspaceEntity` +2 列 | `owner_user_id`, `workspace_type` |
| 3 | `iam/` 包骨架 | 5 entity + SecurityContext + AuthenticationFilter + AuthorizationFilter(空壳) |
| 4 | `WorkspaceRequestFilter` 扩展 | 注入 SecurityContext；无 WS 用户放行；有 WS 校验 member |
| 5 | `IamResource.java` | `GET /api/v1/iam/me`（含 `hasWorkspaces`）+ `GET /api/v1/iam/workspaces` |
| 6 | `WorkspaceService.createWorkspace()` | **三合一事务**：workspace + member(OWNER) + enabled_modules(5) + enabled_adapters(EXCEL) |
| 7 | `WorkspaceSeedService` 适配 | demo WS 创建后为 dev 插入 `workspace_member` OWNER |
| 8 | 前端 `AuthContext` + `AuthProvider` | `hasWorkspaces` 状态 + `createWorkspaceAndSelect()` |
| 9 | 前端 `CreateWorkspacePage` | `hasWorkspaces=false` 时显示；创建后自动刷新进入 |
| 10 | 前端 `WorkspaceProvider` 改造 | 从 `AuthContext` 取列表，不再自己调 API |
| 11 | 前端 `useEnabledModules` + `Layout` | 从 `AuthContext.enabledModules` 驱动侧栏过滤 |
| 12 | 集成测试 | /iam/me 新用户 vs dev；创建 WS 三合一；Filter 放行逻辑 |

> **与评审前对比的关键变化：**
> - 移除"默认 default workspace" → 用户自建
> - 移除"全 WS 盲目种子" → 仅 demo WS 归属 dev
> - 新增 `CreateWorkspacePage` 前端页面
> - `WorkspaceRequestFilter` 无 WS 时放行（不拦创建）
> - `POST /api/v1/workspaces` 三合一创建
> - `hasWorkspaces` 字段驱动前端状态机

---

*TODO-18 IAM 详细设计 · 2026-06-24 · 评审后修正（v2） · 无 default workspace*
