-- U 型线规则：机台字段改为产线 ID（线体）

ALTER TABLE u_line_pair_rule ADD COLUMN IF NOT EXISTS line_id VARCHAR(64);
UPDATE u_line_pair_rule SET line_id = resource_id WHERE line_id IS NULL;
ALTER TABLE u_line_pair_rule ALTER COLUMN line_id SET NOT NULL;

DROP INDEX IF EXISTS uk_u_line_pair_rule_ws;
ALTER TABLE u_line_pair_rule DROP COLUMN IF EXISTS resource_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_u_line_pair_rule_ws ON u_line_pair_rule (
    workspace_id, line_id, first_product_code, second_product_code);
