-- Bootstrap marker for PostgreSQL Flyway path (TODO-12).
-- Legacy app tables (V1–V64) remain on H2 until a PG port or baseline import.

CREATE TABLE IF NOT EXISTS ont_migration_bootstrap (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ont_migration_bootstrap (id, note)
SELECT 1, 'postgresql migration path active — ont_* from V65+'
WHERE NOT EXISTS (SELECT 1 FROM ont_migration_bootstrap WHERE id = 1);
