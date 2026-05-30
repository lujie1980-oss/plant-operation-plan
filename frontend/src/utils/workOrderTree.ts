import type { WorkOrder } from '../types/api';

export interface WorkOrderTreeNode {
  workOrder: WorkOrder;
  children: WorkOrderTreeNode[];
}

/** 将扁平工单列表组装为森林（根 = 无父工单或父不在列表中）。 */
export function buildWorkOrderForest(
  workOrders: WorkOrder[],
  getParent?: (wo: WorkOrder) => string | null | undefined,
): WorkOrderTreeNode[] {
  const byNo = new Map(workOrders.map((wo) => [wo.workOrderNo, wo]));
  const childrenByParent = new Map<string, WorkOrder[]>();
  const parentOf = getParent ?? ((wo: WorkOrder) => wo.parentWorkOrderNo);

  for (const wo of workOrders) {
    const parent = parentOf(wo);
    if (parent && byNo.has(parent)) {
      const siblings = childrenByParent.get(parent) ?? [];
      siblings.push(wo);
      childrenByParent.set(parent, siblings);
    }
  }

  const roots = workOrders.filter((wo) => {
    const parent = parentOf(wo);
    return !parent || !byNo.has(parent);
  });

  const toNode = (wo: WorkOrder): WorkOrderTreeNode => ({
    workOrder: wo,
    children: (childrenByParent.get(wo.workOrderNo) ?? [])
      .slice()
      .sort((a, b) => a.sequenceNo - b.sequenceNo || a.workOrderNo.localeCompare(b.workOrderNo))
      .map(toNode),
  });

  return roots
    .slice()
    .sort((a, b) => a.sequenceNo - b.sequenceNo || a.workOrderNo.localeCompare(b.workOrderNo))
    .map(toNode);
}

/** 默认折叠所有含子节点的根工单（全场景 MRP 视图）。 */
export function defaultCollapsedRootIds(forest: WorkOrderTreeNode[]): Set<string> {
  const collapsed = new Set<string>();
  for (const root of forest) {
    if (root.children.length > 0) {
      collapsed.add(root.workOrder.workOrderNo);
    }
  }
  return collapsed;
}

/** 订单行视角：默认展开全部节点。 */
export function defaultExpandedRootIds(_forest: WorkOrderTreeNode[]): Set<string> {
  return new Set<string>();
}

export function workOrderMatchesTextFilter(wo: WorkOrder, filters: Record<string, string>): boolean {
  const active = Object.entries(filters).filter(([, value]) => value.trim().length > 0);
  if (active.length === 0) return true;

  const textByKey: Record<string, string> = {
    workOrderNo: wo.workOrderNo,
    productCode: wo.productCode,
    resourceId: wo.resourceId,
    salesOrder: wo.peggingCount && wo.peggingCount > 0 && !wo.salesOrderNo
      ? `合并(${wo.peggingCount})`
      : wo.salesOrderNo
        ? `${wo.salesOrderNo}-${wo.salesOrderLineNo}`
        : '',
    dispatchStatus: wo.dispatchStatus,
  };

  return active.every(([key, query]) =>
    (textByKey[key] ?? '').toLowerCase().includes(query.trim().toLowerCase()),
  );
}

export function subtreeVisible(
  node: WorkOrderTreeNode,
  filters: Record<string, string>,
): boolean {
  if (workOrderMatchesTextFilter(node.workOrder, filters)) return true;
  return node.children.some((child) => subtreeVisible(child, filters));
}
