import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { DemandPoolKpi, DetailScheduleOperation } from '../types/api';
import type { DetailSchedulePlanningPreview } from '../types/detailSchedulePlanningPreview';
import { previewOperationsToGantt } from '../utils/previewOperationsToGantt';

export interface DetailScheduleKpiPanelProps {
  detailScheduleVersionId?: string | null;
  preview?: DetailSchedulePlanningPreview | null;
}

export function DetailScheduleKpiPanel({
  detailScheduleVersionId,
  preview,
}: DetailScheduleKpiPanelProps) {
  const [kpis, setKpis] = useState<DemandPoolKpi[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const previewOps: DetailScheduleOperation[] = preview
          ? previewOperationsToGantt(preview.operations)
          : [];
        const list =
          previewOps.length > 0
            ? await api.detailSchedulePageKpis({
                detailScheduleVersionId: detailScheduleVersionId ?? undefined,
                operations: previewOps,
              })
            : await api.detailSchedulePageKpis(detailScheduleVersionId ?? undefined);
        if (!cancelled) {
          setKpis(list);
        }
      } catch {
        if (!cancelled) {
          setKpis([]);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [detailScheduleVersionId, preview]);

  return (
    <aside className="ds-kpi-panel">
      <h3 className="panel-title">关键 KPI</h3>
      {preview && (
        <p className="ds-kpi-session-meta muted-text">
          推演 {preview.scheduledOperationCount}/{preview.operationCount} 已排
          {preview.simulationMode ? ` · ${preview.simulationMode}` : ''}
        </p>
      )}
      <div className="panel-scroll kpi-scroll">
        <ul className="kpi-list">
          {kpis.map((k) => (
            <li key={k.metricId} className={`kpi-item severity-${k.severity}`}>
              <span className="kpi-item-label">{k.label}</span>
              <span className="kpi-item-value">
                {k.value.toLocaleString(undefined, { maximumFractionDigits: 1 })}
                <small>{k.unit}</small>
              </span>
            </li>
          ))}
        </ul>
        {kpis.length === 0 && !loading && (
          <p className="empty">创建 Session 或求解排程后显示 KPI</p>
        )}
        {loading && kpis.length === 0 && <p className="empty">加载中…</p>}
      </div>
    </aside>
  );
}
