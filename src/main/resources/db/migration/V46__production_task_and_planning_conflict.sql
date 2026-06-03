-- 车间执行态：批次工序当前计划与状态（与不可变 detail_schedule_operation 快照分离）
CREATE TABLE production_task (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    step_id VARCHAR(256) NOT NULL,
    batch_no VARCHAR(128),
    work_order_no VARCHAR(128) NOT NULL,
    operation_seq INT NOT NULL DEFAULT 0,
    operation_name VARCHAR(256),
    product_code VARCHAR(128),
    line_id VARCHAR(128),
    resource_id VARCHAR(128),
    quantity DECIMAL(18, 4),
    planned_start_ts TIMESTAMP,
    planned_end_ts TIMESTAMP,
    plan_version_id VARCHAR(64),
    execution_state VARCHAR(32) NOT NULL DEFAULT 'UNPLANNED',
    released_ts TIMESTAMP,
    actual_start_ts TIMESTAMP,
    actual_end_ts TIMESTAMP,
    updated_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_production_task_ws_step UNIQUE (workspace_id, step_id)
);

CREATE INDEX idx_production_task_ws_state ON production_task (workspace_id, execution_state);
CREATE INDEX idx_production_task_ws_wo ON production_task (workspace_id, work_order_no);
CREATE INDEX idx_production_task_ws_plan ON production_task (workspace_id, plan_version_id);

-- 已 RUNNING 工序与新发布版本不一致时记录冲突
CREATE TABLE planning_conflict (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    conflict_id VARCHAR(64) NOT NULL,
    step_id VARCHAR(256) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    message VARCHAR(1024),
    detected_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_planning_conflict_ws_id UNIQUE (workspace_id, conflict_id)
);

CREATE INDEX idx_planning_conflict_ws_step ON planning_conflict (workspace_id, step_id, resolved);
CREATE INDEX idx_planning_conflict_ws_plan ON planning_conflict (workspace_id, plan_version_id);
