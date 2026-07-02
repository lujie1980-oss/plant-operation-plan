-- TODO-17 §16 BusinessRules master data tables

ALTER TABLE production_resource ADD COLUMN IF NOT EXISTS resource_efficiency DECIMAL(8, 4) NOT NULL DEFAULT 1.0;

CREATE TABLE IF NOT EXISTS delivery_date_strategy (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    customer_code VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    delivery_granularity VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    early_allow_days INT NOT NULL DEFAULT 0,
    late_allow_days INT NOT NULL DEFAULT 0,
    early_penalty_coef DECIMAL(12, 4) NOT NULL DEFAULT 1.0,
    late_penalty_coef DECIMAL(12, 4) NOT NULL DEFAULT 1.0,
    CONSTRAINT uk_delivery_date_strategy UNIQUE (workspace_id, customer_code, product_code)
);

CREATE TABLE IF NOT EXISTS supply_quantity_rule (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    lot_size INT NOT NULL DEFAULT 1,
    min_quantity INT NOT NULL DEFAULT 1,
    max_quantity INT NOT NULL DEFAULT 99999,
    min_qty_strategy VARCHAR(32) NOT NULL DEFAULT 'PLAN_AT_MIN',
    CONSTRAINT uk_supply_quantity_rule UNIQUE (workspace_id, product_code, stocking_point_code)
);

CREATE TABLE IF NOT EXISTS routing_step_timing_rule (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    pre_processing_minutes INT NOT NULL DEFAULT 0,
    scheduling_space_minutes INT NOT NULL DEFAULT 0,
    production_minutes INT NOT NULL DEFAULT 0,
    post_processing_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_routing_step_timing_rule UNIQUE (workspace_id, routing_code, sequence_no)
);

CREATE TABLE IF NOT EXISTS routing_step_resource_rule (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    standard_resource_code VARCHAR(128) NOT NULL,
    resource_priority INT NOT NULL DEFAULT 1,
    production_rate DECIMAL(16, 6) NOT NULL DEFAULT 1.0,
    resource_usage_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    batch_size INT NOT NULL DEFAULT 1,
    batch_duration_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_routing_step_resource_rule UNIQUE (workspace_id, standard_resource_code)
);
