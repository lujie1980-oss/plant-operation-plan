-- 并行工序规则表（由原 u_line_pair_rule 重命名）

ALTER TABLE u_line_pair_rule RENAME TO parallel_operation_rule;

DROP INDEX IF EXISTS uk_u_line_pair_rule_ws;
CREATE UNIQUE INDEX uk_parallel_operation_rule_ws ON parallel_operation_rule (
    workspace_id, line_id, first_product_code, second_product_code);

CREATE SEQUENCE IF NOT EXISTS parallel_operation_rule_SEQ START WITH 1 INCREMENT BY 50;
