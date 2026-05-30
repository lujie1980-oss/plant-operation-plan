CREATE TABLE operation_post_processing_rule (
    id BIGINT NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(255) NOT NULL,
    operation_name VARCHAR(255) NOT NULL DEFAULT '*',
    post_processing_minutes INT NOT NULL DEFAULT 0,
    enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE,
    enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_op_post_proc UNIQUE (workspace_id, product_code, operation_name)
);

CREATE SEQUENCE IF NOT EXISTS operation_post_processing_rule_SEQ START WITH 1 INCREMENT BY 50;
