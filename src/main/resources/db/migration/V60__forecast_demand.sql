CREATE TABLE forecast_demand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    forecast_id VARCHAR(128) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    forecast_period VARCHAR(32),
    need_date DATE NOT NULL,
    confidence DECIMAL(5, 4) DEFAULT 0.8,
    UNIQUE (workspace_id, forecast_id)
);

CREATE SEQUENCE IF NOT EXISTS forecast_demand_SEQ START WITH 1 INCREMENT BY 50;
