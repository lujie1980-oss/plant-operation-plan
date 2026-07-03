# QuarkusTest & AC automation checklist (TODO-28)

## Flyway + Hibernate sequences

- Test profile loads **both** `classpath:db/migration` and `classpath:db/test-migration`.
- `V1000__test_sequences_after_seed.sql` restarts high-traffic `*_SEQ` / identity columns after demo seed inserts to avoid PK collisions in `@QuarkusTest` fixtures.
- **Rule:** new seed migrations that insert explicit low ids must add matching `RESTART WITH` rows in `src/test/resources/db/test-migration/`.

## IAM configuration (`src/test/resources/application.properties`)

| Property | Test value | Purpose |
|----------|------------|---------|
| `plantops.security.dev-mode` | `true` | Bearer-less dev super-admin for most tests |
| `plantops.security.local-login-enabled` | `true` | `IamAcTest` password login path |
| `plantops.security.jwt.secret` | ≥32 chars | JWT issue/verify in tests |
| `plantops.security.oidc.enabled` | `true` | Dual-verify; OIDC optional live test |
| `plantops.sample-data.enabled` | `false` | Deterministic fixtures per test |

## Ontology persistence (integration defaults)

| Property | Test value |
|----------|------------|
| `plantops.ontology.persistence.dual-write-enabled` | `true` |
| `plantops.ontology.persistence.session-enabled` | `true` |
| `plantops.ontology.persistence.restorer-read-enabled` | `true` |
| `plantops.ontology.persistence.bootstrap-head-enabled` | `true` |

## `@SpecRef` traceability (TODO-01)

- Annotate integration tests with `@SpecRef("AC-*")` matching `docs/sdd/core/08-acceptance.md`.
- `SpecRefCoverageTest` fails on unknown AC ids.
- Extend annotations incrementally; do not reference fabricated AC ids.

## Automated AC suites

| AC | Test class |
|----|------------|
| AC-IAM-01~06 | `IamAcTest` · `WorkspaceModuleCatalogSyncTest` |
| AC-INT-01~03 | `IntegrationApiIntegrationTest` |
| AC-PERS-03~04 | `MasterPlanOntologySessionPersistenceIntegrationTest` |
| AC-KN-* | `KnowledgeEffectiveEngineIntegrationTest` |

## CI command (local)

```bash
.\mvnw.cmd test "-Dtest=SpecRefCoverageTest,WorkspaceModuleCatalogSyncTest,IamAcTest"
```
