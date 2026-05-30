import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { DataTable } from '../components/DataTable';
import { MachineScheduleGantt } from '../components/MachineScheduleGantt';
import { PlanningDiagnosticsPanel } from '../components/PlanningDiagnosticsPanel';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import './DetailSchedulePage.css';

export function DetailSchedulePage() {
  const { masterPlan, detailSchedule, setDetailSchedule, setMasterPlan } = usePlan();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [refreshMasterPlan, setRefreshMasterPlan] = useState(true);
  const [feedbackCutoff, setFeedbackCutoff] = useState(() => new Date().toISOString().slice(0, 10));
  const [showDsDiagnostics, setShowDsDiagnostics] = useState(false);

  const solve = async () => {
    if (!masterPlan?.planVersionId) {
      setError('请先完成主计划运行');
      return;
    }
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.solveDetailSchedule(masterPlan.planVersionId, {
        refreshMasterPlan,
        feedbackCutoff: refreshMasterPlan ? feedbackCutoff : undefined,
      });
      setDetailSchedule(result);
      if (result.masterPlanRefresh) {
        const refreshed = await api.getMasterPlan(result.masterPlanRefresh.newMasterPlanVersionId);
        setMasterPlan(refreshed);
        setSuccess(
          `排程 ${result.planVersionId} 完成；主计划已滚动更新为 ${result.masterPlanRefresh.newMasterPlanVersionId}（冻结 ${result.masterPlanRefresh.frozenAllocationRows} 条，重排 ${result.masterPlanRefresh.replannedAllocationRows} 条）`,
        );
      } else {
        setSuccess(`排程完成：${result.planVersionId}`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '排程失败');
    } finally {
      setLoading(false);
    }
  };

  const applyFeedbackOnly = async () => {
    if (!detailSchedule?.planVersionId || !masterPlan?.planVersionId) {
      setError('需要已有排程结果与主计划版本');
      return;
    }
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const applied = await api.applyScheduleFeedback(
        detailSchedule.planVersionId,
        masterPlan.planVersionId,
        feedbackCutoff,
      );
      setSuccess(
        `已写入反馈 ${applied.operationCount} 条（冻结 ${applied.frozenCount}，建议 ${applied.suggestionCount}）`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '写入反馈失败');
    } finally {
      setLoading(false);
    }
  };

  const refreshMpOnly = async () => {
    if (!detailSchedule?.planVersionId || !masterPlan?.planVersionId) {
      setError('需要已有排程结果与主计划版本');
      return;
    }
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const refresh = await api.refreshSubsequentMasterPlan({
        parentMasterPlanVersionId: masterPlan.planVersionId,
        detailScheduleVersionId: detailSchedule.planVersionId,
        feedbackCutoff,
      });
      const refreshed = await api.getMasterPlan(refresh.newMasterPlanVersionId);
      setMasterPlan(refreshed);
      setSuccess(
        `主计划已更新为 ${refresh.newMasterPlanVersionId}（冻结 ${refresh.frozenAllocationRows} 条，重排 ${refresh.replannedAllocationRows} 条）`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '滚动更新主计划失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="detail-schedule-page">
      <PageHeader
        title="生产排程"
        description="每台机台一行，任务条按开始/结束时间在同一行排列。需先在「生产工单」确认并发布工单。"
        actions={
          <>
            <button type="button" className="btn" onClick={() => void applyFeedbackOnly()} disabled={loading}>
              仅写入反馈
            </button>
            <button type="button" className="btn" onClick={() => void refreshMpOnly()} disabled={loading}>
              仅刷新主计划
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => setShowDsDiagnostics((v) => !v)}
              disabled={!masterPlan?.planVersionId}
            >
              {showDsDiagnostics ? '收起推演诊断' : '推演诊断'}
            </button>
            <button type="button" className="btn primary" onClick={() => void solve()} disabled={loading}>
              求解排程
            </button>
          </>
        }
      />
      <StatusBanner loading={loading} error={error} success={success} />
      <section className="card ds-feedback-options">
        <label className="ds-check">
          <input
            type="checkbox"
            checked={refreshMasterPlan}
            onChange={(e) => setRefreshMasterPlan(e.target.checked)}
          />
          排程完成后自动滚动更新主计划（反馈闭环）
        </label>
        <label className="ds-cutoff">
          <span>反馈截止日（≤ 该日完成的工序冻结）</span>
          <input
            type="date"
            className="input"
            value={feedbackCutoff}
            onChange={(e) => setFeedbackCutoff(e.target.value)}
            disabled={!refreshMasterPlan}
          />
        </label>
      </section>
      <p className="hint">
        排程前请完成主计划运行，并在 <Link to="/master-plan/work-orders">生产工单</Link> 中确认并发布工单。
      </p>
      {masterPlan && (
        <p className="hint">关联主计划版本：{masterPlan.planVersionId}</p>
      )}
      {showDsDiagnostics && (
        <section className="card ds-diagnostics-panel">
          <h3>详细排程推演诊断（S05）</h3>
          <PlanningDiagnosticsPanel
            layer="detail-schedule"
            contextId={masterPlan?.planVersionId}
            autoLoad
          />
        </section>
      )}
      {detailSchedule && (
        <div className="meta-row">
          <span>版本 <strong>{detailSchedule.planVersionId}</strong></span>
          <span>得分 {detailSchedule.score}</span>
          <span>耗时 {detailSchedule.solveDurationMs} ms</span>
          <span>工序 {detailSchedule.operations.length}</span>
        </div>
      )}
      <VerticalResizeSplit
        className="ds-split"
        storageKey="detail-schedule-split-ratio"
        minTopRatio={0.35}
        maxTopRatio={0.85}
        top={
          <MachineScheduleGantt
            operations={detailSchedule?.operations ?? []}
            className="ds-gantt-panel"
          />
        }
        bottom={
          <div className="ds-bottom-scroll">
            <section className="card">
              <h3>工序列表</h3>
              <DataTable
                tableId="detail-schedule-operations"
                rows={detailSchedule?.operations ?? []}
                rowKey={(row) => row.operationId}
                columns={[
                  { key: 'op', header: '工序', render: (r) => r.operationId },
                  { key: 'wo', header: '工单', render: (r) => r.workOrderNo },
                  { key: 'machine', header: '机台', render: (r) => r.resourceId },
                  { key: 'seq', header: '顺序', render: (r) => r.sequenceIndex },
                  { key: 'line', header: '产线', render: (r) => r.lineId },
                  { key: 'product', header: '产品', render: (r) => r.productCode },
                  { key: 'start', header: '开始(分)', render: (r) => r.startMinute },
                  { key: 'end', header: '结束(分)', render: (r) => r.endMinute },
                  { key: 'pin', header: '锁定', render: (r) => (r.pinned ? '是' : '否') },
                ]}
                emptyText="请先求解详细排程"
              />
            </section>
            {(detailSchedule?.shortageRecommendations.length ?? 0) > 0 && (
              <section className="card">
                <h3>缺口建议 (§H)</h3>
                <DataTable
                  tableId="detail-schedule-shortages"
                  rows={detailSchedule!.shortageRecommendations}
                  rowKey={(row, index) => `${row.shortageType}-${row.lineId}-${index}`}
                  columns={[
                    { key: 'type', header: '类型', render: (r) => r.shortageType },
                    { key: 'sev', header: '严重度', render: (r) => r.severity },
                    { key: 'line', header: '产线', render: (r) => r.lineId },
                    { key: 'action', header: '建议', render: (r) => r.recommendedAction },
                  ]}
                />
              </section>
            )}
          </div>
        }
      />
    </div>
  );
}
