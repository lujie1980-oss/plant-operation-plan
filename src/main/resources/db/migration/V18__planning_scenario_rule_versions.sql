-- 计划场景与业务规则版本
CREATE TABLE rule_set_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    rule_set_version_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_rule_set_version_ws_ver UNIQUE (workspace_id, rule_set_version_id)
);

CREATE TABLE planning_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    scenario_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    strategy_id VARCHAR(64),
    rule_set_version_id VARCHAR(32) NOT NULL,
    current_plan_version_id VARCHAR(32),
    previous_plan_version_id VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_planning_scenario_ws_scn UNIQUE (workspace_id, scenario_id)
);

ALTER TABLE workspace ADD COLUMN default_scenario_id VARCHAR(32);

ALTER TABLE plan_version ADD COLUMN scenario_id VARCHAR(32);
ALTER TABLE plan_version ADD COLUMN version_status VARCHAR(16);

ALTER TABLE planning_pipeline_run ADD COLUMN scenario_id VARCHAR(32);
ALTER TABLE planning_pipeline_run ADD COLUMN rule_set_version_id VARCHAR(32);
