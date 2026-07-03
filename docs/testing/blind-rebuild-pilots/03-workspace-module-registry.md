# Pilot 03 — Workspace module registry

**moduleId:** `workspace-module-registry`  
**Gate:** AC-IAM-06

## Allowed inputs

- `docs/sdd/volumes/platform/18-19-workspace-platform.md` — §19 module catalog, MOD-* ids
- `docs/sdd/core/08-acceptance.md` — AC-IAM-06, RULE-IAM-06
- `src/main/resources/workspace-modules.yaml` (declarative catalog)

**Forbidden:** existing validator/sync test sources.

## Behaviour

1. **Catalog** — canonical list of MOD-* modules with API route prefixes and default enablement.
2. **Validator** — fails CI if `workspace-modules.yaml` drifts from Java `WorkspaceModuleCatalog` (prefixes, ids).
3. **Rule** — new planning capability must register a MOD-* before IAM toggle/UI nav (AC-IAM-06).

## Acceptance (AC-IAM-06)

- Sync test: YAML ↔ catalog consistency
- IAM test: unknown module id rejected on workspace module toggle

## Non-goals

- Full IAM implementation
- MOD-DI integration UI
