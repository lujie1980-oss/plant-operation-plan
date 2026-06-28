# Plant Operation Plan — 详细设计文档（SDD）

> **文档类型：** Specification-Driven Development（规范驱动开发）  
> **蓝图标识：** `BP-PlantOps-v1`  
> **撰写指南：** [sdd-spec-authoring 技能](../.agents/skills/sdd-spec-authoring/SKILL.md)  
> **更新日期：** 2026-06-20

---

## 说明

本 SDD 按 **sdd-spec-authoring** 技能分章组织，同时承担：

- **业务蓝图** — 价值、场景、规则（业务可读）
- **AI 提示词** — 术语表 + Given-When-Then + 契约（可生成代码）
- **验收契约** — AC 锚定场景与不变量（非实现细节）
- **回归预言** — 测试 `@SpecRef` 绑定 AC

> 规范身兼三职：给 AI 看的提示词 + 验收的契约 + 回归的测试预言。

**目录布局（2026-06-20）：** `docs/sdd/core/`（§0–§10）· `docs/sdd/volumes/`（数据/知识/平台三卷）。**§ 编号不变**。

---

## 文档索引

### 核心契约（§0–§10）

| 章节 | 文件 | 内容 |
|------|------|------|
| 导读 | [sdd/README.md](./sdd/README.md) | 三卷结构、可追溯链、质量自检 |
| §0 | [sdd/core/00-meta.md](./sdd/core/00-meta.md) | 元信息、版本、读者 |
| §1 | [sdd/core/01-value-goals.md](./sdd/core/01-value-goals.md) | VAL 价值与 KPI |
| §2 | [sdd/core/02-glossary.md](./sdd/core/02-glossary.md) | **统一术语表** |
| §3 | [sdd/core/03-scenarios.md](./sdd/core/03-scenarios.md) | **SCN 场景 GWT** |
| §4 | [sdd/core/04-business-rules.md](./sdd/core/04-business-rules.md) | RULE 硬/软约束 |
| §5 | [sdd/core/05-domain-model.md](./sdd/core/05-domain-model.md) | ENT 领域模型 |
| §6 | [sdd/core/06-api-contracts.md](./sdd/core/06-api-contracts.md) | API 契约 |
| §7 | [sdd/core/07-standardization.md](./sdd/core/07-standardization.md) | STD/CFG/CUST 标注 |
| §8 | [sdd/core/08-acceptance.md](./sdd/core/08-acceptance.md) | AC 验收标准 |
| §9 | [sdd/core/09-nfr.md](./sdd/core/09-nfr.md) | NFR 非功能 |
| §10 | [sdd/core/10-decisions-risks.md](./sdd/core/10-decisions-risks.md) | ADR / 风险 / 待办 |

### 数据卷（§11–§12）

| 章节 | 文件 | 内容 |
|------|------|------|
| §11·§12 | [sdd/volumes/data/11-12-external-data.md](./sdd/volumes/data/11-12-external-data.md) | External 主数据 + 交易 → md_* / txn_* |

### 知识卷（§13–§16）

| 章节 | 文件 | 内容 |
|------|------|------|
| §13·§14 | [sdd/volumes/knowledge/13-14-business-knowledge.md](./sdd/volumes/knowledge/13-14-business-knowledge.md) | 知识三层 + Standard 目录 |
| §15·§16 | [sdd/volumes/knowledge/15-16-planning-knowledge.md](./sdd/volumes/knowledge/15-16-planning-knowledge.md) | PROC-S04 KPI + 供需知识 |

### 平台卷（§17–§19）

| 章节 | 文件 | 内容 |
|------|------|------|
| §17 | [sdd/volumes/platform/17-ui-ux.md](./sdd/volumes/platform/17-ui-ux.md) | UI/UX 规范 |
| §18·§19 | [sdd/volumes/platform/18-19-workspace-platform.md](./sdd/volumes/platform/18-19-workspace-platform.md) | IAM · Workspace 模块 · ADP |

---

## 可追溯链（示例）

```
VAL-01 提高按时满足率
  → SCN-01c 满足链 / SCN-01a ATP / SCN-01b CTP / SCN-01g~h 建链 / SCN-02a OTIF 预警
    → RULE-FF-01 挂接顺序
    → ENT-COLD, ENT-FF, DTO-FC
    → API-FC-01
    → AC-01, AC-03
```

---

## 配套文档

| 文档 | 关系 |
|------|------|
| [PDD.md](./PDD.md) | 产品说明（业务语言，MOD-OCP / MOD-DI 叙事） |
| [sdd/core/05-domain-model.md](./sdd/core/05-domain-model.md) | §5 Plant Operation Ontology 领域模型 |
| [ontology-domain-model.md](./ontology-domain-model.md) | → 重定向至 §5 |
| [ontology-domain-model.drawio](./ontology-domain-model.drawio) | 领域模型 draw.io 图源 |
| [aps-planning-layer.md](./aps-planning-layer.md) | 推演层 P0–P4（HOW，见 ADR） |
| [otd-ontology-mapping.md](./otd-ontology-mapping.md) | OTD 映射 |

---

## 历史版本

2026-06-10 之前单体 SDD / 项目文档已移至 [archive/](./archive/)（`PROJECT_DOCUMENTATION.md`、`product-documentation.html`）。现行规范以 `docs/sdd/` 为准。

---

*Plant Operation Plan · SDD · BP-PlantOps-v1*
