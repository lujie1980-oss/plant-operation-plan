# §9 非功能要求

## NFR-01 响应时间

| 项 | 目标 | 验证 |
|----|------|------|
| API-FC-01 满足链 | P95 < 2s（te 规模） | 压测 / 手工 |
| API-SES-02 simulate | P95 < 5s | Session 集成测试 |
| API-SES-03 optimize | P95 < 120s（te 规模） | 计划运行计时 |
| **单订单 ATP 评估（端到端）** | **P95 ≤ 30s** | 选中 COLD → `fulfillment-chain`（+ 可选 simulate）完成；对齐 VAL-06 |
| **单订单 CTP 评估（端到端）** | **P95 ≤ 120s** | 同一 COLD → DeliveryPlanningSandbox / Session **optimize** 完成；对齐 VAL-06 |
| **PISP 优化创建供应计划（SCN-07d）** | **P95 ≤ 120s** | POST API-MAT-03 mode=OPTIMIZE |
| 前端首屏 | LCP < 3s（dev 构建） | Lighthouse 抽检 · **§17.11 UI-NFR-01** |

**追溯：** VAL-01, VAL-02, VAL-06

---

## NFR-02 可用性与 Session 生命周期

| 项 | 要求 |
|----|------|
| Session TTL | ~8 小时；过期 DRAFT revision 标记 `ABANDONED` |
| 单 Workspace 并发 Session | 允许多个；以 sessionId + `ont_revision` 区分 |
| 求解失败 | 返回明确错误；不部分 confirm |
| **宕机恢复（FULL）** | DRAFT Session 在 WAL commit 边界可恢复（RULE-PERS-04）；COMMITTED HEAD 始终可 load |

---

## NFR-03 数据持久化

| 项 | 要求 |
|----|------|
| 默认数据库 | H2 文件 `./data/plantops` |
| 迁移 | Flyway 版本化；启动自动迁移 |
| 生产可选 | PostgreSQL（配置切换） |
| 备份 | 运维负责文件级备份（H2） |
| **Ontology 表** | `ont_*` 与 ENT-* 1:1（§5.14）；committed 数据须可 SQL 查询 |
| **revision 归档** | `ARCHIVED` revision 可冷存储；HEAD 仅指向 COMMITTED |

---

## NFR-04 安全

| 项 | v1 过渡 | 目标态（ADR-13 · §18） |
|----|---------|------------------------|
| 认证 | 开发环境可关闭（`dev-mode`） | 生产 OIDC / 本地账号 + JWT |
| 授权 | 仅 `X-Workspace-Id` 存在性校验 | WS 成员 + MOD 开关 + VIEW/EDIT |
| Workspace | 知 id 即可访问（**风险 RSK-07**） | 非成员 **403** |
| Super Admin | 无 | `/admin/*` + 审计日志 |
| Admin API | 须限制网络或外置鉴权 | SUPER_ADMIN + IAM Filter |

---

## NFR-05 可观测性

| 项 | 要求 |
|----|------|
| 流水线状态 | `GET /planning/pipeline-runs/{runId}` |
| 求解得分 | score-explanation API |
| 日志 | Quarkus 标准日志；求解可抓 DEBUG |

---

## NFR-06 部署

| 项 | 要求 |
|----|------|
| 容器 | Docker 多阶段构建 |
| 静态前端 | 打入 `META-INF/resources` |
| 文档 | [docker-deploy.md](../docker-deploy.md) |

---

## NFR-07 可维护性（规范治理）

| 项 | 要求 |
|----|------|
| 规范↔测试 | AC ID 与测试 `@SpecRef` 绑定（目标态） |
| 契约变更 | 同步 §6 + ADR |
| 缺陷回流 | 生产缺陷须补 SCN + AC |

---

**回指：** [00-meta.md](./00-meta.md)
