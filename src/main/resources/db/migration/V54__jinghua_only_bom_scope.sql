-- 晶华专用：BOM 关联字段 + 清理其它演示 workspace + 默认 workspace 改为晶华

ALTER TABLE master_roll ADD COLUMN product_code VARCHAR(256);
ALTER TABLE master_roll ADD COLUMN finished_product_code VARCHAR(256);
ALTER TABLE child_slitting_order ADD COLUMN product_code VARCHAR(256);
ALTER TABLE child_slitting_order ADD COLUMN finished_product_code VARCHAR(256);

UPDATE workspace SET is_default = FALSE;
UPDATE workspace SET is_default = TRUE WHERE workspace_id = 'jinghua';

-- 清理非晶华 workspace 业务数据后删除 workspace 行
DELETE FROM detail_schedule_operation WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM master_plan_allocation WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM line_opening_decision WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM shortage_recommendation WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM plan_dispatch WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM planning_event WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM plan_version WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM kitting_result WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM work_order WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM changeover_matrix WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM product_resource WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM production_line WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM resource_calendar WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM shift_headcount WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM inventory WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM bom_component WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM sales_order_line WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM production_resource WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM planning_pipeline_run WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM system_parameter WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');

DELETE FROM slitting_assignment WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM slitting_roll_node WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM slitting_plan_child_order WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM slitting_plan_master_roll WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM slitting_plan_version WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM child_slitting_order WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM intermediate_roll_catalog WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');
DELETE FROM master_roll WHERE workspace_id IN ('mahle', 'dunan-lite', 'default');

DELETE FROM workspace WHERE workspace_id IN ('mahle', 'dunan-lite');

-- 晶华母卷 / 分切订单 BOM 根（与 MRP 测试用例一致）
UPDATE master_roll
SET product_code = 'M69/305*600M/1R/深黄',
    finished_product_code = 'M69/305*600M/1R/深黄'
WHERE workspace_id = 'jinghua' AND roll_code LIKE 'MR-M69/305%';

UPDATE child_slitting_order
SET product_code = CASE order_code
    WHEN 'CO-M69/730mm/深黄/3M-1' THEN 'M69/730mm/深黄/3M'
    WHEN 'CO-M69/730mm/浅黄/3M-2' THEN 'M69/730mm/浅黄/3M'
    WHEN 'CO-M69/800mm/深黄/3M-3' THEN 'M69/800mm/深黄/3M'
    WHEN 'CO-L80H/1515mm/深黄/3M-4' THEN 'L80H/1515mm/深黄/3M'
    WHEN 'CO-GL60D/1520mm/深黄/3M-5' THEN 'GL60D/1520mm/深黄/3M'
    WHEN 'CO-E48/1555mm/深黄/3M-6' THEN 'E48/1555mm/深黄/3M'
    ELSE product_code END,
    finished_product_code = CASE
    WHEN sales_order_no = 'SO01' THEN 'M69/730mm/深黄/3M'
    WHEN sales_order_no = 'SO02' THEN 'M69/730mm/浅黄/3M'
    WHEN sales_order_no = 'SO03' THEN 'M69/800mm/深黄/3M'
    WHEN sales_order_no = 'SO04' THEN 'L80H/1515mm/深黄/3M'
    WHEN sales_order_no = 'SO05' THEN 'GL60D/1520mm/深黄/3M'
    ELSE COALESCE(finished_product_code, product_code) END
WHERE workspace_id = 'jinghua';
