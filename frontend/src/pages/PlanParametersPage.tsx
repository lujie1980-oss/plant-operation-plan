import { PlanParametersView } from '../components/PlanParametersView';
import { ALL_PLAN_PARAM_IDS, MASTER_PLAN_PARAM_GROUPS } from './planParameterGroups';

const MASTER_PLAN_PARAM_LABELS: Record<string, string> = {
  master_plan_material_constraint_enabled: '启用主计划物料硬约束',
  master_plan_multi_resource_split: '启用工序多机台拆分（OR-Tools）',
  master_plan_jit_warm_start: '启用 JIT 倒排预排（多机台 warm start）',
};

const MASTER_PLAN_PARAM_DEFAULTS: Record<string, { paramValue: string; description: string }> = {
  master_plan_material_constraint_enabled: {
    paramValue: 'false',
    description:
      '主计划 Timefold 硬约束：排产日 BOM/库存物料必须可满足；false 时求解不因缺料扣分',
  },
  master_plan_multi_resource_split: {
    paramValue: 'false',
    description:
      'true：工序可拆到多台设备，全局主计划与单交付有限能力均走 OR-Tools；false：Timefold 单机台',
  },
  master_plan_jit_warm_start: {
    paramValue: 'true',
    description:
      '多机台拆分时按 JIT 交期倒排播种初始槽位与分钟数，作为 OR-Tools hint；种子可行则跳过重求解',
  },
};

export function PlanParametersPage() {
  return (
    <PlanParametersView
      groups={MASTER_PLAN_PARAM_GROUPS}
      title="计划参数"
      description="按页签维护主计划时间轴、产能与班次等参数；修改后请保存并重新执行主计划运行。"
      showOtherGroup
      otherKnownParamIds={ALL_PLAN_PARAM_IDS}
      paramLabels={MASTER_PLAN_PARAM_LABELS}
      paramDefaults={MASTER_PLAN_PARAM_DEFAULTS}
    />
  );
}
