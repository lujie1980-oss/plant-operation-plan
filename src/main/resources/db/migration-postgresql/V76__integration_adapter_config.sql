CREATE TABLE IF NOT EXISTS workspace_adapter_config (
    workspace_id VARCHAR(64) NOT NULL,
    adapter_id VARCHAR(30) NOT NULL,
    config_json VARCHAR(4000),
    configured BOOLEAN NOT NULL DEFAULT FALSE,
    last_run_at TIMESTAMP,
    last_status VARCHAR(32),
    last_message VARCHAR(512),
    PRIMARY KEY (workspace_id, adapter_id)
);
