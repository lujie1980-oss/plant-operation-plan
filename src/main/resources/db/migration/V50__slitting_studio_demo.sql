-- Extra demo data for interactive slitting studio (workspace: default)
INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, kerf_longitudinal_mm, kerf_transverse_mm, status)
VALUES ('default', 'MR-TEST-1000x3000', 1000, 3000, 2, 2, 'AVAILABLE'),
       ('default', 'MR-TEST-800x2400', 800, 2400, 2, 2, 'AVAILABLE');

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority, status)
VALUES ('default', 'CO-ST-280x1200', 280, 1200, 1, 20, 'OPEN'),
       ('default', 'CO-ST-320x1100', 320, 1100, 1, 19, 'OPEN'),
       ('default', 'CO-ST-150x800', 150, 800, 2, 18, 'OPEN'),
       ('default', 'CO-ST-400x2000', 400, 2000, 1, 17, 'OPEN'),
       ('default', 'CO-ST-500x1500', 500, 1500, 1, 16, 'OPEN'),
       ('default', 'CO-ST-200x600', 200, 600, 3, 15, 'OPEN');
