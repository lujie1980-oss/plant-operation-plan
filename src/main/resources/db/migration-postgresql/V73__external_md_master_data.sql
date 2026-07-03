-- TODO-13 M0: external_* staging + md_* internal master data (§11.2–11.3 · ADR-10)

-- ---------------------------------------------------------------------------
-- external_stocking_point → md_stocking_point
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_stocking_point (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    stocking_point_code VARCHAR(128) NOT NULL,
    stocking_point_name VARCHAR(256),
    site_code VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_external_stocking_point_ws_batch ON external_stocking_point (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_stocking_point (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256),
    site_code VARCHAR(64),
    CONSTRAINT uk_md_stocking_point UNIQUE (workspace_id, code)
);

-- ---------------------------------------------------------------------------
-- external_product_in_stocking_point → md_pisp
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_product_in_stocking_point (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    planning_relevant BOOLEAN NOT NULL DEFAULT TRUE,
    ppq INT,
    lot_size INT,
    min_quantity INT,
    max_quantity INT,
    min_qty_strategy VARCHAR(32),
    procurement_type VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_external_pisp_ws_batch ON external_product_in_stocking_point (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_pisp (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    planning_relevant BOOLEAN NOT NULL DEFAULT TRUE,
    ppq INT,
    lot_size INT,
    min_quantity INT,
    max_quantity INT,
    min_qty_strategy VARCHAR(32),
    procurement_type VARCHAR(32),
    CONSTRAINT uk_md_pisp UNIQUE (workspace_id, product_code, stocking_point_code)
);

-- ---------------------------------------------------------------------------
-- external_routing → md_routing
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_routing (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    routing_code VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    path_priority INT NOT NULL DEFAULT 1,
    routing_name VARCHAR(256),
    effective_from DATE,
    effective_to DATE
);

CREATE INDEX IF NOT EXISTS idx_external_routing_ws_batch ON external_routing (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_routing (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    stocking_point_code VARCHAR(128) NOT NULL,
    path_priority INT NOT NULL DEFAULT 1,
    name VARCHAR(256),
    effective_from DATE,
    effective_to DATE,
    CONSTRAINT uk_md_routing UNIQUE (workspace_id, routing_code)
);

-- ---------------------------------------------------------------------------
-- external_routing_step → md_routing_step
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_routing_step (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    operation_code VARCHAR(128),
    operation_name VARCHAR(256),
    standard_resource_group_code VARCHAR(128),
    yield_rate DECIMAL(8, 4) DEFAULT 1.0,
    pre_processing_minutes INT NOT NULL DEFAULT 0,
    scheduling_space_minutes INT NOT NULL DEFAULT 0,
    production_minutes INT NOT NULL DEFAULT 0,
    post_processing_minutes INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_external_routing_step_ws_batch ON external_routing_step (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_routing_step (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    operation_code VARCHAR(128),
    operation_name VARCHAR(256),
    resource_group_code VARCHAR(128),
    yield_rate DECIMAL(8, 4) DEFAULT 1.0,
    pre_processing_minutes INT NOT NULL DEFAULT 0,
    scheduling_space_minutes INT NOT NULL DEFAULT 0,
    production_minutes INT NOT NULL DEFAULT 0,
    post_processing_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_md_routing_step UNIQUE (workspace_id, routing_code, sequence_no)
);

-- ---------------------------------------------------------------------------
-- external_routing_step_on_standard_resource → md_routing_step_osr
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_routing_step_on_standard_resource (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    standard_resource_code VARCHAR(128) NOT NULL,
    resource_priority INT NOT NULL DEFAULT 1,
    setup_time_minutes INT NOT NULL DEFAULT 0,
    process_time_seconds DECIMAL(18, 4),
    process_time_uom VARCHAR(16),
    production_rate DECIMAL(16, 6),
    resource_usage_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    batch_size INT,
    batch_duration_minutes INT
);

CREATE INDEX IF NOT EXISTS idx_external_routing_step_osr_ws_batch ON external_routing_step_on_standard_resource (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_routing_step_osr (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    standard_resource_code VARCHAR(128) NOT NULL,
    resource_priority INT NOT NULL DEFAULT 1,
    setup_time_minutes INT NOT NULL DEFAULT 0,
    process_time_seconds DECIMAL(18, 4),
    process_time_uom VARCHAR(16),
    production_rate DECIMAL(16, 6),
    resource_usage_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    batch_size INT,
    batch_duration_minutes INT,
    CONSTRAINT uk_md_routing_step_osr UNIQUE (workspace_id, routing_code, sequence_no, standard_resource_code)
);

-- ---------------------------------------------------------------------------
-- external_routing_step_input_material → md_routing_step_im
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_routing_step_input_material (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    component_product_code VARCHAR(128) NOT NULL,
    component_qty DECIMAL(18, 6) NOT NULL,
    component_uom VARCHAR(16),
    issue_stocking_point_code VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_external_routing_step_im_ws_batch ON external_routing_step_input_material (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_routing_step_im (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    component_product_code VARCHAR(128) NOT NULL,
    component_qty DECIMAL(18, 6) NOT NULL,
    component_uom VARCHAR(16),
    issue_stocking_point_code VARCHAR(128),
    CONSTRAINT uk_md_routing_step_im UNIQUE (workspace_id, routing_code, sequence_no, component_product_code)
);

-- ---------------------------------------------------------------------------
-- external_routing_step_output_material → md_routing_step_om
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_routing_step_output_material (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    output_product_code VARCHAR(128) NOT NULL,
    output_qty DECIMAL(18, 6) NOT NULL,
    receive_stocking_point_code VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_external_routing_step_om_ws_batch ON external_routing_step_output_material (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_routing_step_om (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    routing_code VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    output_product_code VARCHAR(128) NOT NULL,
    output_qty DECIMAL(18, 6) NOT NULL,
    receive_stocking_point_code VARCHAR(128),
    CONSTRAINT uk_md_routing_step_om UNIQUE (workspace_id, routing_code, sequence_no, output_product_code)
);

-- ---------------------------------------------------------------------------
-- external_resource_group → md_resource_group
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_resource_group (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    resource_group_code VARCHAR(128) NOT NULL,
    resource_group_name VARCHAR(256),
    calendar_code VARCHAR(128),
    resource_efficiency DECIMAL(8, 4) DEFAULT 1.0
);

CREATE INDEX IF NOT EXISTS idx_external_resource_group_ws_batch ON external_resource_group (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_resource_group (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256),
    calendar_code VARCHAR(128),
    resource_efficiency DECIMAL(8, 4) DEFAULT 1.0,
    CONSTRAINT uk_md_resource_group UNIQUE (workspace_id, code)
);

-- ---------------------------------------------------------------------------
-- external_standard_resource → md_standard_resource
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_standard_resource (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    standard_resource_code VARCHAR(128) NOT NULL,
    standard_resource_name VARCHAR(256),
    resource_group_code VARCHAR(128),
    capacity_uom VARCHAR(32),
    is_bottleneck BOOLEAN NOT NULL DEFAULT FALSE,
    resource_efficiency DECIMAL(8, 4) DEFAULT 1.0
);

CREATE INDEX IF NOT EXISTS idx_external_standard_resource_ws_batch ON external_standard_resource (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_standard_resource (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256),
    resource_group_code VARCHAR(128),
    capacity_uom VARCHAR(32),
    is_bottleneck BOOLEAN NOT NULL DEFAULT FALSE,
    resource_efficiency DECIMAL(8, 4) DEFAULT 1.0,
    CONSTRAINT uk_md_standard_resource UNIQUE (workspace_id, code)
);

-- ---------------------------------------------------------------------------
-- external_physical_resource → md_physical_resource
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS external_physical_resource (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    external_row_id VARCHAR(128),
    source_system VARCHAR(64),
    source_revision VARCHAR(128),
    import_batch_id VARCHAR(64),
    imported_at TIMESTAMP,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    quality_checked_at TIMESTAMP,
    quality_issue_codes VARCHAR(2000),
    quality_issue_detail TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCED',
    synced_at TIMESTAMP,
    internal_key VARCHAR(128),
    row_hash VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    physical_resource_code VARCHAR(128) NOT NULL,
    physical_resource_name VARCHAR(256),
    standard_resource_code VARCHAR(128),
    production_line_code VARCHAR(128),
    status VARCHAR(16)
);

CREATE INDEX IF NOT EXISTS idx_external_physical_resource_ws_batch ON external_physical_resource (workspace_id, import_batch_id);

CREATE TABLE IF NOT EXISTS md_physical_resource (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256),
    standard_resource_code VARCHAR(128),
    production_line_code VARCHAR(128),
    status VARCHAR(16),
    CONSTRAINT uk_md_physical_resource UNIQUE (workspace_id, code)
);
