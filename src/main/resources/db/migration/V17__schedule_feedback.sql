CREATE TABLE schedule_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    feedback_id VARCHAR(64) NOT NULL,
    master_plan_version_id VARCHAR(64),
    detail_schedule_version_id VARCHAR(64) NOT NULL,
    work_order_no VARCHAR(128) NOT NULL,
    operation_seq INT NOT NULL DEFAULT 0,
    operation_id VARCHAR(128) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    planned_start TIMESTAMP NOT NULL,
    planned_end TIMESTAMP NOT NULL,
    slot_date DATE NOT NULL,
    duration_minutes INT NOT NULL,
    scope VARCHAR(32) NOT NULL,
    planning_anchor_date DATE NOT NULL,
    feedback_ts TIMESTAMP NOT NULL,
    CONSTRAINT uk_schedule_feedback_ws_op UNIQUE (workspace_id, detail_schedule_version_id, operation_id)
);

CREATE INDEX idx_schedule_feedback_ws_mp ON schedule_feedback (workspace_id, master_plan_version_id);
CREATE INDEX idx_schedule_feedback_ws_slot ON schedule_feedback (workspace_id, resource_id, slot_date);

ALTER TABLE plan_version ADD COLUMN parent_plan_version_id VARCHAR(64);
ALTER TABLE plan_version ADD COLUMN source_detail_schedule_version_id VARCHAR(64);
