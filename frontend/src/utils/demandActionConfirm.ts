import type { OrderDemandActionId } from '../types/demandActions';
import type { DemandPoolEntry } from '../types/api';
import type { OrderPlanningChain } from '../types/orderPlanningChain';

export interface DemandActionConfirmCopy {
  title: string;
  description: string;
  confirmLabel: string;
  destructive?: boolean;
}

export function demandActionConfirmCopy(action: OrderDemandActionId): DemandActionConfirmCopy {
  switch (action) {
    case 'BUILD_UPSTREAM_CHAIN':
      return {
        title: '创建上游满足链',
        description:
          '将触发 MRP 合并工单并重算当前场景下的满足链。该操作会刷新全场景相关数据，可能耗时较长。',
        confirmLabel: '确认创建',
        destructive: true,
      };
    case 'PLAN_UNCONSTRAINED':
      return {
        title: '无限能力计划',
        description: '基于当前主数据与库存做无限能力推演，结果仅用于预览，不会写入计划版本或承诺交期。',
        confirmLabel: '开始推演',
      };
    case 'PLAN_FINITE':
      return {
        title: '有限能力计划',
        description: '基于有限产能与日历做推演预览，结果仅用于分析，不会写入计划版本或承诺交期。',
        confirmLabel: '开始推演',
      };
    case 'CONFIRM_PROMISE_DATE':
      return {
        title: '确认承诺交期',
        description: '将基于有限能力推演自动计算承诺交期，并写入销售订单行。',
        confirmLabel: '确认写入',
        destructive: true,
      };
    case 'CANCEL_PLAN':
      return {
        title: '取消计划',
        description:
          '将解除本订单行的计划满足链。仅服务本行的工单会被删除；若工单还供应其他订单，则保留工单并只解除 pegging 关系。已下发工单不会被删除。',
        confirmLabel: '确认取消',
        destructive: true,
      };
  }
}

export function suggestPromiseDate(planning: OrderPlanningChain | null): string | null {
  if (!planning?.nodes?.length) return null;
  const dates = planning.nodes
    .filter((n) => n.nodeType === 'WORK_ORDER' || n.nodeType === 'SALES_ORDER')
    .map((n) => n.windowEnd)
    .filter((d): d is string => Boolean(d));
  if (dates.length === 0) return null;
  return dates.sort().at(-1) ?? null;
}

export function orderSummaryLine(row: DemandPoolEntry): string {
  return `${row.salesOrderNo}-${row.salesOrderLineNo} · ${row.productCode} · 数量 ${row.orderQty}`;
}
