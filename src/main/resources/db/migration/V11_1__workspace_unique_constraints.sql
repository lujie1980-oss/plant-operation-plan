-- 删除旧全局唯一约束并建立 (workspace_id, …) 组合唯一（H2）
ALTER TABLE sales_order_line DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_2;
CREATE UNIQUE INDEX uk_sales_order_line_ws ON sales_order_line (workspace_id, sales_order_no, sales_order_line_no);

ALTER TABLE production_resource DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_3;
CREATE UNIQUE INDEX uk_production_resource_ws ON production_resource (workspace_id, resource_id);

ALTER TABLE product_resource DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_5;
CREATE UNIQUE INDEX uk_product_resource_ws ON product_resource (workspace_id, product_code, resource_id);

ALTER TABLE shift_headcount DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_7;
CREATE UNIQUE INDEX uk_shift_headcount_ws ON shift_headcount (workspace_id, area_id, shift_id, calendar_date);

ALTER TABLE production_line DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_9;
CREATE UNIQUE INDEX uk_production_line_ws ON production_line (workspace_id, line_id);

ALTER TABLE changeover_matrix DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_A;
CREATE UNIQUE INDEX uk_changeover_matrix_ws ON changeover_matrix (workspace_id, resource_id, from_product_code, to_product_code);

ALTER TABLE work_order DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_C;
CREATE UNIQUE INDEX uk_work_order_ws ON work_order (workspace_id, work_order_no);

ALTER TABLE plan_version DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_E;
CREATE UNIQUE INDEX uk_plan_version_ws ON plan_version (workspace_id, plan_version_id);

ALTER TABLE shortage_recommendation DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_11;
CREATE UNIQUE INDEX uk_shortage_recommendation_ws ON shortage_recommendation (workspace_id, shortage_id);

ALTER TABLE planning_event DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_13;
CREATE UNIQUE INDEX uk_planning_event_ws ON planning_event (workspace_id, event_id);

ALTER TABLE system_parameter DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_15;
CREATE UNIQUE INDEX uk_system_parameter_ws ON system_parameter (workspace_id, param_id);

ALTER TABLE planning_pipeline_run DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_17;
CREATE UNIQUE INDEX uk_planning_pipeline_run_ws ON planning_pipeline_run (workspace_id, run_id);
