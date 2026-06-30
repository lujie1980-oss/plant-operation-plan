# PostgreSQL-only Flyway migrations

Migrations in this folder use **PostgreSQL syntax** (`BIGSERIAL`, `TEXT`, `JSONB`, etc.).

Used when `QUARKUS_PROFILE=postgres` or `prod` (see `application-postgres.properties`).

| Version | 内容 |
|---------|------|
| V0 | Bootstrap marker `ont_migration_bootstrap` |
| **V65** | **P0** `ont_*` 核心表（revision / WAL / session / demand / supply / operation / fulfillment / pispp / srp / rca） |

列级规范：[05-ont-schema.md](../../../docs/sdd/volumes/data/05-ont-schema.md)

**Not here:** legacy `db/migration/V1–V64` remains H2-oriented until ported (see [ont-postgres-dev.md](../../../docs/ont-postgres-dev.md)).
