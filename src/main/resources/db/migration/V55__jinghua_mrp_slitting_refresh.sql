-- 晶华 MRP 测试用例.xlsx → 分切主数据刷新（由 parse_jinghua_mrp_excel.py 生成，勿改 V53）

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, product_code, finished_product_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'jinghua', 'MR-M69/305-600M/1R/深黄', 305.0, 600000.0, NULL, 'PE', 'M69/305*600M/1R/深黄', 'M69/305*600M/1R/深黄', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'jinghua' AND roll_code = 'MR-M69/305-600M/1R/深黄');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/730mm/深黄/3M-1', 730.0, 3000.0, 1, 19, 'M69/730mm/深黄/3M', 'M69/730mm/深黄/3M', 'SO01', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/深黄/3M-1');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/730mm/浅黄/3M-2', 730.0, 3000.0, 1, 18, 'M69/730mm/浅黄/3M', 'M69/730mm/浅黄/3M', 'SO02', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/浅黄/3M-2');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-M69/800mm/深黄/3M-3', 800.0, 3000.0, 1, 17, 'M69/800mm/深黄/3M', 'M69/800mm/深黄/3M', 'SO03', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/800mm/深黄/3M-3');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-L80H/1515mm/深黄/3M-4', 1515.0, 3000.0, 1, 16, 'L80H/1515mm/深黄/3M', 'L80H/1515mm/深黄/3M', 'SO04', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-L80H/1515mm/深黄/3M-4');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-GL60D/1520mm/深黄/3M-5', 1520.0, 3000.0, 1, 15, 'GL60D/1520mm/深黄/3M', 'GL60D/1520mm/深黄/3M', 'SO05', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-GL60D/1520mm/深黄/3M-5');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, product_code, finished_product_code, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'CO-E48/1555mm/深黄/3M-6', 1555.0, 3000.0, 1, 14, 'E48/1555mm/深黄/3M', 'E48/1555mm/深黄/3M', NULL, NULL, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'CO-E48/1555mm/深黄/3M-6');

UPDATE master_roll
SET product_code = 'M69/305*600M/1R/深黄',
    finished_product_code = 'M69/305*600M/1R/深黄'
WHERE workspace_id = 'jinghua' AND roll_code = 'MR-M69/305-600M/1R/深黄'
  AND (product_code IS NULL OR finished_product_code IS NULL);

UPDATE child_slitting_order SET product_code = 'M69/730mm/深黄/3M', finished_product_code = 'M69/730mm/深黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/深黄/3M-1';
UPDATE child_slitting_order SET product_code = 'M69/730mm/浅黄/3M', finished_product_code = 'M69/730mm/浅黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/730mm/浅黄/3M-2';
UPDATE child_slitting_order SET product_code = 'M69/800mm/深黄/3M', finished_product_code = 'M69/800mm/深黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-M69/800mm/深黄/3M-3';
UPDATE child_slitting_order SET product_code = 'L80H/1515mm/深黄/3M', finished_product_code = 'L80H/1515mm/深黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-L80H/1515mm/深黄/3M-4';
UPDATE child_slitting_order SET product_code = 'GL60D/1520mm/深黄/3M', finished_product_code = 'GL60D/1520mm/深黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-GL60D/1520mm/深黄/3M-5';
UPDATE child_slitting_order SET product_code = 'E48/1555mm/深黄/3M', finished_product_code = 'E48/1555mm/深黄/3M'
WHERE workspace_id = 'jinghua' AND order_code = 'CO-E48/1555mm/深黄/3M-6';
