-- U 型线同机台配对规则：两个半品在指定机台上需同时加工

CREATE TABLE u_line_pair_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    first_product_code VARCHAR(64) NOT NULL,
    second_product_code VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX uk_u_line_pair_rule_ws ON u_line_pair_rule (
    workspace_id, resource_id, first_product_code, second_product_code);

CREATE SEQUENCE IF NOT EXISTS u_line_pair_rule_SEQ START WITH 1 INCREMENT BY 50;
