-- 同工序多资源：占用优先级（数值越小越优先，默认 1）
ALTER TABLE product_resource ADD COLUMN resource_priority INT DEFAULT 1;
UPDATE product_resource SET resource_priority = 1 WHERE resource_priority IS NULL;
