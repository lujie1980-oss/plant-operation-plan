ALTER TABLE product_resource ADD COLUMN sequence_no INT DEFAULT 1;
ALTER TABLE product_resource ADD COLUMN operation_name VARCHAR(128);
ALTER TABLE product_resource ADD COLUMN process_time_seconds DECIMAL(18,4);
