import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { ScheduleFeedback } from '../types/api';
import './SchedulerFeedbackRulesPanel.css';

/** §16 / RULE-SUP-05：冻结细排反馈只读视图（txn schedule_feedback） */
export function SchedulerFeedbackRulesPanel() {
  const [rows, setRows] = useState<ScheduleFeedback[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .listScheduleFeedback({ frozenThrough: new Date().toISOString().slice(0, 10) })
      .then((data) => {
        if (!cancelled) setRows(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : '加载细排反馈失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <p className="empty-hint">加载细排反馈…</p>;
  }
  if (error) {
    return <p className="editable-table-error">{error}</p>;
  }
  if (rows.length === 0) {
    return (
      <p className="empty-hint">
        当前无冻结细排反馈。细排确认（S05）后，占用分钟将写入 PRP 并 rollup 至 SRP。
      </p>
    );
  }

  const totalMinutes = rows.reduce((sum, r) => sum + r.durationMinutes, 0);

  return (
    <div className="scheduler-feedback-rules-panel">
      <p className="scheduler-feedback-summary">
        冻结反馈 {rows.length} 条 · 合计占用 {totalMinutes} 分钟（按 SR 汇总见「资源效率」页签）
      </p>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>槽位日期</th>
              <th>工单</th>
              <th>工序</th>
              <th>标准资源</th>
              <th>产线/PR</th>
              <th>占用(分)</th>
              <th>范围</th>
              <th>细排版本</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.feedbackId}>
                <td>{r.slotDate}</td>
                <td className="mono">{r.workOrderNo}</td>
                <td>{r.operationSeq}</td>
                <td className="mono">{r.resourceId}</td>
                <td className="mono">{r.physicalResourceId ?? '—'}</td>
                <td className="num">{r.durationMinutes}</td>
                <td>{r.scope}</td>
                <td className="mono">{r.detailScheduleVersionId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
