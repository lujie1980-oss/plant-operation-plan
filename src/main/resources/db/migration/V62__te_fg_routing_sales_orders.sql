-- One OPEN sales order line per FG (tier-0) product with maintained routing in TE workspace.
DELETE FROM sales_order_line WHERE workspace_id = 'te';

INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 10, 'TE-CUST', '1-2392776-3', 100000, DATE '2026-06-12', DATE '2026-06-12', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 10);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 20, 'TE-CUST', '1-2392776-4', 100000, DATE '2026-06-19', DATE '2026-06-19', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 20);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 30, 'TE-CUST', '1-2411379-1', 100000, DATE '2026-06-26', DATE '2026-06-26', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 30);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 40, 'TE-CUST', '1-2411379-3', 100000, DATE '2026-07-03', DATE '2026-07-03', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 40);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 50, 'TE-CUST', '1-2411498-1', 100000, DATE '2026-07-10', DATE '2026-07-10', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 50);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 60, 'TE-CUST', '1-2411498-2', 100000, DATE '2026-07-17', DATE '2026-07-17', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 60);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 70, 'TE-CUST', '1-2411498-3', 100000, DATE '2026-07-24', DATE '2026-07-24', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 70);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 80, 'TE-CUST', '1-2411498-4', 100000, DATE '2026-07-31', DATE '2026-07-31', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 80);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 90, 'TE-CUST', '1-2411498-6', 100000, DATE '2026-08-07', DATE '2026-08-07', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 90);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 100, 'TE-CUST', '1-2411498-8', 100000, DATE '2026-08-14', DATE '2026-08-14', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 100);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 110, 'TE-CUST', '1-2450881-1', 100000, DATE '2026-08-21', DATE '2026-08-21', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 110);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 120, 'TE-CUST', '1-2450881-2', 100000, DATE '2026-08-28', DATE '2026-08-28', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 120);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 130, 'TE-CUST', '1-2450881-3', 100000, DATE '2026-09-04', DATE '2026-09-04', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 130);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 140, 'TE-CUST', '1-2495112-1', 100000, DATE '2026-09-11', DATE '2026-09-11', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 140);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 150, 'TE-CUST', '1-2496074-1', 100000, DATE '2026-09-18', DATE '2026-09-18', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 150);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 160, 'TE-CUST', '1-2498255-1', 100000, DATE '2026-09-25', DATE '2026-09-25', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 160);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 170, 'TE-CUST', '1-2498255-2', 100000, DATE '2026-10-02', DATE '2026-10-02', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 170);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 180, 'TE-CUST', '1-2500507-1', 100000, DATE '2026-10-09', DATE '2026-10-09', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 180);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 190, 'TE-CUST', '1-2500507-2', 100000, DATE '2026-10-16', DATE '2026-10-16', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 190);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 200, 'TE-CUST', '1-2500529-1', 100000, DATE '2026-10-23', DATE '2026-10-23', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 200);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 210, 'TE-CUST', '1-2500529-2', 100000, DATE '2026-10-30', DATE '2026-10-30', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 210);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 220, 'TE-CUST', '1-2503744-1', 100000, DATE '2026-11-06', DATE '2026-11-06', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 220);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 230, 'TE-CUST', '1-2503744-2', 100000, DATE '2026-11-13', DATE '2026-11-13', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 230);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 240, 'TE-CUST', '1-2507245-1', 100000, DATE '2026-11-20', DATE '2026-11-20', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 240);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 250, 'TE-CUST', '1-2509049-1', 100000, DATE '2026-11-27', DATE '2026-11-27', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 250);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 260, 'TE-CUST', '1-2509049-2', 100000, DATE '2026-12-04', DATE '2026-12-04', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 260);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 270, 'TE-CUST', '1-2509049-3', 100000, DATE '2026-12-11', DATE '2026-12-11', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 270);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 280, 'TE-CUST', '1-2509607-1', 100000, DATE '2026-12-18', DATE '2026-12-18', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 280);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 290, 'TE-CUST', '1-2509607-2', 100000, DATE '2026-12-25', DATE '2026-12-25', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 290);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 300, 'TE-CUST', '1-2511654-1', 100000, DATE '2027-01-01', DATE '2027-01-01', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 300);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 310, 'TE-CUST', '1-2512669-1', 100000, DATE '2027-01-08', DATE '2027-01-08', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 310);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 320, 'TE-CUST', '1-2512669-2', 100000, DATE '2027-01-15', DATE '2027-01-15', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 320);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 330, 'TE-CUST', '1-2512669-3', 100000, DATE '2027-01-22', DATE '2027-01-22', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 330);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 340, 'TE-CUST', '1-2516673-1', 100000, DATE '2027-01-29', DATE '2027-01-29', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 340);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 350, 'TE-CUST', '1-2516673-2', 100000, DATE '2027-02-05', DATE '2027-02-05', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 350);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 360, 'TE-CUST', '1-2516673-3', 100000, DATE '2027-02-12', DATE '2027-02-12', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 360);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 370, 'TE-CUST', '1-2532366-1', 100000, DATE '2027-02-19', DATE '2027-02-19', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 370);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 380, 'TE-CUST', '1-2532366-2', 100000, DATE '2027-02-26', DATE '2027-02-26', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 380);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 390, 'TE-CUST', '1-2532366-3', 100000, DATE '2027-03-05', DATE '2027-03-05', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 390);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 400, 'TE-CUST', '1-2532368-1', 100000, DATE '2027-03-12', DATE '2027-03-12', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 400);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 410, 'TE-CUST', '1-2532368-2', 100000, DATE '2027-03-19', DATE '2027-03-19', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 410);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 420, 'TE-CUST', '1-2532368-3', 100000, DATE '2027-03-26', DATE '2027-03-26', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 420);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 430, 'TE-CUST', '1-2532368-4', 100000, DATE '2027-04-02', DATE '2027-04-02', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 430);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 440, 'TE-CUST', '1-2532713-1', 100000, DATE '2027-04-09', DATE '2027-04-09', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 440);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 450, 'TE-CUST', '1-2532713-2', 100000, DATE '2027-04-16', DATE '2027-04-16', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 450);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 460, 'TE-CUST', '1-2532713-3', 100000, DATE '2027-04-23', DATE '2027-04-23', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 460);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 470, 'TE-CUST', '1-2532713-4', 100000, DATE '2027-04-30', DATE '2027-04-30', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 470);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 480, 'TE-CUST', '1-2532733-1', 100000, DATE '2027-05-07', DATE '2027-05-07', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 480);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 490, 'TE-CUST', '1-2532733-2', 100000, DATE '2027-05-14', DATE '2027-05-14', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 490);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 500, 'TE-CUST', '1-2532733-3', 100000, DATE '2027-05-21', DATE '2027-05-21', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 500);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 510, 'TE-CUST', '1-2532735-2', 100000, DATE '2027-05-28', DATE '2027-05-28', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 510);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 520, 'TE-CUST', '1-2532735-4', 100000, DATE '2027-06-04', DATE '2027-06-04', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 520);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 530, 'TE-CUST', '1-2532938-1', 100000, DATE '2027-06-11', DATE '2027-06-11', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 530);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 540, 'TE-CUST', '1-2536890-1', 100000, DATE '2027-06-18', DATE '2027-06-18', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 540);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 550, 'TE-CUST', '1-2536890-2', 100000, DATE '2027-06-25', DATE '2027-06-25', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 550);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 560, 'TE-CUST', '1-2541414-1', 100000, DATE '2027-07-02', DATE '2027-07-02', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 560);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 570, 'TE-CUST', '2-2411498-1', 100000, DATE '2027-07-09', DATE '2027-07-09', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 570);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 580, 'TE-CUST', '2-2411498-2', 100000, DATE '2027-07-16', DATE '2027-07-16', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 580);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 590, 'TE-CUST', '2-2411498-3', 100000, DATE '2027-07-23', DATE '2027-07-23', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 590);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 600, 'TE-CUST', '2-2414106-3', 100000, DATE '2027-07-30', DATE '2027-07-30', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 600);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 610, 'TE-CUST', '2-2414106-4', 100000, DATE '2027-08-06', DATE '2027-08-06', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 610);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 620, 'TE-CUST', '2-2494797-1', 100000, DATE '2027-08-13', DATE '2027-08-13', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 620);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 630, 'TE-CUST', '2-2495109-1', 100000, DATE '2027-08-20', DATE '2027-08-20', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 630);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 640, 'TE-CUST', '2-2495109-2', 100000, DATE '2027-08-27', DATE '2027-08-27', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 640);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 650, 'TE-CUST', '2-2496552-1', 100000, DATE '2027-09-03', DATE '2027-09-03', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 650);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 660, 'TE-CUST', '2-2502736-1', 100000, DATE '2027-09-10', DATE '2027-09-10', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 660);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 670, 'TE-CUST', '2-2502736-2', 100000, DATE '2027-09-17', DATE '2027-09-17', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 670);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 680, 'TE-CUST', '2-2502736-3', 100000, DATE '2027-09-24', DATE '2027-09-24', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 680);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 690, 'TE-CUST', '2-2505201-1', 100000, DATE '2027-10-01', DATE '2027-10-01', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 690);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 700, 'TE-CUST', '2-2507750-1', 100000, DATE '2027-10-08', DATE '2027-10-08', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 700);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 710, 'TE-CUST', '2-2507750-2', 100000, DATE '2027-10-15', DATE '2027-10-15', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 710);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 720, 'TE-CUST', '2-2514898-1', 100000, DATE '2027-10-22', DATE '2027-10-22', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 720);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 730, 'TE-CUST', '2-2514898-2', 100000, DATE '2027-10-29', DATE '2027-10-29', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 730);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 740, 'TE-CUST', '2-2528026-1', 100000, DATE '2027-11-05', DATE '2027-11-05', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 740);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 750, 'TE-CUST', '2344903-1', 100000, DATE '2027-11-12', DATE '2027-11-12', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 750);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 760, 'TE-CUST', '2357996-1', 100000, DATE '2027-11-19', DATE '2027-11-19', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 760);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 770, 'TE-CUST', '2357996-3', 100000, DATE '2027-11-26', DATE '2027-11-26', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 770);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 780, 'TE-CUST', '2358211-1', 100000, DATE '2027-12-03', DATE '2027-12-03', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 780);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 790, 'TE-CUST', '2367483-1', 100000, DATE '2027-12-10', DATE '2027-12-10', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 790);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 800, 'TE-CUST', '2367483-3', 100000, DATE '2027-12-17', DATE '2027-12-17', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 800);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 810, 'TE-CUST', '2367483-4', 100000, DATE '2027-12-24', DATE '2027-12-24', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 810);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 820, 'TE-CUST', '2372347-2', 100000, DATE '2027-12-31', DATE '2027-12-31', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 820);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 830, 'TE-CUST', '2372347-3', 100000, DATE '2028-01-07', DATE '2028-01-07', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 830);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 840, 'TE-CUST', '2372827-1', 100000, DATE '2028-01-14', DATE '2028-01-14', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 840);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 850, 'TE-CUST', '2372828-1', 100000, DATE '2028-01-21', DATE '2028-01-21', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 850);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 860, 'TE-CUST', '2375215-2', 100000, DATE '2028-01-28', DATE '2028-01-28', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 860);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 870, 'TE-CUST', '2375582-1', 100000, DATE '2028-02-04', DATE '2028-02-04', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 870);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 880, 'TE-CUST', '2386056-1', 100000, DATE '2028-02-11', DATE '2028-02-11', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 880);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 890, 'TE-CUST', '2388418-1', 100000, DATE '2028-02-18', DATE '2028-02-18', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 890);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 900, 'TE-CUST', '2388654-1', 100000, DATE '2028-02-25', DATE '2028-02-25', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 900);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 910, 'TE-CUST', '2388654-2', 100000, DATE '2028-03-03', DATE '2028-03-03', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 910);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 920, 'TE-CUST', '2388656-1', 100000, DATE '2028-03-10', DATE '2028-03-10', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 920);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 930, 'TE-CUST', '2388658-1', 100000, DATE '2028-03-17', DATE '2028-03-17', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 930);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 940, 'TE-CUST', '2389671-1', 100000, DATE '2028-03-24', DATE '2028-03-24', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 940);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 950, 'TE-CUST', '2389681-1', 100000, DATE '2028-03-31', DATE '2028-03-31', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 950);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 960, 'TE-CUST', '2389829-1', 100000, DATE '2028-04-07', DATE '2028-04-07', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 960);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 970, 'TE-CUST', '2389829-2', 100000, DATE '2028-04-14', DATE '2028-04-14', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 970);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 980, 'TE-CUST', '2389829-3', 100000, DATE '2028-04-21', DATE '2028-04-21', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 980);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 990, 'TE-CUST', '2390650-3', 100000, DATE '2028-04-28', DATE '2028-04-28', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 990);
INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)
SELECT 'te', 'TE-FG-ROUTING-202606', 1000, 'TE-CUST', '2390653-1', 100000, DATE '2028-05-05', DATE '2028-05-05', 5, 0, 'OPEN', FALSE
WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = 'TE-FG-ROUTING-202606' AND sales_order_line_no = 1000);
