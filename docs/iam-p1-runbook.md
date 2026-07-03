# P1 验收 Runbook — OIDC 联调 + AC-IAM

> **前置：** IAM M0–M4 已落地（PR #1）

## 1. 自动化 AC-IAM（无需 Keycloak）

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
.\mvnw.cmd test "-Dtest=IamAcTest,AuthenticationFilterTest" "-Dskip.frontend.build=true"
```

覆盖：**AC-IAM-01 ~ 05**（手动建 WS、非成员 403、MOD 关闭、VIEW 禁写、Super Admin 审计）

## 2. OIDC 端到端（需 Docker）

### 2.1 一键脚本

```powershell
.\tools\p1-acceptance.ps1
```

### 2.2 手动步骤

| 步骤 | 命令 |
|------|------|
| Keycloak | `.\tools\start-oidc-dev.ps1` |
| 后端 oidc profile | `$env:QUARKUS_PROFILE='oidc'; .\mvnw.cmd quarkus:dev "-Dskip.frontend.build=true"` |
| 前端 | `cd frontend; npm run dev` |
| API 冒烟 | `.\tools\oidc-smoke-test.ps1` |
| 浏览器 | http://localhost:5173 → **使用企业账号登录** → `planner` / `planner` |

### 2.3 自动化 OIDC（Keycloak 运行时）

```powershell
# 后端须 QUARKUS_PROFILE=oidc 且端口 8080
.\mvnw.cmd test "-Dtest=OidcLiveIntegrationTest" "-Dskip.frontend.build=true"
```

Keycloak 未启动时该测试 **自动跳过**。

## 3. 验收清单

| ID | 自动化 | 手工 |
|----|--------|------|
| AC-IAM-01 | `IamAcTest#acIam01_*` | 新用户登录 → CreateWorkspacePage → 创建 |
| AC-IAM-02 | `IamAcTest#acIam02_*` | — |
| AC-IAM-03 | `IamAcTest#acIam03_*` | 关 MOD-SLT 后侧栏无分切 |
| AC-IAM-04 | `IamAcTest#acIam04_*` | — |
| AC-IAM-05 | `IamAcTest#acIam05_*` | `/admin/users` 创建用户 |
| OIDC SSO | `OidcLiveIntegrationTest`（可选） | 浏览器企业登录全流程 |

## 4. 参考

- [oidc-keycloak-dev.md](./oidc-keycloak-dev.md)
- §8 [08-acceptance.md](./sdd/core/08-acceptance.md#ac-iam用户与权限18--adr-13)
