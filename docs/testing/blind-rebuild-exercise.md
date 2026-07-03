# Blind rebuild exercise (TODO-05)

> **Goal:** Validate that SDD is sufficient for an agent (or new developer) to **reimplement a module from spec alone** and pass the same automated gates as production code.
>
> **Principle (环三 · SDD governance):** Gaps exposed during blind rebuild become spec/ADR/test backfill — not tribal knowledge.

## When to run

- After major SDD refactors (§5 / §6 / §8)
- Before declaring a module "spec-complete"
- Quarterly architecture hygiene

## Module packs

Registered in `BlindRebuildRegistry` (`src/test/java/com/plantops/testsupport/blindrebuild/`).

| moduleId | Pilot pack | Gate AC |
|----------|------------|---------|
| `sch-p0-projection` | [01-sch-p0-projection.md](./blind-rebuild-pilots/01-sch-p0-projection.md) | AC-SCH-P0-01 |
| `scenario-comparison` | [02-scenario-comparison.md](./blind-rebuild-pilots/02-scenario-comparison.md) | AC-VAL-06-01 |
| `workspace-module-registry` | [03-workspace-module-registry.md](./blind-rebuild-pilots/03-workspace-module-registry.md) | AC-IAM-06 |

## Procedure

### 1. Prepare blind context

Give the implementer **only**:

- The pilot pack markdown under `docs/testing/blind-rebuild-pilots/`
- Linked SDD sections (paths listed in the pack)
- **Do not** point at existing Java/TS implementation paths

Use a fresh worktree or hide `src/` for the target package if running a true blind exercise.

### 2. Implement

Rebuild backend (+ UI if in pack scope) to satisfy **SCN / RULE / AC** in the pilot brief.

### 3. Run gates

**Global gates** (every exercise):

```bash
.\mvnw.cmd test -Dtest=SpecRefCoverageTest,OpenApiSpecCoverageTest,BlindRebuildGateSuiteTest
```

**Module gates** (replace `{moduleId}`):

```bash
.\mvnw.cmd test "-Dtest=$(java -cp target/test-classes -e 'import com.plantops.testsupport.blindrebuild.BlindRebuildGateSuiteTest; System.out.print(BlindRebuildGateSuiteTest.gateTestCommand(\"sch-p0-projection\"));')"
```

Or manually from registry — example for `sch-p0-projection`:

```bash
.\mvnw.cmd test "-Dtest=SpecRefCoverageTest,OpenApiSpecCoverageTest,BlindRebuildGateSuiteTest,com.plantops.ontology.scheduling.DetailScheduleLegacyProjectorTest"
```

### 4. Log gaps

Record findings in [blind-rebuild-gap-log.md](./blind-rebuild-gap-log.md):

- Missing field in appendix
- AC without testable black-box assertion
- API in code but absent from §6 / OpenAPI
- Implicit anchor date / workspace rules

### 5. Backfill

Each gap → PR that updates **SDD and/or AC test** (defect→spec ratchet).

## CI integration

`BlindRebuildGateSuiteTest` (`AC-BR-01`) validates pack integrity on every test run. Full blind rebuild remains a **manual/quarterly** exercise.

## Success criteria (TODO-05 M1)

- [x] Pilot 01 对照演练 + gap log（2026-07-03）
- [x] Spec 回填（§5.22 · API-MP-03/04 · OpenAPI 再生）
- [x] Gate command green on merged implementation
- [ ] Full blind rebuild in isolated worktree (quarterly)
