import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { GanttPanel } from '../components/GanttPanel';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import { detailScheduleToGanttTasks, masterPlanToGanttTasks } from '../utils/ganttMappers';
import './DashboardPage.css';

export function PipelinePage() {
  const { setMasterPlan, setDetailSchedule, setPipeline } = usePlan();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [includeDetailSchedule, setIncludeDetailSchedule] = useState(true);
  const [refreshAfterSchedule, setRefreshAfterSchedule] = useState(true);

  const [masterGantt, setMasterGantt] = useState<ReturnType<typeof masterPlanToGanttTasks>>([]);
  const [detailGantt, setDetailGantt] = useState<ReturnType<typeof detailScheduleToGanttTasks>>([]);

  const run = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const strategies = await api.listMasterPlanStrategies();
      const strategyId = strategies.find((s) => s.isDefault)?.id ?? strategies[0]?.id;
      if (!strategyId) {
        throw new Error('请先在优化目标中配置主计划策略');
      }
      const result = await api.runFullPipeline(strategyId, {
        includeDetailSchedule,
        refreshMasterPlanAfterSchedule: refreshAfterSchedule,
      });
      setPipeline(result);
      setMasterPlan(result.masterPlan);
      setDetailSchedule(result.detailSchedule ?? null);
      setMasterGantt(masterPlanToGanttTasks(result.masterPlan.allocations));
      setDetailGantt(
        result.detailSchedule
          ? detailScheduleToGanttTasks(result.detailSchedule.operations)
          : [],
      );
      if (result.masterPlanRefresh) {
        setSuccess(
          `全链路闭环：排程 ${result.detailSchedule?.planVersionId}，主计划已更新 ${result.masterPlan.planVersionId}`,
        );
      } else if (result.detailSchedule) {
        setSuccess(
          `全链路完成：主计划 ${result.masterPlan.planVersionId}，排程 ${result.detailSchedule.planVersionId}`,
        );
      } else {
        setSuccess(`主计划运行完成：${result.masterPlan.planVersionId}`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '流水线失败');
    } finally {
      setLoading(false);
    }
  };

  const steps = ['S01 需求满足', 'S02 物料需求', 'S03 产能平衡', 'S04 主计划', 'S05 排程', 'S06 下发', 'S07 KPI'];

  return (
    <>
      <PageHeader
        title="全链路编排"
        description="一键执行 S01→S07，并预览主计划与排程甘特图"
        actions={
          <button type="button" className="btn primary" onClick={() => void run()} disabled={loading}>
            运行全链路
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} success={success} />
      <section className="card">
        <div className="dash-run-options">
          <label className="dash-check">
            <input
              type="checkbox"
              checked={includeDetailSchedule}
              onChange={(e) => {
                setIncludeDetailSchedule(e.target.checked);
                if (!e.target.checked) setRefreshAfterSchedule(false);
              }}
              disabled={loading}
            />
            含详细排程
          </label>
          <label className="dash-check">
            <input
              type="checkbox"
              checked={refreshAfterSchedule}
              onChange={(e) => setRefreshAfterSchedule(e.target.checked)}
              disabled={loading || !includeDetailSchedule}
            />
            排程后滚动更新主计划
          </label>
        </div>
        <div className="pipeline-steps">
          {steps.map((s) => (
            <span key={s} className="pipeline-step">{s}</span>
          ))}
        </div>
        <p className="hint">
          完成后可在 <Link to="/master-plan/plan-run">订单协同计划</Link> 与{' '}
          <Link to="/scheduling/detail-schedule">详细排程</Link> 页面继续操作。
        </p>
      </section>
      {masterGantt.length > 0 && <GanttPanel tasks={masterGantt} title="主计划甘特（全链路结果）" />}
      {detailGantt.length > 0 && <GanttPanel tasks={detailGantt} title="详细排程甘特（全链路结果）" />}
    </>
  );
}
