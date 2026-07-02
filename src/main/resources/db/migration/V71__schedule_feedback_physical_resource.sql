-- TODO-24 P5: attribute scheduler feedback to physical resource (line)

ALTER TABLE schedule_feedback ADD COLUMN physical_resource_id VARCHAR(128);
