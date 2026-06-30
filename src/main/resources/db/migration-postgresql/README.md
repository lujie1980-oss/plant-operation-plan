# PostgreSQL-only Flyway migrations

Migrations in this folder use **PostgreSQL syntax** (`BIGSERIAL`, `TEXT`, `JSONB`, etc.).

Used when `QUARKUS_PROFILE=postgres` or `prod` (see `application-postgres.properties`).

**Scope (TODO-12):**

- `V65+` — `ont_*` schema (revision, WAL, demand/supply/fulfillment, …)
- AC-PERS integration tests run against PostgreSQL

**Not here:** legacy `db/migration/V1–V64` remains H2-oriented until ported (see `docs/ont-postgres-dev.md`).
