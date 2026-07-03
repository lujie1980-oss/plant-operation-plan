-- TODO-14 T0: external_* transactional staging + txn_* internal (§12 · ADR-11)

-- ---------------------------------------------------------------------------
-- external_customer_order → txn_customer_order
-- ---------------------------------------------------------------------------
CREATE TABLE external_customer_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    customer_order_no VARCHAR(128) NOT NULL,
    customer_code VARCHAR(128),
    order_date DATE,
    order_status VARCHAR(64),
    customer_grade VARCHAR(32),
    priority INT,
    kitting_enabled BOOLEAN,
    kitting_granularity VARCHAR(32)
);
CREATE SEQUENCE IF NOT EXISTS external_customer_order_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_customer_order_ws_batch ON external_customer_order (workspace_id, import_batch_id);

CREATE TABLE txn_customer_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    customer_order_no VARCHAR(128) NOT NULL,
    customer_code VARCHAR(128),
    order_date DATE,
    source_status VARCHAR(64),
    customer_grade VARCHAR(32),
    priority INT,
    kitting_enabled BOOLEAN,
    kitting_granularity VARCHAR(32),
    CONSTRAINT uk_txn_customer_order UNIQUE (workspace_id, customer_order_no)
);
CREATE SEQUENCE IF NOT EXISTS txn_customer_order_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_customer_order_line → txn_customer_order_line
-- ---------------------------------------------------------------------------
CREATE TABLE external_customer_order_line (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    customer_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    order_qty DECIMAL(18, 4) NOT NULL,
    uom_code VARCHAR(32)
);
CREATE SEQUENCE IF NOT EXISTS external_customer_order_line_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_customer_order_line_ws_batch ON external_customer_order_line (workspace_id, import_batch_id);

CREATE TABLE txn_customer_order_line (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    customer_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    order_qty DECIMAL(18, 4) NOT NULL,
    uom_code VARCHAR(32),
    CONSTRAINT uk_txn_customer_order_line UNIQUE (workspace_id, customer_order_no, line_no)
);
CREATE SEQUENCE IF NOT EXISTS txn_customer_order_line_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_customer_order_line_delivery → txn_customer_order_line_delivery + txn_demand
-- ---------------------------------------------------------------------------
CREATE TABLE external_customer_order_line_delivery (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    customer_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    delivery_seq INT NOT NULL,
    delivery_qty DECIMAL(18, 4) NOT NULL,
    delivery_min_qty DECIMAL(18, 4),
    delivery_max_qty DECIMAL(18, 4),
    ppq DECIMAL(18, 4),
    delivery_granularity VARCHAR(32),
    early_allow_days INT,
    late_allow_days INT,
    requested_date DATE,
    confirmed_date DATE,
    line_status VARCHAR(64)
);
CREATE SEQUENCE IF NOT EXISTS external_customer_order_line_delivery_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_cold_ws_batch ON external_customer_order_line_delivery (workspace_id, import_batch_id);

CREATE TABLE txn_customer_order_line_delivery (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    customer_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    delivery_seq INT NOT NULL,
    delivery_qty DECIMAL(18, 4) NOT NULL,
    delivery_min_qty DECIMAL(18, 4),
    delivery_max_qty DECIMAL(18, 4),
    ppq DECIMAL(18, 4),
    delivery_granularity VARCHAR(32),
    early_allow_days INT,
    late_allow_days INT,
    requested_date DATE,
    confirmed_date DATE,
    status VARCHAR(64),
    CONSTRAINT uk_txn_cold UNIQUE (workspace_id, customer_order_no, line_no, delivery_seq)
);
CREATE SEQUENCE IF NOT EXISTS txn_customer_order_line_delivery_SEQ START WITH 1 INCREMENT BY 50;

CREATE TABLE txn_demand (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    demand_id VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128),
    quantity DECIMAL(18, 4) NOT NULL,
    need_date DATE,
    priority INT,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    CONSTRAINT uk_txn_demand UNIQUE (workspace_id, demand_id)
);
CREATE SEQUENCE IF NOT EXISTS txn_demand_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_work_order → txn_supply_order + txn_plan_unit
-- ---------------------------------------------------------------------------
CREATE TABLE external_work_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    work_order_no VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    need_date DATE,
    parent_work_order_no VARCHAR(128),
    firm_flag BOOLEAN NOT NULL DEFAULT FALSE,
    source_type VARCHAR(64),
    dispatch_status VARCHAR(64)
);
CREATE SEQUENCE IF NOT EXISTS external_work_order_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_work_order_ws_batch ON external_work_order (workspace_id, import_batch_id);

CREATE TABLE txn_supply_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    supply_order_id VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    need_date DATE,
    parent_supply_order_id VARCHAR(128),
    firm_status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    source_type VARCHAR(64),
    dispatch_status VARCHAR(64),
    CONSTRAINT uk_txn_supply_order UNIQUE (workspace_id, supply_order_id)
);
CREATE SEQUENCE IF NOT EXISTS txn_supply_order_SEQ START WITH 1 INCREMENT BY 50;

CREATE TABLE txn_plan_unit (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    plan_unit_id VARCHAR(128) NOT NULL,
    supply_order_id VARCHAR(128) NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    sequence_no INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_txn_plan_unit UNIQUE (workspace_id, plan_unit_id)
);
CREATE SEQUENCE IF NOT EXISTS txn_plan_unit_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_work_order_operation → txn_operation
-- ---------------------------------------------------------------------------
CREATE TABLE external_work_order_operation (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    work_order_no VARCHAR(128) NOT NULL,
    operation_seq INT NOT NULL,
    operation_code VARCHAR(128),
    operation_name VARCHAR(256),
    planned_start TIMESTAMP,
    planned_end TIMESTAMP,
    plan_unit_seq INT NOT NULL DEFAULT 0
);
CREATE SEQUENCE IF NOT EXISTS external_work_order_operation_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_woo_ws_batch ON external_work_order_operation (workspace_id, import_batch_id);

CREATE TABLE txn_operation (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    supply_order_id VARCHAR(128) NOT NULL,
    plan_unit_id VARCHAR(128),
    routing_sequence_no INT NOT NULL,
    operation_code VARCHAR(128),
    operation_name VARCHAR(256),
    planned_start TIMESTAMP,
    planned_end TIMESTAMP,
    CONSTRAINT uk_txn_operation UNIQUE (workspace_id, operation_id)
);
CREATE SEQUENCE IF NOT EXISTS txn_operation_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_work_order_operation_resource → txn_operation_osr
-- ---------------------------------------------------------------------------
CREATE TABLE external_work_order_operation_resource (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    work_order_no VARCHAR(128) NOT NULL,
    operation_seq INT NOT NULL,
    standard_resource_code VARCHAR(128) NOT NULL,
    resource_priority INT NOT NULL DEFAULT 1,
    setup_time_minutes INT NOT NULL DEFAULT 0,
    process_time_seconds DECIMAL(18, 4)
);
CREATE SEQUENCE IF NOT EXISTS external_work_order_operation_resource_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_woor_ws_batch ON external_work_order_operation_resource (workspace_id, import_batch_id);

CREATE TABLE txn_operation_osr (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    supply_order_id VARCHAR(128) NOT NULL,
    standard_resource_code VARCHAR(128) NOT NULL,
    resource_priority INT NOT NULL DEFAULT 1,
    setup_time_minutes INT NOT NULL DEFAULT 0,
    process_time_seconds DECIMAL(18, 4),
    CONSTRAINT uk_txn_operation_osr UNIQUE (workspace_id, operation_id, standard_resource_code)
);
CREATE SEQUENCE IF NOT EXISTS txn_operation_osr_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_inventory → txn_inventory_balance
-- ---------------------------------------------------------------------------
CREATE TABLE external_inventory (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    on_hand_qty DECIMAL(18, 4) NOT NULL,
    available_qty DECIMAL(18, 4),
    as_of_date DATE
);
CREATE SEQUENCE IF NOT EXISTS external_inventory_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_inventory_ws_batch ON external_inventory (workspace_id, import_batch_id);

CREATE TABLE txn_inventory_balance (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    on_hand_qty DECIMAL(18, 4) NOT NULL,
    available_qty DECIMAL(18, 4),
    as_of_date DATE,
    CONSTRAINT uk_txn_inventory_balance UNIQUE (workspace_id, product_code, stocking_point_code)
);
CREATE SEQUENCE IF NOT EXISTS txn_inventory_balance_SEQ START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- external_purchase_order → txn_purchase_order
-- ---------------------------------------------------------------------------
CREATE TABLE external_purchase_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail VARCHAR(4000),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    purchase_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128),
    order_qty DECIMAL(18, 4) NOT NULL,
    open_qty DECIMAL(18, 4) NOT NULL,
    promised_date DATE,
    po_status VARCHAR(64)
);
CREATE SEQUENCE IF NOT EXISTS external_purchase_order_SEQ START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_external_purchase_order_ws_batch ON external_purchase_order (workspace_id, import_batch_id);

CREATE TABLE txn_purchase_order (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    purchase_order_no VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128),
    order_qty DECIMAL(18, 4) NOT NULL,
    open_qty DECIMAL(18, 4) NOT NULL,
    available_date DATE,
    status VARCHAR(64),
    CONSTRAINT uk_txn_purchase_order UNIQUE (workspace_id, purchase_order_no, line_no)
);
CREATE SEQUENCE IF NOT EXISTS txn_purchase_order_SEQ START WITH 1 INCREMENT BY 50;
