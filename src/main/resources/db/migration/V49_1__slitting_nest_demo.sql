-- Demo data for slitting nest (workspace: default)
INSERT INTO master_roll (workspace_id, roll_code, width_mm, length_mm, kerf_longitudinal_mm, kerf_transverse_mm, status)
VALUES ('default', 'MR-1200-5000-A', 1200, 5000, 2, 2, 'AVAILABLE'),
       ('default', 'MR-1200-5000-B', 1200, 5000, 2, 2, 'AVAILABLE');

INSERT INTO intermediate_roll_catalog (workspace_id, spec_code, width_mm, length_mm, cutting_method, kerf_mm)
VALUES ('default', 'INT-600-2500', 600, 2500, 'LONGITUDINAL', 2),
       ('default', 'INT-400-2500', 400, 2500, 'LONGITUDINAL', 2),
       ('default', 'INT-600-2000', 600, 2000, 'TRANSVERSE', 2),
       ('default', 'INT-800-2500', 800, 2500, 'LONGITUDINAL', 2);

INSERT INTO child_slitting_order (workspace_id, order_code, width_mm, length_mm, quantity, priority)
VALUES ('default', 'CO-001', 280, 1200, 2, 10),
       ('default', 'CO-002', 320, 1100, 1, 9),
       ('default', 'CO-003', 150, 800, 3, 8),
       ('default', 'CO-004', 200, 900, 2, 7),
       ('default', 'CO-005', 180, 750, 2, 6),
       ('default', 'CO-006', 250, 1000, 1, 5),
       ('default', 'CO-007', 300, 1150, 2, 4),
       ('default', 'CO-008', 220, 850, 1, 3);
