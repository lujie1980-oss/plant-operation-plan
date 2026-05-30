import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  PlanningConstraintMatchTotal,
  PlanningScoreExplanation,
  PlanningScoreLayer,
} from '../types/planningScoreExplanation';
import './PlanningScoreExplanationPanel.css';

export interface PlanningScoreExplanationPanelProps {
  layer: PlanningScoreLayer;
  planVersionId?: string | null;
  /** 细排 explain 必填 */
  masterPlanVersionId?: string | null;
  autoLoad?: boolean;
}

function fmtScore(hard: number, soft: number): string {
  return `${hard}hard/${soft}soft`;
}

export function PlanningScoreExplanationPanel({
  layer,
  planVersionId,
  masterPlanVersionId = null,
  autoLoad = false,
}: PlanningScoreExplanationPanelProps) {
  const [data, setData] = useState<PlanningScoreExplanation | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const canLoad =
    !!planVersionId && (layer === 'master-plan' || !!masterPlanVersionId);

  const load = useCallback(async () => {
    if (!canLoad || !planVersionId) {
      return;
    }
    setLoading(true);
    setErr(null);
    try {
      const result =
        layer === 'master-plan'
          ? await api.explainMasterPlanScore(planVersionId)
          : await api.explainDetailScheduleScore(planVersionId, masterPlanVersionId!);
      setData(result);
    } catch (e: unknown) {
      setData(null);
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [canLoad, layer, masterPlanVersionId, planVersionId]);

  useEffect(() => {
    if (autoLoad && canLoad && !data && !loading && !err) {
      void load();
    }
  }, [autoLoad, canLoad, data, err, load, loading]);

  const totals = data?.constraintTotals ?? [];
  const hardViolations = totals.filter((t) => t.hardScore !== 0);
  const softPenalties = totals.filter((t) => t.hardScore === 0 && t.softScore !== 0);

  return (
    <div className="planning-score-panel">
      <div className="planning-score-toolbar">
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          disabled={!canLoad || loading}
          onClick={() => void load()}
        >
          {loading ? '分解中…' : '分解得分'}
        </button>
        {!canLoad && (
          <span className="planning-score-hint">
            {layer === 'detail-schedule'
              ? '需要详细排程版本 ID 与主计划版本 ID'
              : '需要主计划版本 ID'}
          </span>
        )}
      </div>
      {err && <p className="planning-score-error">{err}</p>}
      {data && (
        <>
          <div className="planning-score-summary">
            <span className="planning-score-badge">{data.score}</span>
            <span>
              hard {data.hardScore} · soft {data.softScore}
            </span>
            {data.matchesTruncated && (
              <span className="planning-score-truncated">匹配样本已截断</span>
            )}
          </div>
          {hardViolations.length > 0 && (
            <ConstraintGroup title="硬约束违反" totals={hardViolations} />
          )}
          {softPenalties.length > 0 && (
            <ConstraintGroup title="软约束惩罚" totals={softPenalties} />
          )}
          {totals.length === 0 && (
            <p className="planning-score-empty">无约束匹配（得分为 0）。</p>
          )}
        </>
      )}
    </div>
  );
}

function ConstraintGroup({
  title,
  totals,
}: {
  title: string;
  totals: PlanningConstraintMatchTotal[];
}) {
  return (
    <section className="planning-score-group">
      <h4>{title}</h4>
      <table className="planning-score-table">
        <thead>
          <tr>
            <th>约束</th>
            <th>得分</th>
            <th>匹配数</th>
            <th>样本</th>
          </tr>
        </thead>
        <tbody>
          {totals.map((t) => (
            <tr key={t.constraintId}>
              <td>{t.constraintName}</td>
              <td>{fmtScore(t.hardScore, t.softScore)}</td>
              <td>{t.matchCount}</td>
              <td>
                {t.sampleMatches.length === 0 ? (
                  '—'
                ) : (
                  <ul className="planning-score-samples">
                    {t.sampleMatches.map((m) => (
                      <li key={m.identification}>
                        <code>{m.identification}</code>
                        <span>
                          {' '}
                          ({fmtScore(m.hardScore, m.softScore)})
                        </span>
                        {m.indictedIds.length > 0 && (
                          <span className="planning-score-indicted">
                            {' '}
                            → {m.indictedIds.join(', ')}
                          </span>
                        )}
                      </li>
                    ))}
                    {t.sampleTruncated && (
                      <li className="planning-score-more">…更多匹配未展示</li>
                    )}
                  </ul>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
