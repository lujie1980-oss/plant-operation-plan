CREATE TABLE continuous_production_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    line_id VARCHAR(64) NOT NULL,
    first_product_code VARCHAR(128) NOT NULL DEFAULT '',
    second_product_code VARCHAR(128) NOT NULL DEFAULT '',
    finished_product_code VARCHAR(128) NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX uk_continuous_production_rule_ws ON continuous_production_rule (
    workspace_id, line_id, first_product_code, second_product_code, finished_product_code);

CREATE SEQUENCE IF NOT EXISTS continuous_production_rule_SEQ START WITH 1 INCREMENT BY 50;
