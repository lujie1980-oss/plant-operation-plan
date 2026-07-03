# Pilot 01 — SCH-P0 legacy projection

**moduleId:** `sch-p0-projection`  
**Gate:** AC-SCH-P0-01

## Allowed inputs

Read only:

- `docs/sdd/core/05-domain-model.md` §5.21–5.22
- `docs/sdd/core/05-domain-model-appendix-fields.md` — ENT-OP-SCH, ENT-RCA-SCH
- `docs/sdd/core/08-acceptance.md` — AC-SCH-P0-01
- `docs/scheduling-domain-model.md` — `DetailScheduleOperation` legacy shape (minute fields)

**Forbidden:** existing `com.plantops.ontology.scheduling.*` sources.

## Behaviour

Build a **read-only** projector that:

1. Accepts a detail schedule version id, planning anchor date, and a list of persisted operation rows equivalent to:
   - `operationId`, `workOrderNo`, `lineId`, `resourceId`, `sequenceIndex`, `startMinute`, `endMinute`, `productCode`, `pinned`, `batchNo`, `operationSeq`, `operationName`
2. Emits `DetailScheduleOntologyView` containing:
   - `operationSchedules` (ENT-OP-SCH) — ids `OPS-SCH-{version}-{operationId}`
   - `capacityAssignments` (ENT-RCA-SCH) — ids `RCAS-{operationId}-{physicalResourceId}`
3. Derives wall-clock timestamps per **§5.22 分钟→日历时间** (anchor `00:00` + natural minutes; `slotDate` = completion calendar day).
4. Does **not** write `ont_*` tables or merge into ENT-OG graph.

**Loader (in scope for full SCH-P0 stack):** resolve `planningAnchorDate` from `schedule_feedback.planning_anchor_date` → else `plan_version.plan_generated_ts` → else today (§5.22 legacy 映射).

## Acceptance (AC-SCH-P0-01)

Automated test must verify:

- Stable id helpers for OPS-SCH and RCAS prefixes
- One sample row maps field-for-field per appendix
- `slotDate` = completion calendar day
- `locked` follows `pinned`

## Non-goals

- SCH-P1 persistence
- REST API exposure
- UI
