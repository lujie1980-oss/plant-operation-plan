CREATE TABLE slitting_solver_run (
    id BIGINT NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    plan_version_id VARCHAR(64),
    master_node_id VARCHAR(128),
    session_id VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    started_ts TIMESTAMP NOT NULL,
    finished_ts TIMESTAMP,
    duration_ms BIGINT,
    score VARCHAR(128),
    summary VARCHAR(512),
    error_message VARCHAR(2000),
    execution_log CLOB,
    CONSTRAINT pk_slitting_solver_run PRIMARY KEY (id),
    CONSTRAINT uk_slitting_solver_run_ws_run UNIQUE (workspace_id, run_id)
);

CREATE INDEX idx_slitting_solver_run_ws_started ON slitting_solver_run (workspace_id, started_ts DESC);

CREATE SEQUENCE IF NOT EXISTS slitting_solver_run_SEQ START WITH 1 INCREMENT BY 50;
