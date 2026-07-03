# Blind rebuild gap log (TODO-05)

Record each blind exercise here. One row per session.

| Date | moduleId | Agent/human | Gate result | Gap summary | Follow-up |
|------|----------|-------------|-------------|-------------|-----------|
| 2026-07-03 | `sch-p0-projection` | Cursor agent (M1 对照演练) | **pass**（既有实现 + 闸绿） | 见下方 session | §5.22 时间派生 · API-MP-03/04 已回填 |

## Gap template (copy for new rows)

```
### YYYY-MM-DD · {moduleId}

**Gate:** pass / fail ({test name})

**What was ambiguous or wrong in spec:**
- …

**Backfill actions:**
- [ ] SDD section …
- [ ] AC-… test …
- [ ] OpenAPI §6 entry …
```

---

### 2026-07-03 · sch-p0-projection (M1)

**Gate:** pass (`DetailScheduleLegacyProjectorTest` · `BlindRebuildGateSuiteTest` · global gates)

**Method:** 对照演练 — 仅读 pilot 01 + §5.22/附录，核对既有实现是否可由规范唯一推导；未删代码重写的真盲重建留待独立 worktree。

**What was ambiguous or wrong in spec:**
- 附录写 `ScheduleTimingUtil` 但未给出公式 → 盲实现无法推导 `plannedStartTs`/`slotDate`
- Pilot 01 未写 loader 锚点解析优先级（feedback → plan_generated_ts → today）
- Pilot 02 / SCN-06b 的对比 API 未入 §6 → OpenAPI 生成器无法发现契约

**Backfill actions:**
- [x] §5.22 分钟→日历时间表
- [x] 附录 ENT-OP-SCH 派生字段改指 §5.22
- [x] Pilot 01 补充 loader 锚点规则
- [x] §6 增 API-MP-03 / API-MP-04 + 再生 `openapi.yaml`
- [x] AC-BR-02 · `BlindRebuildPilot01SessionTest`
- [ ] 真盲重建（隐藏 `ontology.scheduling` 包）— 下次季度演练
