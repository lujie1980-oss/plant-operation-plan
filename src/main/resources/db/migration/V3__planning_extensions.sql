ALTER TABLE work_order ADD COLUMN parent_work_order_no VARCHAR(64);

ALTER TABLE master_plan_allocation ADD COLUMN work_order_no VARCHAR(128);
ALTER TABLE master_plan_allocation ADD COLUMN product_code VARCHAR(128);
