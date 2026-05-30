-- BOM 批量规则（作用于父项 parent_product_code 的计划批量）
ALTER TABLE bom_component ADD COLUMN lot_size DECIMAL(18,4);
ALTER TABLE bom_component ADD COLUMN lot_size_multiple DECIMAL(18,4);

-- 合并 MRP 工单字段
ALTER TABLE work_order ALTER COLUMN sales_order_no DROP NOT NULL;
ALTER TABLE work_order ALTER COLUMN sales_order_line_no DROP NOT NULL;
ALTER TABLE work_order ADD COLUMN need_date DATE;
ALTER TABLE work_order ADD COLUMN bom_level INT NOT NULL DEFAULT 0;
ALTER TABLE work_order ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'MRP';

CREATE TABLE work_order_pegging (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    work_order_no VARCHAR(64) NOT NULL,
    sales_order_no VARCHAR(64) NOT NULL,
    sales_order_line_no INT NOT NULL,
    finished_product_code VARCHAR(64),
    pegged_qty DECIMAL(18,4) NOT NULL,
    need_date DATE
);

CREATE INDEX idx_wo_pegging_ws_wo ON work_order_pegging (workspace_id, work_order_no);
CREATE INDEX idx_wo_pegging_ws_so ON work_order_pegging (workspace_id, sales_order_no, sales_order_line_no);

CREATE TABLE work_order_bom_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    parent_work_order_no VARCHAR(64) NOT NULL,
    child_work_order_no VARCHAR(64) NOT NULL,
    UNIQUE (workspace_id, parent_work_order_no, child_work_order_no)
);

CREATE INDEX idx_wo_bom_dep_parent ON work_order_bom_dependency (workspace_id, parent_work_order_no);
CREATE INDEX idx_wo_bom_dep_child ON work_order_bom_dependency (workspace_id, child_work_order_no);
