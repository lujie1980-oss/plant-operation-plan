-- TODO-24 P4: ENT-PRP persistence (ADR-17 · §5.8.2)

CREATE TABLE ont_physical_resource_period (
    workspace_id                 VARCHAR(64)   NOT NULL,
    revision_id                  VARCHAR(128)  NOT NULL,
    entity_id                    VARCHAR(128)  NOT NULL,
    physical_resource_id         VARCHAR(128)  NOT NULL,
    standard_resource_id         VARCHAR(128)  NOT NULL,
    period_id                    VARCHAR(128)  NOT NULL,
    total_capacity               DOUBLE        NOT NULL DEFAULT 0,
    calendar_downtime            DOUBLE        NOT NULL DEFAULT 0,
    scheduler_feedback_minutes   DOUBLE        NOT NULL DEFAULT 0,
    reserved_capacity            DOUBLE        NOT NULL DEFAULT 0,
    available_capacity           DOUBLE        NOT NULL DEFAULT 0,
    overload_capacity            DOUBLE        NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_prp PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_prp_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_prp_rev_physical_period
    ON ont_physical_resource_period (workspace_id, revision_id, physical_resource_id, period_id);

CREATE INDEX idx_ont_prp_rev_standard_period
    ON ont_physical_resource_period (workspace_id, revision_id, standard_resource_id, period_id);
