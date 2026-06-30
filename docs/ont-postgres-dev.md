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
| `db/migration/` | H2（历史） | V1–V64 legacy 表 + **V66** `ont_*` P0 |
| `db/migration-postgresql/` | PostgreSQL | V0 bootstrap + **V65** `ont_*` P0 |

**验收：** `OntP0SchemaMigrationTest`（PG :5432 运行时执行；无 PG 时 `@EnabledIf` 跳过）

**已知限制：** 在空 PG 上跑 **完整应用** 会因 legacy 表缺失失败。`postgres` profile 已设 `plantops.legacy-schema.enabled=false`，跳过 workspace/样例数据等启动钩子；仅适合 `ont_*` 持久化开发与集成测试。

- **H2** — 全应用 + legacy 计划链路  
- **PG** — `ont_*` migration + `OntologyRestorer` 集成测试

后续可选：将 legacy migration 移植为 PG 方言，或从 H2 导出 baseline 初始化 PG（单独待办）。

## 4. 测试策略

| 套件 | 数据库 |
|------|--------|
| 大部分 `@QuarkusTest` | H2 内存（`%test`） |
| `IamAcTest` 等 | H2 |
| `OntP0SchemaMigrationTest` | PostgreSQL（compose） |
| `OntologyRestorerIntegrationTest` | PostgreSQL（AC-PERS-01 P0 子集） |
| `OntologyDraftPersistenceIntegrationTest` | PostgreSQL（AC-PERS-02 DRAFT + WAL 恢复） |
| `OntologyConfirmIntegrationTest` | PostgreSQL（AC-PERS-03 promote + HEAD） |
| `OntologyLegacyDualWriteIntegrationTest` | H2（AC-PERS-04 work_order 双写） |
| `OntologyLoaderRestorerParityIntegrationTest` | H2（AC-PERS-01 loader↔restorer P0） |

**Session API 持久化：** `plantops.ontology.persistence.session-enabled=true`（`postgres` profile 默认开启）时，`MasterPlanOntologySessionService` 的 create/simulate/optimize/confirm 同步写 `ont_*`；默认 H2 dev 仍为内存 `OntologySandboxStore`。

**P4 迁移开关（postgres profile）：**

| 配置项 | 作用 |
|--------|------|
| `plantops.ontology.persistence.dual-write-enabled` | confirm promote 时将 `work_order` 同步到 `ont_supply_order`（需 `legacy-schema.enabled=true`） |
| `plantops.ontology.persistence.restorer-read-enabled` | 权威图装载时叠加 committed `ont_*` P0（`OntologyP0Overlay`） |
| `plantops.ontology.persistence.bootstrap-head-enabled` | 启动/读路径时从 `OntologyLoader` 引导 `ont_revision_head(WORKSPACE)` |

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
