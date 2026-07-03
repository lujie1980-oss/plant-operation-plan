# Plant Operation Plan — SDD 规范文档集

> **蓝图标识：** `BP-PlantOps-v1`  
> **规范驱动开发（SDD）** — 本文档集同时是业务蓝图、AI 提示词、验收契约与回归预言。  
> **产品版本：** 1.0.0-SNAPSHOT · **基线：** Plant Operation Ontology  
> **更新日期：** 2026-06-30

---

## 文档结构（三卷 + 核心）

| 卷 | 章节 | 文件 | 主笔 | 说明 |
|----|------|------|------|------|
| **核心** | §0 | [core/00-meta.md](./core/00-meta.md) | 顾问 | 元信息、版本、读者、治理 |
| **核心** | §1 | [core/01-value-goals.md](./core/01-value-goals.md) | 产品 | 价值、KPI、范围 |
| **核心** | §2 | [core/02-glossary.md](./core/02-glossary.md) | 全员 | **统一术语表**（一词一义） |
| **核心** | §3 | [core/03-scenarios.md](./core/03-scenarios.md) | 顾问 | **七类业务场景** + 平台场景 GWT |
| **核心** | §4 | [core/04-business-rules.md](./core/04-business-rules.md) | 顾问+开发 | **§4.0 基础规则** + 业务硬/软约束 |
| **核心** | §5 | [core/05-domain-model.md](./core/05-domain-model.md) | 开发 | **Plant Operation Ontology** 领域模型 |
| **核心** | §6 | [core/06-api-contracts.md](./core/06-api-contracts.md) | 开发 | REST 契约 |
| **核心** | §7 | [core/07-standardization.md](./core/07-standardization.md) | 产品 | STD/CFG/CUST 标注 |
| **核心** | §8 | [core/08-acceptance.md](./core/08-acceptance.md) | 全员 | 可机械判定的验收 |
| **核心** | §9 | [core/09-nfr.md](./core/09-nfr.md) | 开发 | 性能、安全、运维 |
| **核心** | §10 | [core/10-decisions-risks.md](./core/10-decisions-risks.md) | 全员 | ADR、假设、风险、待办 |
| **核心** | §5 附录 | [core/05-domain-model-appendix-fields.md](./core/05-domain-model-appendix-fields.md) | 开发 | §5.20 实体字段目录（TODO-21 Phase 2） |
| **数据卷** | ont schema | [volumes/data/05-ont-schema.md](./volumes/data/05-ont-schema.md) | 开发 | `ont_*` 列级 DDL（**V65 P0 已落地** · PostgreSQL） |
| **数据卷** | §11·§12 | [volumes/data/11-12-external-data.md](./volumes/data/11-12-external-data.md) | 开发+顾问 | External → `md_*` / `txn_*` |
| **知识卷** | §13·§14 | [volumes/knowledge/13-14-business-knowledge.md](./volumes/knowledge/13-14-business-knowledge.md) | 产品+顾问 | 知识三层 + Standard 目录 |
| **知识卷** | §15·§16 | [volumes/knowledge/15-16-planning-knowledge.md](./volumes/knowledge/15-16-planning-knowledge.md) | 产品+顾问 | PROC-S04 KPI + 供需知识 |
| **平台卷** | §17 | [volumes/platform/17-ui-ux.md](./volumes/platform/17-ui-ux.md) | 产品+前端 | UI/UX（路由·组件·SCN 映射） |
| **平台卷** | §18·§19 | [volumes/platform/18-19-workspace-platform.md](./volumes/platform/18-19-workspace-platform.md) | 架构+产品 | IAM · MOD-* · ADP 适配器 |

## 可追溯链

```
VAL → SCN → RULE / ENT → API → AC → 测试 @SpecRef
SCN → UI-PAGE / UI-COMP（§17）→ 前端实现
MOD / ADP（§19）→ API-INT / API-IAM → 侧栏与模块开关
§4.0 基础规则 → SCN → AC（不经过 VAL）
§15 KPI · §16 供需知识 → MOD-OCP / SCH 规则路由（§4.6 · §17）
```

## 配套文档

- [PDD.md](../PDD.md) — 产品说明（业务语言）
- [core/05-domain-model.md](./core/05-domain-model.md) — **Plant Operation Ontology** 领域模型（§5 唯一正文）
- [ontology-domain-model.drawio](../ontology-domain-model.drawio) — Draw.io 图源
- [archive/](../archive/) — 历史文档（只读）

## 质量自检（提交评审前）

- [ ] 术语均在 §2 定义，正文无同义词
- [ ] 每个 SCN 挂在 VAL 下，含至少一条异常流
- [ ] 每条 hard RULE 有 §8 不变量验收
- [ ] §7 能力标签完整（STD/CFG/CUST + COMMON/SPECIFIC）
- [ ] 验收锚定规范，不锚定实现类名
- [ ] 前端页面行为与 §17 UI-NAV / SCN 映射一致（或标注 [GAP]）

---

*撰写指南：`.agents/skills/sdd-spec-authoring/SKILL.md`*
