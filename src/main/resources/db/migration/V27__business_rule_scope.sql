-- 规则项目级启用范围（主计划 / 详细排程），与业务规则页「规则项目」tab id 一致

CREATE TABLE business_rule_scope (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    rule_type_id VARCHAR(64) NOT NULL,
    enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE,
    enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_business_rule_scope_ws_type UNIQUE (workspace_id, rule_type_id)
);
