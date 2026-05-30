import { PlanParametersView } from '../components/PlanParametersView';
import { SCHEDULING_PARAM_GROUPS } from './planParameterGroups';

export function SchedulingPlanParametersPage() {
  return (
    <PlanParametersView
      groups={SCHEDULING_PARAM_GROUPS}
      title="计划参数"
      description="按页签维护物料约束、排程求解与主计划衔接参数；修改后请重新执行排程。"
    />
  );
}
