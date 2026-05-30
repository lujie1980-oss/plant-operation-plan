import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type {
  DetailSchedulePlanningDiagnostics,
  MasterPlanPlanningDiagnostics,
  PlanningDiagnosticIssue,
  PlanningDiagnosticsLayer,
} from '../types/planningDiagnostics';
import {
  countIssues,
  counterValue,
  DETAIL_SCHEDULE_WARN_COUNTERS,
  fmtDiagnosticTime,
  funnelSteps,
  MASTER_PLAN_SKIP_COUNTERS,
  MASTER_PLAN_WARN_COUNTERS,
  metaLines,
  reasonLabel,
  summaryLevel,
} from '../utils/planningDiagnosticsModel';
import './PlanningDiagnosticsPanel.css';

type SeverityFilter = 'ALL' | 'SKIP' | 'WARN' | 'INFO';

export interface PlanningDiagnosticsPanelProps {
  layer: PlanningDiagnosticsLayer;
  /** 主计划：strategyId；详细排程：masterPlanVersionId */
  contextId?: string | null;
  /** 已持久化快照（如 pipeline run），优先于 API 拉取 */
  snapshot?: MasterPlanPlanningDiagnostics | DetailSchedulePlanningDiagnostics | null;
  /** S04 preview：启用反馈 overlay */
  feedbackCutoff?: string | null;
  autoLoad?: boolean;
  compact?: boolean;
  readOnly?: boolean;
}

export function PlanningDiagnosticsPanel({
  layer,
  contextId,
  snapshot = null,
  feedbackCutoff = null,
  autoLoad = false,
  compact = false,
  readOnly = false,
}: PlanningDiagnosticsPanelProps) {
  const [fetched, setFetched] = useState<
    MasterPlanPlanningDiagnostics | DetailSchedulePlanningDiagnostics | null
  >(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('ALL');
  const [woFilter, setWoFilter] = useState('');

  const data = snapshot ?? fetched;

  const load = useCallback(async () => {
    if (readOnly || !contextId) {
      return;
    }
    setLoading(true);
    setErr(null);
    try {
      const result =
        layer === 'master-plan'
          ? await api.previewMasterPlanDiagnostics(
              contextId,
              feedbackCutoff ?? undefined,
            )
          : await api.previewDetailScheduleDiagnostics(contextId);
      setFetched(result);
    } catch (e: unknown) {
      setFetched(null);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [contextId, feedbackCutoff, layer, readOnly]);

  useEffect(() => {
    if (snapshot) {
      setFetched(null);
      setErr(null);
      return;
    }
    if (autoLoad && contextId && !readOnly) {
      void load();
    }
  }, [autoLoad, contextId, feedbackCutoff, load, readOnly, snapshot]);

  const issues = useMemo(() => {
    if (!data) return [];
    const wo = woFilter.trim().toLowerCase();
    return data.issues.filter((item) => {
      if (severityFilter !== 'ALL' && item.severity !== severityFilter) return false;
      if (wo && !(item.workOrderNo ?? '').toLowerCase().includes(wo)) return false;
      return true;
    });
  }, [data, severityFilter, woFilter]);

  const issueTotals = useMemo(() => countIssues(data?.issues ?? []), [data]);
  const level = summaryLevel(issueTotals.skip, issueTotals.warn);

  const skipCounters =
    layer === 'master-plan'
      ? MASTER_PLAN_SKIP_COUNTERS
      : [
          { key: 'workOrdersSkippedNotSchedulable', label: '不可排程' },
          { key: 'workOrdersSkippedNoRouting', label: '无工艺' },
        ];
  const warnCounters = layer === 'master-plan' ? MASTER_PLAN_WARN_COUNTERS : DETAIL_SCHEDULE_WARN_COUNTERS;

  const missingContextHint =
    layer === 'master-plan'
      ? '请选择主计划策略后点击「刷新诊断」。'
      : '请先完成主计划运行，再刷新详细排程诊断。';

  return (
    <div className={`pdiag-panel ${compact ? 'pdiag-compact' : ''}`}>
      <div className="pdiag-toolbar">
        <p className="pdiag-desc">
          {readOnly
            ? '来自计划运行记录的推演快照（求解前采集）。'
            : layer === 'master-plan'
              ? '在 Timefold 求解前预览主计划推演结果：工单筛选、分配展开与 eligible 槽位过滤。'
              : '在 Timefold 求解前预览详细排程推演：齐套、主计划契约与绑定规则。'}
        </p>
        <div className="pdiag-actions">
          <label className="pdiag-filter">
            级别
            <select
              className="input"
              value={severityFilter}
              onChange={(e) => setSeverityFilter(e.target.value as SeverityFilter)}
              disabled={!data}
            >
              <option value="ALL">全部</option>
              <option value="SKIP">跳过</option>
              <option value="WARN">预警</option>
              <option value="INFO">信息</option>
            </select>
          </label>
          <label className="pdiag-filter">
            工单
            <input
              className="input pdiag-search"
              value={woFilter}
              onChange={(e) => setWoFilter(e.target.value)}
              placeholder="过滤工单号"
              disabled={!data}
            />
          </label>
          {!readOnly && (
            <button
              type="button"
              className="btn btn-secondary"
              disabled={loading || !contextId}
              onClick={() => void load()}
            >
              {loading ? '加载中…' : '刷新诊断'}
            </button>
          )}
        </div>
      </div>

      {err && <div className="pdiag-alert">诊断加载失败：{err}</div>}
      {!err && !data && !loading && (
        <p className="pdiag-hint">
          {readOnly ? '该运行记录未保存推演诊断（旧版本运行）。' : contextId ? '点击「刷新诊断」获取推演快照。' : missingContextHint}
        </p>
      )}

      {data && (
        <>
          <div className={`pdiag-summary pdiag-${level}`}>
            <div className="pdiag-stat">
              <span className="pdiag-stat-value">{issueTotals.skip}</span>
              <span className="pdiag-stat-label">跳过样本</span>
            </div>
            <div className="pdiag-stat">
              <span className="pdiag-stat-value">{issueTotals.warn}</span>
              <span className="pdiag-stat-label">预警样本</span>
            </div>
            <div className="pdiag-stat">
              <span className="pdiag-stat-value">{issues.length}</span>
              <span className="pdiag-stat-label">当前列表</span>
            </div>
            <div className="pdiag-meta">
              <span>采集于 {fmtDiagnosticTime(data.computedAt)}</span>
              {metaLines(layer, data).map((line) => (
                <span key={line}>{line}</span>
              ))}
            </div>
          </div>

          <div className="pdiag-funnel" aria-label="推演漏斗">
            {funnelSteps(layer).map((step, index) => (
              <div key={step.key} style={{ display: 'contents' }}>
                {index > 0 && <span className="pdiag-funnel-arrow" aria-hidden>→</span>}
                <div className="pdiag-funnel-step">
                  <strong>{counterValue(data, step.key)}</strong>
                  <span>{step.label}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="pdiag-chips">
            {skipCounters.map((c) => {
              const n = counterValue(data, c.key);
              if (n <= 0) return null;
              return (
                <span key={c.key} className="pdiag-chip">
                  {c.label} <strong>{n}</strong>
                </span>
              );
            })}
            {warnCounters.map((c) => {
              const n = counterValue(data, c.key);
              if (n <= 0) return null;
              return (
                <span key={c.key} className="pdiag-chip">
                  {c.label} <strong>{n}</strong>
                </span>
              );
            })}
          </div>

          {data.issuesTruncated && (
            <p className="pdiag-truncated">仅展示前 100 条 issue 样本，完整数量请以 counters 为准。</p>
          )}

          <div className="pdiag-table-wrap">
            <table className="pdiag-table">
              <thead>
                <tr>
                  <th>级别</th>
                  <th>类型</th>
                  <th>工单</th>
                  <th>实体</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                {issues.length === 0 ? (
                  <tr>
                    <td colSpan={5}>当前筛选条件下无诊断条目</td>
                  </tr>
                ) : (
                  issues.map((item, index) => (
                    <IssueRow key={`${item.reasonCode}-${item.workOrderNo}-${item.entityId}-${index}`} item={item} />
                  ))
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function IssueRow({ item }: { item: PlanningDiagnosticIssue }) {
  const pillClass =
    item.severity === 'SKIP'
      ? 'pdiag-pill-skip'
      : item.severity === 'WARN'
        ? 'pdiag-pill-warn'
        : 'pdiag-pill-info';
  const severityLabel =
    item.severity === 'SKIP' ? '跳过' : item.severity === 'WARN' ? '预警' : '信息';

  return (
    <tr>
      <td>
        <span className={`pdiag-pill ${pillClass}`}>{severityLabel}</span>
      </td>
      <td>{reasonLabel(item.reasonCode)}</td>
      <td className="pdiag-wo">{item.workOrderNo ?? '—'}</td>
      <td className="pdiag-wo">{item.entityId ?? '—'}</td>
      <td>{item.message}</td>
    </tr>
  );
}
