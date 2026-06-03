ALTER TABLE operation_transfer_time_rule ADD COLUMN max_transfer_minutes INT NOT NULL DEFAULT 0;
UPDATE operation_transfer_time_rule SET max_transfer_minutes = transfer_minutes;

ALTER TABLE operation_transfer_time_rule ADD COLUMN link_mode VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE operation_transfer_time_rule ADD COLUMN delay_start_minutes INT NOT NULL DEFAULT 0;
