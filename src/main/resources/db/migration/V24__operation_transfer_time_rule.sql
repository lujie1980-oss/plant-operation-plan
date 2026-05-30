CREATE TABLE operation_transfer_time_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(128) NOT NULL,
    from_operation_name VARCHAR(128) NOT NULL,
    to_operation_name VARCHAR(128) NOT NULL,
    transfer_minutes INT NOT NULL,
    min_transfer_minutes INT NOT NULL
);

CREATE UNIQUE INDEX uk_operation_transfer_time_rule_ws ON operation_transfer_time_rule (
    workspace_id, product_code, from_operation_name, to_operation_name);

CREATE SEQUENCE IF NOT EXISTS operation_transfer_time_rule_SEQ START WITH 1 INCREMENT BY 50;
