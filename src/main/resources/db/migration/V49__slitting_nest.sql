-- 分切排样域
CREATE TABLE master_roll (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    roll_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    material_code VARCHAR(64),
    kerf_longitudinal_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    kerf_transverse_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_master_roll_ws_code UNIQUE (workspace_id, roll_code)
);

CREATE TABLE child_slitting_order (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    order_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    quantity INT NOT NULL DEFAULT 1,
    priority INT NOT NULL DEFAULT 0,
    sales_order_no VARCHAR(128),
    sales_order_line_no INT,
    work_order_no VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_child_slitting_order_ws_code UNIQUE (workspace_id, order_code)
);

CREATE INDEX idx_child_slitting_order_so ON child_slitting_order (workspace_id, sales_order_no, sales_order_line_no);
CREATE INDEX idx_child_slitting_order_wo ON child_slitting_order (workspace_id, work_order_no);

CREATE TABLE intermediate_roll_catalog (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    spec_code VARCHAR(128) NOT NULL,
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    cutting_method VARCHAR(32) NOT NULL,
    kerf_mm DECIMAL(18, 4) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_intermediate_catalog_ws_code UNIQUE (workspace_id, spec_code)
);

CREATE TABLE slitting_plan_version (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    score VARCHAR(64),
    utilization_pct DECIMAL(8, 4),
    solve_duration_ms BIGINT,
    solver_phase VARCHAR(32),
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_slitting_plan_version_ws_id UNIQUE (workspace_id, plan_version_id)
);

CREATE TABLE slitting_plan_master_roll (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    master_roll_id BIGINT NOT NULL
);

CREATE INDEX idx_slitting_plan_master_roll_plan ON slitting_plan_master_roll (workspace_id, plan_version_id);

CREATE TABLE slitting_plan_child_order (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    child_slitting_order_id BIGINT NOT NULL
);

CREATE INDEX idx_slitting_plan_child_order_plan ON slitting_plan_child_order (workspace_id, plan_version_id);

CREATE TABLE slitting_roll_node (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    parent_node_id VARCHAR(64),
    width_mm DECIMAL(18, 4) NOT NULL,
    length_mm DECIMAL(18, 4) NOT NULL,
    thickness_mm DECIMAL(18, 4),
    cutting_method VARCHAR(32),
    kerf_mm DECIMAL(18, 4),
    source_spec_code VARCHAR(128),
    source_child_order_id BIGINT,
    source_master_roll_id BIGINT,
    CONSTRAINT uk_slitting_roll_node UNIQUE (workspace_id, plan_version_id, node_id)
);

CREATE TABLE slitting_assignment (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_version_id VARCHAR(64) NOT NULL,
    assignment_id VARCHAR(64) NOT NULL,
    child_node_id VARCHAR(64) NOT NULL,
    parent_node_id VARCHAR(64) NOT NULL,
    pos_x_mm DECIMAL(18, 4) NOT NULL,
    pos_y_mm DECIMAL(18, 4) NOT NULL,
    rotated BOOLEAN NOT NULL DEFAULT FALSE,
    sequence INT,
    CONSTRAINT uk_slitting_assignment UNIQUE (workspace_id, plan_version_id, assignment_id)
);
