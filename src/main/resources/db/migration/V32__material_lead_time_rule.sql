CREATE TABLE material_lead_time_rule (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(255) NOT NULL,
    lead_time_days INT NOT NULL DEFAULT 0,
    enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_material_lead_time UNIQUE (workspace_id, product_code)
);

CREATE SEQUENCE IF NOT EXISTS material_lead_time_rule_SEQ START WITH 1 INCREMENT BY 50;
