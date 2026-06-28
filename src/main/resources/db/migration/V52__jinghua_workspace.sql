-- 晶华新材 workspace 及分切主数据
INSERT INTO workspace (workspace_id, name, description, is_default)
SELECT 'jinghua', '晶华新材', '晶华新材薄膜分切演示数据集', FALSE
WHERE NOT EXISTS (SELECT 1 FROM workspace WHERE workspace_id = 'jinghua');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'jinghua', 'JH-MR-1500x6000', 1500, 6000, 0.025, 'PET', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'jinghua' AND roll_code = 'JH-MR-1500x6000');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'jinghua', 'JH-MR-1350x4500', 1350, 4500, 0.025, 'BOPP', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'jinghua' AND roll_code = 'JH-MR-1350x4500');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'jinghua', 'JH-MR-1000x3000', 1000, 3000, 0.02, 'OPP', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'jinghua' AND roll_code = 'JH-MR-1000x3000');

INSERT INTO intermediate_roll_catalog (workspace_id, spec_code, width_mm, length_mm, cutting_method, kerf_mm, active)
SELECT 'jinghua', 'JH-INT-750x3000', 750, 3000, 'LONGITUDINAL', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM intermediate_roll_catalog WHERE workspace_id = 'jinghua' AND spec_code = 'JH-INT-750x3000');

INSERT INTO intermediate_roll_catalog (workspace_id, spec_code, width_mm, length_mm, cutting_method, kerf_mm, active)
SELECT 'jinghua', 'JH-INT-500x2500', 500, 2500, 'LONGITUDINAL', 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM intermediate_roll_catalog WHERE workspace_id = 'jinghua' AND spec_code = 'JH-INT-500x2500');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-280x1200', 280, 1200, 1, 20, 'SO-JH-260601', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-280x1200');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-320x1100', 320, 1100, 1, 19, 'SO-JH-260602', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-320x1100');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-150x800', 150, 800, 2, 18, 'SO-JH-260603', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-150x800');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-200x900', 200, 900, 1, 17, 'SO-JH-260604', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-200x900');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-400x2000', 400, 2000, 1, 16, 'SO-JH-260605', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-400x2000');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, sales_order_no, sales_order_line_no, status)
SELECT 'jinghua', 'JH-CO-500x1500', 500, 1500, 1, 15, 'SO-JH-260606', 10, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-500x1500');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, status)
SELECT 'jinghua', 'JH-CO-250x1000', 250, 1000, 1, 14, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-250x1000');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, status)
SELECT 'jinghua', 'JH-CO-180x750', 180, 750, 2, 13, 'OPEN'
WHERE NOT EXISTS (SELECT 1 FROM child_slitting_order WHERE workspace_id = 'jinghua' AND order_code = 'JH-CO-180x750');
