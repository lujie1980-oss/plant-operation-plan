import { PageHeader } from '../components/PageHeader';
import { PlanDiagnosticsTab } from './PlanDiagnosticsTab';
import './PlanDiagnosticsPage.css';

export function PlanDiagnosticsPage() {
  return (
    <div className="plan-diagnostics-page">
      <PageHeader
        title="推演诊断"
        showScenarioSelector
        description="预览 S04/S05 推演结果（不求解 Timefold），并对已持久化计划版本做约束得分分解。"
      />
      <div className="plan-diagnostics-page-scroll">
        <PlanDiagnosticsTab />
      </div>
    </div>
  );
}
