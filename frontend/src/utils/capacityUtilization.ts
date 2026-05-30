import type { LoadBucket } from '../types/api';

export type UtilizationBand = 'idle' | 'light' | 'normal' | 'high' | 'overload';

/** 0% 灰；>0–<50% 浅绿；50–90% 深绿；>90–100% 黄；>100% 红 */
export function utilizationBand(pct: number): UtilizationBand {
  if (pct > 100) return 'overload';
  if (pct > 90) return 'high';
  if (pct >= 50) return 'normal';
  if (pct > 0) return 'light';
  return 'idle';
}

export const UTILIZATION_BAND_ORDER: UtilizationBand[] = [
  'idle',
  'light',
  'normal',
  'high',
  'overload',
];

export function utilizationBandLabel(band: UtilizationBand): string {
  switch (band) {
    case 'idle':
      return '0%';
    case 'light':
      return '>0–<50%';
    case 'normal':
      return '50–90%';
    case 'high':
      return '>90–100%';
    case 'overload':
      return '>100%';
  }
}

export function bucketColumnKey(date: string, shiftId: string): string {
  return `${date}|${shiftId}`;
}

export function formatBucketColumnLabel(date: string, shiftId: string): string {
  const d = new Date(date + 'T00:00:00');
  const md = `${d.getMonth() + 1}/${d.getDate()}`;
  return `${md} · ${shiftId}`;
}

export function buildCapacityGanttModel(buckets: LoadBucket[]) {
  const resourceIds = [...new Set(buckets.map((b) => b.resourceId))].sort((a, b) =>
    a.localeCompare(b, 'zh-CN'),
  );
  const columns = [
    ...new Map(
      buckets.map((b) => {
        const key = bucketColumnKey(b.date, b.shiftId);
        return [key, { key, date: b.date, shiftId: b.shiftId, label: formatBucketColumnLabel(b.date, b.shiftId) }];
      }),
    ).values(),
  ].sort((a, b) => a.date.localeCompare(b.date) || a.shiftId.localeCompare(b.shiftId));

  const cellMap = new Map<string, LoadBucket>();
  for (const b of buckets) {
    cellMap.set(`${b.resourceId}|${bucketColumnKey(b.date, b.shiftId)}`, b);
  }

  return { resourceIds, columns, cellMap };
}
