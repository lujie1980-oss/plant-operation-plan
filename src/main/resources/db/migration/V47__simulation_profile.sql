-- 详细排程推演场景配置（SimulationProfile，Phase 2）

CREATE TABLE simulation_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    profile_id VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    layer VARCHAR(32) NOT NULL DEFAULT 'DETAIL_SCHEDULE',
    master_plan_version_id VARCHAR(64),
    config_json CLOB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_simulation_profile_ws_pid UNIQUE (workspace_id, profile_id)
);

CREATE INDEX idx_simulation_profile_ws_layer_active
    ON simulation_profile (workspace_id, layer, active);
