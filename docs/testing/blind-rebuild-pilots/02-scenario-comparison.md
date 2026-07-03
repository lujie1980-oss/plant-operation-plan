# Pilot 02 — Scenario comparison (VAL-06)

**moduleId:** `scenario-comparison`  
**Gate:** AC-VAL-06-01

## Allowed inputs

- `docs/sdd/core/01-value-goals.md` — VAL-06
- `docs/sdd/core/03-scenarios.md` — SCN-06b
- `docs/sdd/volumes/knowledge/15-16-planning-knowledge.md` — KPI-MP-B01~B10 labels
- `docs/sdd/volumes/platform/17-ui-ux.md` — UI-COMP-07
- `docs/sdd/core/08-acceptance.md` — AC-VAL-06-01

**Forbidden:** existing `ScenarioComparisonService`, `ScenarioComparisonPage`.

## API (behavioural contract)

Implement per **§6 API-MP-03 / API-MP-04**:

### Required metric id groups

| Prefix | Source |
|--------|--------|
| `mp_score_*` | Plan version score string |
| `cold_*` | COLD delivery summary per version |
| `mp_b01`…`mp_b10` | §15 business KPIs |
| `cap_*` | Capacity analysis KPIs |
| `mp_total_wo`, `mp_total_load`, `solve_duration` | Scheduling aggregates |

## UI (UI-COMP-07)

- Route: `/master-plan/scenario-comparison`
- Multi-select scenarios; first selected = baseline; table shows deltas
- Chart sections for Score, COLD, B01~B10, capacity, scheduling

## Acceptance (AC-VAL-06-01)

Unit/integration test: metric registry includes `cold_*` and `mp_b01`…`mp_b10` ids.
