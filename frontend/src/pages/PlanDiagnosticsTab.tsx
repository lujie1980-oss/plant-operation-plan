import { useState } from 'react';
import { DetailSchedulePlanningPreviewPanel } from '../components/DetailSchedulePlanningPreviewPanel';
import { MasterPlanPlanningPreviewPanel } from '../components/MasterPlanPlanningPreviewPanel';
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
        基于当前场景预览 S04/S05 推演结果。主计划/细排程均支持统一推演 API：仅诊断、或内存/落库求解（细排程另支持初始队列赋时）。
        S04 可选反馈 overlay。下方「得分分解」对已持久化版本做 Timefold explain。
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
        <MasterPlanPlanningPreviewPanel
          strategyId={strategyId}
          feedbackCutoff={useFeedbackOverlay ? feedbackCutoff : null}
        />
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
        <DetailSchedulePlanningPreviewPanel masterPlanVersionId={masterPlanVersionId} />
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
