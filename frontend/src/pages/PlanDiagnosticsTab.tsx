import { useState } from 'react';
import { PlanningDiagnosticsPanel } from '../components/PlanningDiagnosticsPanel';
import { PlanningScoreExplanationPanel } from '../components/PlanningScoreExplanationPanel';
import { usePlan } from '../context/PlanContext';
import './PlanDiagnosticsTab.css';

export function PlanDiagnosticsTab() {
  const { masterPlan, detailSchedule, activePlanVersionId } = usePlan();
  const [feedbackCutoff, setFeedbackCutoff] = useState(() => new Date().toISOString().slice(0, 10));
  const [useFeedbackOverlay, setUseFeedbackOverlay] = useState(false);

  const strategyId = masterPlan?.strategyId ?? null;
  const masterPlanVersionId = activePlanVersionId ?? masterPlan?.planVersionId ?? null;
  const detailScheduleVersionId = detailSchedule?.planVersionId ?? null;

  return (
    <div className="plan-diagnostics-tab">
      <p className="plan-diagnostics-intro">
        基于当前场景最新主计划版本预览 S04/S05 推演结果（不求解 Timefold）。S04 可选反馈 overlay 模拟滚动刷新前的冻结槽位。
        下方「得分分解」对已求解并持久化的计划版本调用 Timefold explain，展示约束匹配明细。
      </p>
      <section className="card plan-diagnostics-section">
        <h3>主计划推演（S04）</h3>
        <div className="plan-diagnostics-options">
          <label className="plan-diagnostics-check">
            <input
              type="checkbox"
              checked={useFeedbackOverlay}
              onChange={(e) => setUseFeedbackOverlay(e.target.checked)}
            />
            启用反馈 overlay 预览
          </label>
          <label className="plan-diagnostics-cutoff">
            <span>反馈截止日</span>
            <input
              type="date"
              className="input"
              value={feedbackCutoff}
              onChange={(e) => setFeedbackCutoff(e.target.value)}
              disabled={!useFeedbackOverlay}
            />
          </label>
        </div>
        <PlanningDiagnosticsPanel
          layer="master-plan"
          contextId={strategyId}
          feedbackCutoff={useFeedbackOverlay ? feedbackCutoff : null}
          autoLoad
        />
        <div className="plan-diagnostics-score">
          <h4>选优层得分分解</h4>
          <PlanningScoreExplanationPanel
            layer="master-plan"
            planVersionId={masterPlanVersionId}
          />
        </div>
      </section>
      <section className="card plan-diagnostics-section">
        <h3>详细排程推演（S05）</h3>
        <PlanningDiagnosticsPanel
          layer="detail-schedule"
          contextId={masterPlanVersionId}
          autoLoad
        />
        <div className="plan-diagnostics-score">
          <h4>选优层得分分解</h4>
          <PlanningScoreExplanationPanel
            layer="detail-schedule"
            planVersionId={detailScheduleVersionId}
            masterPlanVersionId={masterPlanVersionId}
          />
        </div>
      </section>
    </div>
  );
}
