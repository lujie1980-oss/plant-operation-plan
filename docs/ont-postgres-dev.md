# Ontology 持久化 · PostgreSQL 开发指南

> **策略：** 默认开发仍用 **H2 文件库**；本体 `ont_*`（TODO-12）与 AC-PERS 在 **PostgreSQL** 上实现与验收。

## 1. 启动 PostgreSQL

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
.\tools\start-postgres-dev.ps1
```

连接：`jdbc:postgresql://localhost:5432/plantops` · 用户 `plantops` / `plantops`

停止：`.\tools\start-postgres-dev.ps1 -Down`

## 2. Profile 对照

| Profile | 数据库 | 用途 |
|---------|--------|------|
| *(默认)* | H2 `./data/plantops` | 日常功能开发、现有 Flyway V1–V64 |
| `postgres` | 本地 PG :5432 | TODO-12 `ont_*` 开发、AC-PERS 集成测试 |
| `prod` | 环境变量 `DB_*` | 生产部署（见 [docker-deploy.md](./docker-deploy.md)） |

```powershell
$env:QUARKUS_PROFILE='postgres'
.\mvnw.cmd quarkus:dev "-Dskip.frontend.build=true"
```

## 3. Flyway 分工

| 目录 | 方言 | 内容 |
|------|------|------|
| `db/migration/` | H2（历史） | V1–V64 legacy 表 |
| `db/migration-postgresql/` | PostgreSQL | V65+ `ont_*`（待 Sprint 1） |

**已知限制：** 在空 PG 上跑 **完整应用** 会因 V1 的 `AUTO_INCREMENT` / `CLOB` 失败。当前阶段：

- **H2** — 全应用 + legacy 计划链路  
- **PG** — 新增 `ont_*` migration + 专用集成测试（不依赖 legacy V1 在 PG 重放）

后续可选：将 legacy migration 移植为 PG 方言，或从 H2 导出 baseline 初始化 PG（单独待办）。

## 4. 测试策略

| 套件 | 数据库 |
|------|--------|
| 大部分 `@QuarkusTest` | H2 内存（`%test`） |
| `IamAcTest` 等 | H2 |
| `OntologyPersistence*Test`（待建） | PostgreSQL（Testcontainers 或 compose） |

## 5. 生产环境变量

| 变量 | 说明 |
|------|------|
| `QUARKUS_PROFILE` | `prod` |
| `DB_JDBC_URL` | `jdbc:postgresql://host:5432/plantops` |
| `DB_USER` / `DB_PASSWORD` | 凭据 |
| `JWT_SECRET` | 必填 |
| `OIDC_ENABLED` | `true` 时启用 OIDC |

## 6. 相关规范

- ADR-09 · TODO-12 · [05-ont-schema.md](./sdd/volumes/data/05-ont-schema.md)
- NFR-03 · [09-nfr.md](./sdd/core/09-nfr.md)
