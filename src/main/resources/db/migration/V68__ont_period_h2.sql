-- TODO-21 Phase 3: ENT-PER shift-aware period snapshots (ADR-16)

CREATE TABLE ont_period (
    workspace_id      VARCHAR(64)   NOT NULL,
    revision_id       VARCHAR(128)  NOT NULL,
    entity_id         VARCHAR(128)  NOT NULL,
    sequence_nr       INT           NOT NULL,
    start_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    granularity       VARCHAR(16)   NOT NULL,
    shift_id          VARCHAR(64),
    parent_period_id  VARCHAR(128),
    start_date_time   TIMESTAMP,
    end_date_time     TIMESTAMP,
    is_leaf           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_period PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_period_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_period_rev_sequence
    ON ont_period (workspace_id, revision_id, sequence_nr);

CREATE INDEX idx_ont_period_rev_parent
    ON ont_period (workspace_id, revision_id, parent_period_id);
