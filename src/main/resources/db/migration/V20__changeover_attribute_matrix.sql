-- 换型矩阵：由「资源+产品→产品」改为「工序+属性前后值」

DROP INDEX IF EXISTS uk_changeover_matrix_ws;
DROP TABLE IF EXISTS changeover_matrix;

CREATE TABLE changeover_matrix (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    attribute_key VARCHAR(64) NOT NULL,
    from_attribute_value VARCHAR(128) NOT NULL,
    to_attribute_value VARCHAR(128) NOT NULL,
    setup_minutes INT NOT NULL
);

CREATE UNIQUE INDEX uk_changeover_matrix_ws ON changeover_matrix (
    workspace_id, operation_name, attribute_key, from_attribute_value, to_attribute_value);
