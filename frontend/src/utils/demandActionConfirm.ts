import type { OrderDemandActionId } from '../types/demandActions';

import type { DemandPoolEntry } from '../types/api';

import { suggestPromiseDate as suggestFromChain } from './fulfillmentChainMeta';



export interface DemandActionConfirmCopy {

  title: string;

  description: string;

  confirmLabel: string;

  destructive?: boolean;

}



export function demandActionConfirmCopy(action: OrderDemandActionId): DemandActionConfirmCopy {

  switch (action) {

    case 'INFINITE_PLAN_JIT':

    case 'BUILD_UPSTREAM_CHAIN':

      return {

        title: '无限能力计划（JIT）',

        description:

          '基于 Demand 交期 JIT 倒排：挂接库存/已有供应，未满足部分递归创建上游 SupplyOrder 并落库。可用「取消计划」回滚本行专属工单。',

        confirmLabel: '开始 JIT 计划',

        destructive: true,

      };

    case 'FINITE_PLAN':

    case 'PLAN_FINITE':

      return {

        title: '有限能力计划',

        description:

          '对当前 CustomerOrderLineDelivery 单独调用优化器有限能力求解；基线主计划中其他订单已排结果以固定负荷占用产能，不会被改写。结果写入满足链预览（Sandbox）。',

        confirmLabel: '开始求解',

      };

    case 'PLAN_UNCONSTRAINED':

      return {

        title: '无限能力计划',

        description: '基于当前主数据与库存做无限能力推演，结果写入满足链预览，不会写入承诺交期。',

        confirmLabel: '开始推演',

      };

    case 'CONFIRM_PROMISE_DATE':

      return {

        title: '确认承诺交期',

        description: '将基于有限能力优化后的满足链自动计算承诺交期，并写入销售订单行。',

        confirmLabel: '确认写入',

        destructive: true,

      };

    case 'CANCEL_PLAN':

      return {

        title: '取消计划',

        description:

          '将解除本订单行的计划满足链。仅服务本行的工单会被删除；若工单还供应其他订单，则保留工单并只解除 pegging 关系。已下发工单不会被删除。承诺交期不会被清除（请使用「取消承诺」）。',

        confirmLabel: '确认取消',

        destructive: true,

      };

    case 'CANCEL_PROMISE':

      return {

        title: '取消承诺交期',

        description:

          '仅清空销售订单行的承诺交期（promiseDate），不删除计划工单、pegging 或满足链。',

        confirmLabel: '确认取消承诺',

        destructive: true,

      };

  }

}



export { suggestFromChain as suggestPromiseDate };



export function orderSummaryLine(row: DemandPoolEntry): string {

  return `${row.salesOrderNo}-${row.salesOrderLineNo} · ${row.productCode} · 数量 ${row.orderQty}`;

}

