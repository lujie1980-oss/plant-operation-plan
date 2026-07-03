-- TODO-11: multi-path ENT-RT via routing_path_priority (RULE-MRP-01)

ALTER TABLE product_resource ADD COLUMN routing_path_priority INT DEFAULT 1;
UPDATE product_resource SET routing_path_priority = 1 WHERE routing_path_priority IS NULL;
