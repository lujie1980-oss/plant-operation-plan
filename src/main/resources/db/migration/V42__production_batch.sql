-- 排程域：生产批次（与 MRP 无关）
CREATE TABLE production_batch (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    batch_no VARCHAR(128) NOT NULL,
    work_order_no VARCHAR(128) NOT NULL,
    batch_seq INT NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    kitting_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    split_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    pending_schedule_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_production_batch_ws_no UNIQUE (workspace_id, batch_no)
);

CREATE INDEX idx_production_batch_wo ON production_batch (workspace_id, work_order_no, status);

ALTER TABLE work_order ADD COLUMN batch_split_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

ALTER TABLE detail_schedule_operation ADD COLUMN batch_no VARCHAR(128);
