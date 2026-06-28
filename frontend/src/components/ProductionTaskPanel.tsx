import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DataTable } from './DataTable';
import type { ProductionTask } from '../types/scheduleSession';
import { executionStateLabel, formatDateTime } from '../utils/productionTaskGantt';
import './ProductionTaskPanel.css';

const STATE_FILTER_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'RELEASED', label: '已发布' },
  { value: 'RUNNING', label: '进行中' },
  { value: 'COMPLETED', label: '已完工' },
  { value: 'UNPLANNED', label: '未计划' },
  { value: 'ARCHIVED', label: '已归档' },
];

export interface ProductionTaskPanelProps {
  onTasksChange?: (tasks: ProductionTask[]) => void;
  refreshToken?: number;
  /** 按产线筛选（空=全部） */
  lineFilter?: string;
  hideLineFilter?: boolean;
}

export function ProductionTaskPanel({
  onTasksChange,
  refreshToken = 0,
  lineFilter: lineFilterProp,
  hideLineFilter = false,
}: ProductionTaskPanelProps) {
  const [tasks, setTasks] = useState<ProductionTask[]>([]);
  const [stateFilter, setStateFilter] = useState('');
  const [lineFilterLocal, setLineFilterLocal] = useState('');
  const lineFilter = lineFilterProp ?? lineFilterLocal;
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const result = await api.listProductionTasks(stateFilter || undefined);
      setTasks(result);
      onTasksChange?.(result);
    } catch (e: unknown) {
      setTasks([]);
      onTasksChange?.([]);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [stateFilter, onTasksChange]);

  const lineOptions = useMemo(() => {
    const ids = new Set<string>();
    for (const t of tasks) {
      if (t.lineId) ids.add(t.lineId);
    }
    return [...ids].sort((a, b) => a.localeCompare(b, 'zh-CN'));
  }, [tasks]);

  const displayedTasks = useMemo(() => {
    if (!lineFilter) return tasks;
    return tasks.filter((t) => t.lineId === lineFilter);
  }, [tasks, lineFilter]);

  useEffect(() => {
    void load();
  }, [load, refreshToken]);

  const runAction = async (stepId: string, action: 'start' | 'complete') => {
    setActionId(stepId);
    setErr(null);
    try {
      if (action === 'start') {
        await api.startProductionTask(stepId);
      } else {
        await api.completeProductionTask(stepId);
      }
      await load();
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setActionId(null);
    }
  };

  return (
    <div className="production-task-panel">
      <div className="production-task-toolbar">
        <label className="production-task-filter">
          <span>执行状态</span>
          <select
            className="input"
            value={stateFilter}
            onChange={(e) => setStateFilter(e.target.value)}
          >
            {STATE_FILTER_OPTIONS.map((o) => (
              <option key={o.value || 'all'} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        {!hideLineFilter && (
          <label className="production-task-filter">
            <span>产线</span>
            <select
              className="input"
              value={lineFilter}
              onChange={(e) => setLineFilterLocal(e.target.value)}
            >
              <option value="">全部产线</option>
              {lineOptions.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
        )}
        <button type="button" className="btn" disabled={loading} onClick={() => void load()}>
          {loading ? '刷新中…' : '刷新'}
        </button>
        <span className="production-task-hint muted-text">
          已发布任务（RELEASED）对车间可见；与上方甘特「已发布任务」视图联动。
        </span>
      </div>
      {err && <p className="error production-task-error">{err}</p>}
      <div className="production-task-table-wrap">
        <DataTable
          tableId="production-tasks"
          rows={displayedTasks}
          rowKey={(row) => row.stepId}
          loading={loading}
          getRowClassName={(row) =>
            row.executionState === 'RUNNING' ? 'production-task-row-running' : ''
          }
          columns={[
            { key: 'step', header: '工序', render: (r) => r.stepId },
            { key: 'wo', header: '工单', render: (r) => r.workOrderNo },
            { key: 'batch', header: '批次', render: (r) => r.batchNo ?? '—' },
            { key: 'name', header: '工序名', render: (r) => r.operationName || '—' },
            { key: 'line', header: '产线', render: (r) => r.lineId ?? '—' },
            {
              key: 'state',
              header: '状态',
              render: (r) => (
                <span className={`production-task-state state-${r.executionState.toLowerCase()}`}>
                  {executionStateLabel(r.executionState)}
                </span>
              ),
            },
            {
              key: 'planStart',
              header: '计划开始',
              render: (r) => formatDateTime(r.plannedStartTs),
            },
            {
              key: 'planEnd',
              header: '计划结束',
              render: (r) => formatDateTime(r.plannedEndTs),
            },
            { key: 'planVer', header: '排程版本', render: (r) => r.planVersionId ?? '—' },
            {
              key: 'actual',
              header: '实际开工',
              render: (r) => formatDateTime(r.actualStartTs),
            },
            {
              key: 'actions',
              header: '操作',
              render: (r) => {
                const busy = actionId === r.stepId;
                if (r.executionState === 'RELEASED') {
                  return (
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={busy}
                      onClick={() => void runAction(r.stepId, 'start')}
                    >
                      开工
                    </button>
                  );
                }
                if (r.executionState === 'RUNNING') {
                  return (
                    <button
                      type="button"
                      className="btn btn-sm primary"
                      disabled={busy}
                      onClick={() => void runAction(r.stepId, 'complete')}
                    >
                      完工
                    </button>
                  );
                }
                return '—';
              },
            },
          ]}
          emptyText="暂无生产任务；请在需求满足链中完成有限能力计划并确认发布。"
        />
      </div>
    </div>
  );
}
