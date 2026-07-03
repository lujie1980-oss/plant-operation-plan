-- TE workspace: scale sales order and MRP work order quantities by 1000x
UPDATE sales_order_line
SET order_qty = order_qty * 1000
WHERE workspace_id = 'te'
  AND order_qty IS NOT NULL
  AND order_qty < 10000;

UPDATE work_order
SET quantity = quantity * 1000
WHERE workspace_id = 'te'
  AND quantity IS NOT NULL
  AND quantity < 10000;

UPDATE work_order_pegging
SET pegged_qty = pegged_qty * 1000
WHERE workspace_id = 'te'
  AND pegged_qty IS NOT NULL
  AND pegged_qty < 10000;
