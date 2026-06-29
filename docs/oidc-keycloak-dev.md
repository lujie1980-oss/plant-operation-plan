# OIDC / Keycloak 本地联调

与 Keycloak 做端到端 OIDC 联调：浏览器 SSO + 后端 token 校验。

## 前置

- Docker Desktop（或 Docker Engine）
- JDK 21、Node 22（前端 dev server）

## 1. 启动 Keycloak

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
docker compose -f docker-compose.oidc.yml up -d
```

等待就绪（约 30–60s）：

```powershell
curl http://localhost:8081/realms/plantops/.well-known/openid-configuration
```

管理台（可选）：http://localhost:8081/admin — `admin` / `admin`

## 2. 启动后端（oidc profile）

```powershell
$env:QUARKUS_PROFILE = "oidc"
.\mvnw.cmd quarkus:dev "-Dskip.frontend.build=true"
```

`oidc` profile 会关闭 `dev-mode`，启用 OIDC，并保留本地密码登录以便对比测试。

## 3. 启动前端

```powershell
cd frontend
npm run dev
```

打开 http://localhost:5173 — 应出现登录页与 **「使用企业账号登录」** 按钮。

## 4. 测试账号

| 场景 | Keycloak 用户 | 密码 | app_user | 说明 |
|------|---------------|------|----------|------|
| Super Admin SSO | `dev` | `dev` | 已有 `dev` | 超管，全平台权限 |
| 普通成员 SSO | `planner` | `planner` | V64 种子 | `jinghua` WS 成员，非超管 |

> Keycloak `preferred_username` 必须与 `app_user.login_name` 一致；用户须预先在平台创建（迁移或 Super Admin 创建）。

## 5. 浏览器联调流程

1. 点击 **使用企业账号登录**
2. 跳转 Keycloak 登录页，输入 `planner` / `planner`
3. 回调至 `http://localhost:5173/?code=...`
4. 前端自动调用 `POST /api/v1/auth/oidc/exchange` 换 token
5. 进入应用，请求头携带 IdP `access_token`

## 6. API 冒烟测试（无需浏览器）

后端与 Keycloak 均启动后：

```powershell
.\tools\oidc-smoke-test.ps1
```

脚本用 password grant 取 token，并调用 `GET /api/v1/iam/me` 验证 OIDC JWT 链路。

## 7. 配置说明

| 文件 | 作用 |
|------|------|
| `docker-compose.oidc.yml` | Keycloak 26 + realm 导入 |
| `tools/keycloak/plantops-realm.json` | realm `plantops`、client `plantops-ui` |
| `src/main/resources/application-oidc.properties` | 联调 profile |
| `V64__iam_oidc_planner_seed.sql` | `planner` 用户与 WS 成员 |

Client secret（联调）：`plantops-ui-secret` — **仅用于本地，勿用于生产。**

生产环境请使用 `%prod` profile + 环境变量，见 `application.properties` 中 IAM M4 注释。

## 故障排查

| 现象 | 处理 |
|------|------|
| 无「企业账号登录」按钮 | 确认 `QUARKUS_PROFILE=oidc`；检查 `/api/v1/auth/config` 中 `oidc.enabled` 与 `authorizationEndpoint` |
| `OIDC_DISCOVERY_NOT_READY` | Keycloak 未启动；重启后端或等待 Keycloak 就绪后刷新 |
| `OIDC_USER_NOT_PROVISIONED` | IdP 用户名在 `app_user` 中不存在 |
| `invalid_redirect_uri` | Keycloak client 需包含 `http://localhost:5173/` |
| Token exchange 失败 | 核对 client secret；redirect_uri 须与授权请求完全一致 |

## 停止

```powershell
docker compose -f docker-compose.oidc.yml down
```
