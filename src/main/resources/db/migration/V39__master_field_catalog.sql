-- 主数据字段目录（General / Custom）与 product_resource 扩展 JSON

CREATE TABLE master_field_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    field_category VARCHAR(16) NOT NULL,
    data_type VARCHAR(16) NOT NULL,
    label_zh VARCHAR(256) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    visible_in_grid BOOLEAN NOT NULL DEFAULT TRUE,
    used_in_rules BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'
);

CREATE UNIQUE INDEX uk_master_field_def ON master_field_definition (workspace_id, entity_type, field_key);
CREATE SEQUENCE IF NOT EXISTS master_field_definition_SEQ START WITH 1 INCREMENT BY 50;

ALTER TABLE product_resource ADD COLUMN IF NOT EXISTS extensions JSON;

-- 默认 workspace：产品工艺 Custom 字段目录（值存 extensions，legacy 列保留双写过渡期）
INSERT INTO master_field_definition
    (id, workspace_id, entity_type, field_key, field_category, data_type, label_zh, used_in_rules, display_order, source)
VALUES
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'bomLevel', 'CUSTOM', 'STRING', 'A/B料', FALSE, 10, 'PLATFORM'),
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'wireMaterial', 'CUSTOM', 'STRING', '线材', TRUE, 20, 'PLATFORM'),
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'keyMaterial', 'CUSTOM', 'STRING', '关键物料', TRUE, 30, 'PLATFORM'),
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'maleFemaleEnd', 'CUSTOM', 'STRING', '公母端', FALSE, 40, 'PLATFORM'),
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'totalBranch', 'CUSTOM', 'STRING', '总成分支', TRUE, 50, 'PLATFORM'),
    (NEXT VALUE FOR master_field_definition_SEQ, 'default', 'PRODUCT_RESOURCE', 'standardLabor', 'CUSTOM', 'NUMBER', '制造人力', FALSE, 60, 'PLATFORM');
