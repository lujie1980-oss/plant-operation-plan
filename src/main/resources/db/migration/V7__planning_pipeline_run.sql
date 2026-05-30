CREATE TABLE planning_pipeline_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    capacity_strategy VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_ts TIMESTAMP NOT NULL,
    finished_ts TIMESTAMP,
    duration_ms BIGINT,
    master_plan_version_id VARCHAR(64),
    detail_plan_version_id VARCHAR(64),
    master_plan_score VARCHAR(64),
    error_message VARCHAR(2000)
);

CREATE INDEX idx_pipeline_run_started ON planning_pipeline_run (started_ts DESC);
