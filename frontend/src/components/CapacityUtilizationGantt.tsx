import { useMemo } from 'react';
import type { LoadBucket } from '../types/api';
import {
  buildCapacityGanttModel,
  UTILIZATION_BAND_ORDER,
  utilizationBand,
  utilizationBandLabel,
} from '../utils/capacityUtilization';
import './CapacityUtilizationGantt.css';

const CELL_W = 76;
const ROW_H = 31;
const LABEL_W = 132;

interface CapacityUtilizationGanttProps {
  buckets: LoadBucket[];
  selectedBucketId: string | null;
  onSelectBucket: (bucket: LoadBucket) => void;
  /** UI-NAV-01: highlight row when deep-linked by resource */
  focusResourceId?: string | null;
}

export function CapacityUtilizationGantt({
  buckets,
  selectedBucketId,
  onSelectBucket,
  focusResourceId = null,
}: CapacityUtilizationGanttProps) {
  const model = useMemo(() => buildCapacityGanttModel(buckets), [buckets]);
  const { resourceIds, columns, cellMap } = model;

  if (buckets.length === 0) {
    return <p className="cap-gantt-empty">请先执行产能平衡分析</p>;
  }

  const gridWidth = columns.length * CELL_W;

  return (
    <div className="cap-gantt">
      <div className="cap-gantt-legend">
        {UTILIZATION_BAND_ORDER.map((band) => (
          <span key={band} className={`cap-leg band-${band}`}>
            {utilizationBandLabel(band)}
          </span>
        ))}
        <span className="cap-leg cap-leg-feedback">排程反馈锁定</span>
      </div>
      <div className="cap-gantt-scroll">
        <div
          className="cap-gantt-grid"
          style={{ minWidth: LABEL_W + gridWidth }}
        >
          <div
            className="cap-gantt-header-row"
            style={{ gridTemplateColumns: `${LABEL_W}px repeat(${columns.length}, ${CELL_W}px)` }}
          >
            <div className="cap-gantt-corner">机台 / 区间</div>
            {columns.map((col) => (
              <div key={col.key} className="cap-gantt-col-head" title={col.key}>
                <span className="cap-col-date">{col.label}</span>
              </div>
            ))}
          </div>
          {resourceIds.map((resId) => {
            const label = buckets.find((b) => b.resourceId === resId)?.resourceLabel ?? resId;
            const focused = focusResourceId != null && resId === focusResourceId;
            return (
              <div
                key={resId}
                className={`cap-gantt-body-row ${focused ? 'is-focused' : ''}`.trim()}
                style={{ gridTemplateColumns: `${LABEL_W}px repeat(${columns.length}, ${CELL_W}px)` }}
              >
                <div className="cap-gantt-row-label" title={resId}>
                  {label}
                </div>
                {columns.map((col) => {
                  const bucket = cellMap.get(`${resId}|${col.key}`);
                  if (!bucket) {
                    return (
                      <div key={col.key} className="cap-gantt-cell cap-gantt-cell-empty band-idle" />
                    );
                  }
                  const band = utilizationBand(bucket.utilizationPct);
                  const selected = selectedBucketId === bucket.bucketId;
                  const locked = bucket.feedbackLockedMinutes ?? 0;
                  const lockedPct =
                    bucket.availableMinutes > 0
                      ? Math.min(100, (locked / bucket.availableMinutes) * 100)
                      : 0;
                  return (
                    <button
                      key={col.key}
                      type="button"
                      className={`cap-gantt-cell band-${band} ${selected ? 'is-selected' : ''} ${locked > 0 ? 'has-feedback-lock' : ''}`}
                      style={{ height: ROW_H }}
                      onClick={() => onSelectBucket(bucket)}
                      title={`${label} ${col.label}：利用率 ${bucket.utilizationPct}% · 反馈锁定 ${locked} 分 / 负荷 ${bucket.demandMinutes} 分`}
                    >
                      {lockedPct > 0 && (
                        <span
                          className="cap-feedback-lock-bar"
                          style={{ height: `${lockedPct}%` }}
                          aria-hidden
                        />
                      )}
                      <span className="cap-cell-pct">{bucket.utilizationPct}%</span>
                      <span className="cap-cell-sub">
                        {bucket.demandMinutes}/{bucket.availableMinutes}分
                        {locked > 0 ? ` · 锁${locked}` : ''}
                      </span>
                    </button>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
