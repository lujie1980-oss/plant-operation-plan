CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workspace_id VARCHAR(64) NOT NULL,
    material_code VARCHAR(128) NOT NULL,
    material_name VARCHAR(256),
    uom_code VARCHAR(64),
    material_type VARCHAR(64),
    site_code VARCHAR(64)
);

CREATE UNIQUE INDEX uk_material_ws ON material (workspace_id, material_code);

