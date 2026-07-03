-- TODO-12 P0: Ontology persistence core schema (ADR-09 · H2)
-- H2 dialect counterpart to migration-postgresql/V65__ont_p0.sql

CREATE TABLE ont_revision (
    workspace_id        VARCHAR(64)  NOT NULL,
    revision_id         VARCHAR(128) NOT NULL,
    parent_revision_id  VARCHAR(128),
    plan_version_id     VARCHAR(128),
    session_id          VARCHAR(128),
    status              VARCHAR(16)  NOT NULL,
    persistence_mode    VARCHAR(16)  NOT NULL DEFAULT 'FULL',
    change_seq          BIGINT       NOT NULL DEFAULT 0,
    committed_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_revision PRIMARY KEY (workspace_id, revision_id),
    CONSTRAINT ck_ont_revision_status CHECK (
        status IN ('DRAFT', 'COMMITTED', 'ABANDONED', 'ARCHIVED')
    ),
    CONSTRAINT ck_ont_revision_persistence_mode CHECK (
        persistence_mode IN ('FULL', 'PARTIAL')
    )
);

CREATE INDEX idx_ont_revision_ws_status ON ont_revision (workspace_id, status);
CREATE INDEX idx_ont_revision_session ON ont_revision (workspace_id, session_id);

CREATE TABLE ont_revision_head (
    workspace_id  VARCHAR(64)  NOT NULL,
    scope_key     VARCHAR(256) NOT NULL,
    revision_id   VARCHAR(128) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_revision_head PRIMARY KEY (workspace_id, scope_key),
    CONSTRAINT fk_ont_revision_head_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE RESTRICT
);

CREATE TABLE ont_change_log (
    workspace_id   VARCHAR(64)  NOT NULL,
    revision_id    VARCHAR(128) NOT NULL,
    change_seq     BIGINT       NOT NULL,
    change_type    VARCHAR(64)  NOT NULL,
    entity_type    VARCHAR(64),
    entity_id      VARCHAR(128),
    payload_json   JSON         NOT NULL DEFAULT '{}',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_change_log PRIMARY KEY (workspace_id, revision_id, change_seq),
    CONSTRAINT fk_ont_change_log_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_change_log_rev_type
    ON ont_change_log (workspace_id, revision_id, change_type);

CREATE TABLE ont_session (
    workspace_id            VARCHAR(64)  NOT NULL,
    session_id              VARCHAR(128) NOT NULL,
    draft_revision_id       VARCHAR(128) NOT NULL,
    base_revision_id        VARCHAR(128),
    delivery_id             VARCHAR(128),
    trial_revision          INT          NOT NULL DEFAULT 0,
    solve_profile_json      JSON,
    optimizer_result_json   JSON,
    expires_at              TIMESTAMP    NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_session PRIMARY KEY (workspace_id, session_id),
    CONSTRAINT fk_ont_session_draft_revision
        FOREIGN KEY (workspace_id, draft_revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ont_session_base_revision
        FOREIGN KEY (workspace_id, base_revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_ont_session_expires ON ont_session (expires_at);

CREATE TABLE ont_demand (
    workspace_id   VARCHAR(64)   NOT NULL,
    revision_id    VARCHAR(128)  NOT NULL,
    entity_id      VARCHAR(128)  NOT NULL,
    product_code   VARCHAR(64)   NOT NULL,
    pisp_id        VARCHAR(128)  NOT NULL,
    quantity       DOUBLE        NOT NULL DEFAULT 0,
    need_date      DATE          NOT NULL,
    priority       INT           NOT NULL DEFAULT 0,
    source_type    VARCHAR(32)   NOT NULL,
    source_id      VARCHAR(128)  NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_demand PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_demand_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_demand_rev_source
    ON ont_demand (workspace_id, revision_id, source_type, source_id);

CREATE TABLE ont_supply_order (
    workspace_id   VARCHAR(64)   NOT NULL,
    revision_id    VARCHAR(128)  NOT NULL,
    entity_id      VARCHAR(128)  NOT NULL,
    product_code   VARCHAR(64)   NOT NULL,
    pisp_id        VARCHAR(128)  NOT NULL,
    quantity       DOUBLE        NOT NULL DEFAULT 0,
    need_date      DATE          NOT NULL,
    status         VARCHAR(32)   NOT NULL,
    type           VARCHAR(32)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_supply_order PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_supply_order_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_supply_order_rev ON ont_supply_order (workspace_id, revision_id);

CREATE TABLE ont_operation (
    workspace_id                  VARCHAR(64)   NOT NULL,
    revision_id                   VARCHAR(128)  NOT NULL,
    entity_id                     VARCHAR(128)  NOT NULL,
    supply_order_id               VARCHAR(128)  NOT NULL,
    plan_unit_id                  VARCHAR(128),
    sequence_nr                   INT           NOT NULL DEFAULT 0,
    routing_sequence_no           INT           NOT NULL DEFAULT 0,
    operation_name                VARCHAR(256)  NOT NULL,
    production_duration           BIGINT        NOT NULL DEFAULT 0,
    preprocessing_time            BIGINT        NOT NULL DEFAULT 0,
    postprocessing_time           BIGINT        NOT NULL DEFAULT 0,
    segment_index                 INT           NOT NULL DEFAULT 0,
    last_segment                  BOOLEAN       NOT NULL DEFAULT TRUE,
    parallel_group_id             VARCHAR(128),
    locked                        BOOLEAN       NOT NULL DEFAULT FALSE,
    earliest_possible_start_own   TIMESTAMP,
    earliest_possible_end_own     TIMESTAMP,
    earliest_possible_start_total TIMESTAMP,
    earliest_possible_end_total TIMESTAMP,
    latest_desired_start          TIMESTAMP,
    latest_desired_end            TIMESTAMP,
    planned_start_total           TIMESTAMP,
    planned_end_total             TIMESTAMP,
    infeasible                    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_operation PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_operation_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_operation_rev_so
    ON ont_operation (workspace_id, revision_id, supply_order_id);

CREATE TABLE ont_fulfillment (
    workspace_id   VARCHAR(64)   NOT NULL,
    revision_id    VARCHAR(128)  NOT NULL,
    entity_id      VARCHAR(128)  NOT NULL,
    demand_id      VARCHAR(128)  NOT NULL,
    supply_id      VARCHAR(128)  NOT NULL,
    quantity       DOUBLE        NOT NULL DEFAULT 0,
    type           VARCHAR(32)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_fulfillment PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_fulfillment_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_fulfillment_rev_demand
    ON ont_fulfillment (workspace_id, revision_id, demand_id);

CREATE TABLE ont_pispp (
    workspace_id                    VARCHAR(64)   NOT NULL,
    revision_id                     VARCHAR(128)  NOT NULL,
    entity_id                       VARCHAR(128)  NOT NULL,
    pisp_id                         VARCHAR(128)  NOT NULL,
    period_id                       VARCHAR(128)  NOT NULL,
    on_hand                         DOUBLE        NOT NULL DEFAULT 0,
    planned_supply_total            DOUBLE        NOT NULL DEFAULT 0,
    planned_supply_total_mrp        DOUBLE        NOT NULL DEFAULT 0,
    planned_supply_total_optimized  DOUBLE        NOT NULL DEFAULT 0,
    planned_demand_quantity_total   DOUBLE        NOT NULL DEFAULT 0,
    inventory_target_quantity       DOUBLE        NOT NULL DEFAULT 0,
    planned_inventory_level         DOUBLE        NOT NULL DEFAULT 0,
    replenished_inventory_level     DOUBLE        NOT NULL DEFAULT 0,
    stock_shortage_quantity         DOUBLE        NOT NULL DEFAULT 0,
    created_at                      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_pispp PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_pispp_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_pispp_rev_pisp_period
    ON ont_pispp (workspace_id, revision_id, pisp_id, period_id);

CREATE TABLE ont_srp (
    workspace_id          VARCHAR(64)   NOT NULL,
    revision_id           VARCHAR(128)  NOT NULL,
    entity_id             VARCHAR(128)  NOT NULL,
    standard_resource_id  VARCHAR(128)  NOT NULL,
    period_id             VARCHAR(128)  NOT NULL,
    total_capacity        DOUBLE        NOT NULL DEFAULT 0,
    calendar_downtime     DOUBLE        NOT NULL DEFAULT 0,
    technical_downtime    DOUBLE        NOT NULL DEFAULT 0,
    reserved_capacity     DOUBLE        NOT NULL DEFAULT 0,
    available_capacity    DOUBLE        NOT NULL DEFAULT 0,
    free_capacity         DOUBLE        NOT NULL DEFAULT 0,
    overload_capacity     DOUBLE        NOT NULL DEFAULT 0,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_srp PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_srp_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_srp_rev_resource_period
    ON ont_srp (workspace_id, revision_id, standard_resource_id, period_id);

CREATE TABLE ont_resource_capacity_assignment (
    workspace_id                      VARCHAR(64)   NOT NULL,
    revision_id                       VARCHAR(128)  NOT NULL,
    entity_id                         VARCHAR(128)  NOT NULL,
    operation_id                      VARCHAR(128)  NOT NULL,
    operation_on_standard_resource_id VARCHAR(128),
    standard_resource_period_id       VARCHAR(128)  NOT NULL,
    assigned_minutes                  INT           NOT NULL DEFAULT 0,
    operation_total_minutes           INT           NOT NULL DEFAULT 0,
    locked                            BOOLEAN       NOT NULL DEFAULT FALSE,
    parallel_group_id                 VARCHAR(128),
    created_at                        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ont_resource_capacity_assignment
        PRIMARY KEY (workspace_id, revision_id, entity_id),
    CONSTRAINT fk_ont_rca_revision
        FOREIGN KEY (workspace_id, revision_id)
        REFERENCES ont_revision (workspace_id, revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ont_rca_rev_operation
    ON ont_resource_capacity_assignment (workspace_id, revision_id, operation_id);
