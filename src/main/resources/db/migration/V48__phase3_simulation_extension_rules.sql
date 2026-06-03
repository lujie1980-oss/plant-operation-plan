-- Phase 3 扩展推演规则：默认关闭，按需启用

INSERT INTO business_rule_scope (workspace_id, rule_type_id, enable_master_plan, enable_detail_schedule)
SELECT w.workspace_id, 'factory-calendar', FALSE, FALSE
FROM workspace w
WHERE NOT EXISTS (
    SELECT 1 FROM business_rule_scope b
    WHERE b.workspace_id = w.workspace_id AND b.rule_type_id = 'factory-calendar');

INSERT INTO business_rule_scope (workspace_id, rule_type_id, enable_master_plan, enable_detail_schedule)
SELECT w.workspace_id, 'feedback-freeze', FALSE, FALSE
FROM workspace w
WHERE NOT EXISTS (
    SELECT 1 FROM business_rule_scope b
    WHERE b.workspace_id = w.workspace_id AND b.rule_type_id = 'feedback-freeze');

INSERT INTO business_rule_scope (workspace_id, rule_type_id, enable_master_plan, enable_detail_schedule)
SELECT w.workspace_id, 'batch-continuous', FALSE, FALSE
FROM workspace w
WHERE NOT EXISTS (
    SELECT 1 FROM business_rule_scope b
    WHERE b.workspace_id = w.workspace_id AND b.rule_type_id = 'batch-continuous');
