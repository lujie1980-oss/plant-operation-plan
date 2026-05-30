CREATE TABLE sales_order_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sales_order_no VARCHAR(64) NOT NULL,
    sales_order_line_no INT NOT NULL,
    customer_code VARCHAR(64),
    product_code VARCHAR(64) NOT NULL,
    order_qty DECIMAL(18,4) NOT NULL,
    uom VARCHAR(16),
    promise_date DATE,
    due_date DATE NOT NULL,
    priority INT DEFAULT 5,
    expedite_level INT DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    schedule_lock_flag BOOLEAN DEFAULT FALSE,
    last_modified_ts TIMESTAMP,
    UNIQUE (sales_order_no, sales_order_line_no)
);

CREATE TABLE bom_component (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bom_id VARCHAR(64) NOT NULL,
    bom_version VARCHAR(32) NOT NULL,
    parent_product_code VARCHAR(64) NOT NULL,
    component_product_code VARCHAR(64) NOT NULL,
    component_qty DECIMAL(18,4) NOT NULL,
    is_critical_component BOOLEAN DEFAULT FALSE
);

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stocking_point_code VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    onhand_qty DECIMAL(18,4) NOT NULL,
    reserved_qty DECIMAL(18,4) DEFAULT 0,
    quality_hold_qty DECIMAL(18,4) DEFAULT 0,
    in_transit_qty DECIMAL(18,4) DEFAULT 0,
    eta_date DATE
);

CREATE TABLE production_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id VARCHAR(64) NOT NULL UNIQUE,
    resource_group VARCHAR(64),
    area_id VARCHAR(64) NOT NULL,
    bottleneck BOOLEAN DEFAULT FALSE,
    run_rate_per_hour DECIMAL(18,4) DEFAULT 1
);

CREATE TABLE product_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    setup_time_minutes INT DEFAULT 0,
    UNIQUE (product_code, resource_id)
);

CREATE TABLE resource_calendar (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id VARCHAR(64) NOT NULL,
    shift_id VARCHAR(32) NOT NULL,
    calendar_date DATE NOT NULL,
    available_capacity_minutes INT NOT NULL,
    unavailable_capacity_minutes INT DEFAULT 0
);

CREATE TABLE shift_headcount (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_id VARCHAR(64) NOT NULL,
    shift_id VARCHAR(32) NOT NULL,
    calendar_date DATE NOT NULL,
    available_headcount INT NOT NULL,
    UNIQUE (area_id, shift_id, calendar_date)
);

CREATE TABLE production_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    line_id VARCHAR(64) NOT NULL UNIQUE,
    area_id VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    line_min_headcount INT NOT NULL,
    line_capacity_per_shift INT NOT NULL
);

CREATE TABLE changeover_matrix (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id VARCHAR(64) NOT NULL,
    from_product_code VARCHAR(64) NOT NULL,
    to_product_code VARCHAR(64) NOT NULL,
    setup_minutes INT NOT NULL,
    UNIQUE (resource_id, from_product_code, to_product_code)
);

CREATE TABLE work_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_no VARCHAR(64) NOT NULL UNIQUE,
    sales_order_no VARCHAR(64) NOT NULL,
    sales_order_line_no INT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    sequence_no INT NOT NULL
);

CREATE TABLE plan_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_version_id VARCHAR(64) NOT NULL UNIQUE,
    plan_type VARCHAR(32) NOT NULL,
    plan_generated_ts TIMESTAMP NOT NULL,
    changed_by VARCHAR(64),
    change_source VARCHAR(64),
    solve_duration_ms BIGINT,
    score VARCHAR(128)
);

CREATE TABLE master_plan_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_version_id VARCHAR(64) NOT NULL,
    sales_order_no VARCHAR(64) NOT NULL,
    sales_order_line_no INT NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    slot_index INT NOT NULL,
    slot_date DATE NOT NULL,
    shift_id VARCHAR(32)
);

CREATE TABLE line_opening_decision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_version_id VARCHAR(64) NOT NULL,
    area_id VARCHAR(64) NOT NULL,
    line_id VARCHAR(64) NOT NULL,
    shift_id VARCHAR(32) NOT NULL,
    calendar_date DATE NOT NULL,
    opened BOOLEAN NOT NULL,
    suggested_headcount INT
);

CREATE TABLE detail_schedule_operation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_version_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(64) NOT NULL,
    work_order_no VARCHAR(64) NOT NULL,
    line_id VARCHAR(64) NOT NULL,
    sequence_index INT NOT NULL,
    start_minute INT NOT NULL,
    end_minute INT NOT NULL,
    pinned BOOLEAN DEFAULT FALSE
);

CREATE TABLE kitting_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    computed_ts TIMESTAMP NOT NULL,
    sales_order_no VARCHAR(64) NOT NULL,
    sales_order_line_no INT NOT NULL,
    kitting_status VARCHAR(32) NOT NULL,
    shortage_reason VARCHAR(512)
);

CREATE TABLE shortage_recommendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shortage_id VARCHAR(64) NOT NULL UNIQUE,
    plan_version_id VARCHAR(64),
    shortage_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    area_id VARCHAR(64),
    shift_id VARCHAR(32),
    line_id VARCHAR(64),
    evidence_json CLOB,
    recommended_action VARCHAR(64) NOT NULL,
    impact_orders_json CLOB,
    created_ts TIMESTAMP NOT NULL
);

CREATE TABLE planning_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_ts TIMESTAMP NOT NULL,
    payload_json CLOB,
    reschedule_level VARCHAR(8),
    processed BOOLEAN DEFAULT FALSE
);

CREATE TABLE plan_dispatch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_version_id VARCHAR(64) NOT NULL,
    dispatched_ts TIMESTAMP NOT NULL,
    target_system VARCHAR(32) NOT NULL
);

CREATE TABLE system_parameter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    param_id VARCHAR(64) NOT NULL UNIQUE,
    param_value VARCHAR(512) NOT NULL,
    description VARCHAR(256)
);
