ALTER TABLE master_plan_allocation ADD COLUMN allocation_id VARCHAR(160);
ALTER TABLE master_plan_allocation ADD COLUMN duration_minutes INT;
ALTER TABLE plan_version ADD COLUMN capacity_strategy VARCHAR(32);
