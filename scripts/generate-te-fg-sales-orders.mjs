import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const jsonPath = path.join(root, 'src/main/resources/sample-data/factory-te-demo.json');
const migPath = path.join(root, 'src/main/resources/db/migration/V62__te_fg_routing_sales_orders.sql');

const data = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
const routing = new Set((data.productResources ?? []).map((r) => r.productCode));
const finished = new Set();
const childrenByParentScoped = new Map();
const manufacturedParents = new Set();

for (const b of data.bomComponents ?? []) {
  if (b.finishedProductCode) finished.add(b.finishedProductCode);
  if (b.parentProductCode && b.componentProductCode) {
    manufacturedParents.add(b.parentProductCode);
    const key = `${b.finishedProductCode}>${b.parentProductCode}`;
    if (!childrenByParentScoped.has(key)) childrenByParentScoped.set(key, new Set());
    childrenByParentScoped.get(key).add(b.componentProductCode);
  }
}

const tierByProduct = new Map();
function bfs(finishedProduct, root) {
  const queue = [[root, 0]];
  const visited = new Set();
  while (queue.length > 0) {
    const [productCode, tier] = queue.shift();
    const visitKey = `${finishedProduct}>${productCode}@${tier}`;
    if (visited.has(visitKey)) continue;
    visited.add(visitKey);
    tierByProduct.set(productCode, Math.min(tierByProduct.get(productCode) ?? 999, tier));
    const children = childrenByParentScoped.get(`${finishedProduct}>${productCode}`) ?? new Set();
    for (const child of children) {
      const childTier = tier + 1;
      tierByProduct.set(child, Math.min(tierByProduct.get(child) ?? 999, childTier));
      if (manufacturedParents.has(child)) queue.push([child, childTier]);
    }
  }
}
for (const fg of finished) bfs(fg, fg);

const fgWithRouting = [...routing]
  .filter((productCode) => (tierByProduct.get(productCode) ?? 3) === 0)
  .sort();

const orderNo = 'TE-FG-ROUTING-202606';
const baseDate = new Date('2026-06-12T00:00:00Z');
const lines = fgWithRouting.map((productCode, index) => {
  const lineNo = (index + 1) * 10;
  const due = new Date(baseDate);
  due.setUTCDate(due.getUTCDate() + index * 7);
  const dueStr = due.toISOString().slice(0, 10);
  return {
    salesOrderNo: orderNo,
    salesOrderLineNo: lineNo,
    customerCode: 'TE-CUST',
    productCode,
    orderQty: 100000.0,
    promiseDate: dueStr,
    dueDate: dueStr,
    priority: 5,
    expediteLevel: 0,
    status: 'OPEN',
  };
});

data.salesOrderLines = lines;
data._meta.sales_order_count = lines.length;
fs.writeFileSync(jsonPath, `${JSON.stringify(data, null, 2)}\n`);

const sql = [
  '-- One OPEN sales order line per FG (tier-0) product with maintained routing in TE workspace.',
  "DELETE FROM sales_order_line WHERE workspace_id = 'te';",
  '',
  ...lines.map((line) =>
    [
      'INSERT INTO sales_order_line (workspace_id, sales_order_no, sales_order_line_no, customer_code, product_code, order_qty, promise_date, due_date, priority, expedite_level, status, schedule_lock_flag)',
      `SELECT 'te', '${line.salesOrderNo}', ${line.salesOrderLineNo}, '${line.customerCode}', '${line.productCode}', ${line.orderQty}, DATE '${line.dueDate}', DATE '${line.dueDate}', ${line.priority}, ${line.expediteLevel}, '${line.status}', FALSE`,
      `WHERE NOT EXISTS (SELECT 1 FROM sales_order_line WHERE workspace_id = 'te' AND sales_order_no = '${line.salesOrderNo}' AND sales_order_line_no = ${line.salesOrderLineNo});`,
    ].join('\n'),
  ),
  '',
].join('\n');
fs.writeFileSync(migPath, sql);

console.log(`Generated ${lines.length} sales order lines`);
console.log(`Updated ${jsonPath}`);
console.log(`Wrote ${migPath}`);
