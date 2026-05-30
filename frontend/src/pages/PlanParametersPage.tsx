import { PlanParametersView } from '../components/PlanParametersView';
import { ALL_PLAN_PARAM_IDS, MASTER_PLAN_PARAM_GROUPS } from './planParameterGroups';

export function PlanParametersPage() {
  return (
    <PlanParametersView
      groups={MASTER_PLAN_PARAM_GROUPS}
      title="计划参数"
      description="按页签维护主计划时间轴、产能与班次等参数；修改后请保存并重新执行主计划运行。"
      showOtherGroup
      otherKnownParamIds={ALL_PLAN_PARAM_IDS}
    />
  );
}
