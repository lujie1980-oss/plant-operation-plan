-- 规则启用范围：主计划 / 详细排程（默认均启用）

ALTER TABLE changeover_matrix ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE changeover_matrix ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE parallel_operation_rule ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE parallel_operation_rule ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE operation_transfer_time_rule ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE operation_transfer_time_rule ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE continuous_production_rule ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE continuous_production_rule ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE bom_component ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE bom_component ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE shift_headcount ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE shift_headcount ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE sales_order_line ADD COLUMN enable_master_plan BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sales_order_line ADD COLUMN enable_detail_schedule BOOLEAN NOT NULL DEFAULT TRUE;
