-- 补充母卷主数据（workspace: default），供分切工作台拖放测试（已存在则跳过）
INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-1500x6000', 1500, 6000, 0.025, 'PET-FILM', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-1500x6000');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-1350x4500', 1350, 4500, 0.025, 'PET-FILM', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-1350x4500');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-900x2000', 900, 2000, 0.02, 'BOPP', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-900x2000');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-600x1800', 600, 1800, 0.02, 'BOPP', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-600x1800');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-1000x1000', 1000, 1000, 0.03, 'AL-FOIL', 1.5, 1.5, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-1000x1000');

INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, thickness_mm, material_code, kerf_longitudinal_mm, kerf_transverse_mm, status)
SELECT 'default', 'MR-500x1200', 500, 1200, 0.015, 'PAPER', 2, 2, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM master_roll WHERE workspace_id = 'default' AND roll_code = 'MR-500x1200');
