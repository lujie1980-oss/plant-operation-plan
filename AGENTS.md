# AGENTS.md

## Cursor Cloud specific instructions

Product: **Plant Operation Plan** — a single-factory APS (advanced planning & scheduling) covering scenarios S01–S07, with optimization driven by Timefold (S04 master plan, S05 detail schedule). Two services:

- **Backend**: Quarkus 3.17 / Java 21, H2 (file DB) + Flyway, Timefold solver. Runs on **port 8080**.
- **Frontend**: React + Vite (multi-page, Gantt charts). Dev server on **port 5173**, proxies `/api` to the backend.

Standard build/test/run commands live in `README.md` and `pom.xml` / `frontend/package.json` — use those. Notes below are only the non-obvious things.

### Running the services (dev mode)
- Backend: `./mvnw quarkus:dev` (from repo root) → http://localhost:8080 , Swagger UI at `/q/swagger-ui`.
- Frontend: from `frontend/`, run `VITE_BACKEND_URL=http://localhost:8080 npm run dev` → http://localhost:5173/#/ .
  - **Gotcha**: `frontend/vite.config.ts` defaults the `/api` proxy target to `http://localhost:8081`, but the backend dev server listens on **8080**. Without setting `VITE_BACKEND_URL=http://localhost:8080`, all `/api` calls from the UI return 404.

### Gotchas
- There is **no** health extension: `/q/health/*` returns 404. Do not use it as a readiness probe. Use `/q/openapi` (200) or any `/api/...` endpoint to check the backend is up.
- Quarkus dev prints a "Help improve Quarkus" analytics prompt, but `quarkus.analytics.disabled=true` is set and it auto-continues after ~10s, so `quarkus:dev` starts unattended.
- H2 is a **file** DB at `./data/plantops.*` and persists across restarts. Sample data auto-loads on empty DB (`plantops.sample-data.enabled=true`, resource `sample-data/factory-dunan-demo-lite.json`). To force a fresh reload, set `plantops.sample-data.force-reload=true`, start once, then set it back to `false`. Tests use an in-memory H2 (`%test` profile), so they are isolated from the file DB.
- **Timefold solves are slow**: the full pipeline (`POST /api/v1/planning/run-full-pipeline`) takes ~3 min, a master-plan solve ~30s. Tests that invoke solvers take minutes; the full `./mvnw test` suite runs several minutes — do not assume it hung.

### Known pre-existing test failure (not an environment issue)
- `com.plantops.scenario.DetailSchedulePlanningPreviewServiceTest.previewMemorySolveReturnsScoreAndSchedule` fails **deterministically** with `IllegalStateException: Solving failed ... ContextNotActiveException` (the solver's background thread lazily queries the DB after the CDI request/transaction context closes). This is an application/test bug unrelated to environment setup; the other 151 tests pass.
