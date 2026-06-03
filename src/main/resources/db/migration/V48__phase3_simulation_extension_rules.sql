-- Phase 3 扩展推演规则：默认关闭，按需启用（幂等）

INSERT INTO business_rule_scope (workspace_id, rule_type_id, enable_master_plan, enable_detail_schedule)
SELECT w.workspace_id, t.rule_type_id, FALSE, FALSE
FROM workspace w
CROSS JOIN (
    VALUES ('factory-calendar'), ('feedback-freeze'), ('batch-continuous')
) AS t(rule_type_id)
WHERE NOT EXISTS (
    SELECT 1 FROM business_rule_scope b
    WHERE b.workspace_id = w.workspace_id AND b.rule_type_id = t.rule_type_id);

-- 若 ensureDefaults 已先创建了这三项（默认 true），统一改为 Phase 3 默认关
UPDATE business_rule_scope
SET enable_master_plan = FALSE, enable_detail_schedule = FALSE
WHERE rule_type_id IN ('factory-calendar', 'feedback-freeze', 'batch-continuous')
  AND enable_master_plan = TRUE
  AND enable_detail_schedule = TRUE;
