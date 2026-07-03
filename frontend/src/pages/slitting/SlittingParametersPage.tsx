import { PlanParametersView } from '../../components/PlanParametersView';
import { SLITTING_PARAM_DEFAULTS, SLITTING_PARAM_GROUPS, SLITTING_PARAM_LABELS } from './slittingParameterGroups';

export function SlittingParametersPage() {
  return (
    <PlanParametersView
      groups={SLITTING_PARAM_GROUPS}
      paramDefaults={SLITTING_PARAM_DEFAULTS}
      paramLabels={SLITTING_PARAM_LABELS}
      hideGroupNav
      title="优化参数"
      description={
        '维护分切 Timefold 求解时限与需求导入默认尺寸。切边余量、重叠与层级等几何硬约束由求解器内置，取自母卷 kerf 字段；' +
        '软目标包括紧凑排布与非标中间卷惩罚，当前版本暂不支持在此页调整权重。'
      }
    />
  );
}
