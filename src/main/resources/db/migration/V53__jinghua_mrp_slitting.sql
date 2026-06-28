-- 晶华 MRP 测试用例.xlsx → 分切主数据（workspace: jinghua）
-- 已应用版本：请勿修改；Excel 重生成请写入 V55__jinghua_mrp_slitting_refresh.sql

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'jinghua', 'MR-M69/305-600M/1R/深黄', 305.0, 600000.0, NULL, 'PE', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'jinghua' AND roll_code = 'MR-M69/305-600M/1R/深黄');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/730mm/深黄/3M-1', 730.0, 3000.0, 1, 19, 'SO01', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/深黄/3M-1');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/730mm/浅黄/3M-2', 730.0, 3000.0, 1, 18, 'SO02', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/浅黄/3M-2');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/800mm/深黄/3M-3', 800.0, 3000.0, 1, 17, 'SO03', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/800mm/深黄/3M-3');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-L80H/1515mm/深黄/3M-4', 1515.0, 3000.0, 1, 16, 'SO04', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-L80H/1515mm/深黄/3M-4');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-GL60D/1520mm/深黄/3M-5', 1520.0, 3000.0, 1, 15, 'SO05', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-GL60D/1520mm/深黄/3M-5');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-E48/1555mm/深黄/3M-6', 1555.0, 3000.0, 1, 14, NULL, NULL, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-E48/1555mm/深黄/3M-6');
