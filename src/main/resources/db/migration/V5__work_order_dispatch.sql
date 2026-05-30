ALTER TABLE work_order ADD COLUMN dispatch_status VARCHAR(32) DEFAULT 'PENDING';
ALTER TABLE work_order ADD COLUMN dispatched_ts TIMESTAMP;

ALTER TABLE kitting_result ADD COLUMN work_order_no VARCHAR(64);
