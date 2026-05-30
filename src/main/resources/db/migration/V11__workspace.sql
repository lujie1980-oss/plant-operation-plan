-- Workspace 多数据集隔离（方案 A）
CREATE TABLE workspace (
    workspace_id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_default BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO workspace (workspace_id, name, description, is_default)
VALUES ('default', '默认数据集', '升级前已有数据归入此空间', TRUE);

-- 业务表增加 workspace_id（先可空 + 默认，再 NOT NULL）
ALTER TABLE sales_order_line ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE sales_order_line SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE sales_order_line ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE bom_component ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE bom_component SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE bom_component ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE inventory ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE inventory SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE inventory ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE production_resource ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE production_resource SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE production_resource ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE product_resource ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE product_resource SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE product_resource ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE resource_calendar ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE resource_calendar SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE resource_calendar ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE shift_headcount ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE shift_headcount SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE shift_headcount ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE production_line ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE production_line SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE production_line ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE changeover_matrix ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE changeover_matrix SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE changeover_matrix ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE work_order ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE work_order SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE work_order ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE plan_version ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE plan_version SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE plan_version ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE master_plan_allocation ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE master_plan_allocation SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE master_plan_allocation ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE line_opening_decision ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE line_opening_decision SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE line_opening_decision ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE detail_schedule_operation ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE detail_schedule_operation SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE detail_schedule_operation ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE kitting_result ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE kitting_result SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE kitting_result ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE shortage_recommendation ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE shortage_recommendation SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE shortage_recommendation ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE planning_event ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE planning_event SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE planning_event ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE plan_dispatch ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE plan_dispatch SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE plan_dispatch ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE system_parameter ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE system_parameter SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE system_parameter ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE planning_pipeline_run ADD COLUMN workspace_id VARCHAR(64) DEFAULT 'default';
UPDATE planning_pipeline_run SET workspace_id = 'default' WHERE workspace_id IS NULL;
ALTER TABLE planning_pipeline_run ALTER COLUMN workspace_id SET NOT NULL;
