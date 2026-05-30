-- 启用范围改由 business_rule_scope（规则项目层级）统一管理，删除各规则条目上的冗余字段

ALTER TABLE changeover_matrix DROP COLUMN enable_master_plan;
ALTER TABLE changeover_matrix DROP COLUMN enable_detail_schedule;

ALTER TABLE parallel_operation_rule DROP COLUMN enable_master_plan;
ALTER TABLE parallel_operation_rule DROP COLUMN enable_detail_schedule;

ALTER TABLE operation_transfer_time_rule DROP COLUMN enable_master_plan;
ALTER TABLE operation_transfer_time_rule DROP COLUMN enable_detail_schedule;

ALTER TABLE continuous_production_rule DROP COLUMN enable_master_plan;
ALTER TABLE continuous_production_rule DROP COLUMN enable_detail_schedule;

ALTER TABLE operation_post_processing_rule DROP COLUMN enable_master_plan;
ALTER TABLE operation_post_processing_rule DROP COLUMN enable_detail_schedule;

ALTER TABLE material_lead_time_rule DROP COLUMN enable_master_plan;

ALTER TABLE bom_component DROP COLUMN enable_master_plan;
ALTER TABLE bom_component DROP COLUMN enable_detail_schedule;

ALTER TABLE shift_headcount DROP COLUMN enable_master_plan;
ALTER TABLE shift_headcount DROP COLUMN enable_detail_schedule;

ALTER TABLE sales_order_line DROP COLUMN enable_master_plan;
ALTER TABLE sales_order_line DROP COLUMN enable_detail_schedule;
