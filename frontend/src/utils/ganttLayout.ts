import { ViewMode, type Task } from 'gantt-task-react';

const DAY_MS = 86_400_000;

function addToDate(date: Date, quantity: number, scale: 'day' | 'hour' | 'month' | 'year'): Date {
  const d = new Date(date);
  switch (scale) {
    case 'day':
      d.setDate(d.getDate() + quantity);
      break;
    case 'hour':
      d.setHours(d.getHours() + quantity);
      break;
    case 'month':
      d.setMonth(d.getMonth() + quantity);
      break;
    case 'year':
      d.setFullYear(d.getFullYear() + quantity);
      break;
  }
  return d;
}

function startOfDate(date: Date, scale: 'day' | 'hour'): Date {
  const d = new Date(date);
  if (scale === 'day') {
    d.setHours(0, 0, 0, 0);
  } else {
    d.setMinutes(0, 0, 0);
  }
  return d;
}

/** 与 gantt-task-react 一致的可见任务（折叠工序后） */
export function ganttVisibleTasks(tasks: Task[]): Task[] {
  let visible = [...tasks];
  const grouped = visible.filter((t) => t.hideChildren && t.type === 'project');
  for (const parent of grouped) {
    const childIds = new Set(
      visible.filter((t) => t.project === parent.id).map((t) => t.id),
    );
    visible = visible.filter((t) => !childIds.has(t.id));
  }
  return visible.sort(
    (a, b) => (a.displayOrder ?? Number.MAX_VALUE) - (b.displayOrder ?? Number.MAX_VALUE),
  );
}

/** 与库内 ganttDateRange + seedDates 对齐 */
export function ganttChartDates(
  tasks: Task[],
  viewMode: ViewMode,
  preStepsCount = 1,
): Date[] {
  const visible = ganttVisibleTasks(tasks);
  if (visible.length === 0) return [new Date()];

  let newStart = new Date(visible[0].start);
  let newEnd = new Date(visible[0].start);
  for (const t of visible) {
    if (t.start < newStart) newStart = new Date(t.start);
    if (t.end > newEnd) newEnd = new Date(t.end);
  }

  switch (viewMode) {
    case ViewMode.Week:
      newStart = startOfDate(newStart, 'day');
      newStart = addToDate(newStart, -7 * preStepsCount, 'day');
      newEnd = startOfDate(newEnd, 'day');
      newEnd = addToDate(newEnd, 45, 'day');
      break;
    case ViewMode.Hour:
      newStart = startOfDate(newStart, 'hour');
      newStart = addToDate(newStart, -preStepsCount, 'hour');
      newEnd = startOfDate(newEnd, 'day');
      newEnd = addToDate(newEnd, 1, 'day');
      break;
    case ViewMode.Day:
    default:
      newStart = startOfDate(newStart, 'day');
      newStart = addToDate(newStart, -preStepsCount, 'day');
      newEnd = startOfDate(newEnd, 'day');
      newEnd = addToDate(newEnd, 19, 'day');
      break;
  }

  const dates: Date[] = [new Date(newStart)];
  let current = new Date(newStart);
  while (current < newEnd) {
    switch (viewMode) {
      case ViewMode.Week:
        current = addToDate(current, 7, 'day');
        break;
      case ViewMode.Hour:
        current = addToDate(current, 1, 'hour');
        break;
      default:
        current = addToDate(current, 1, 'day');
        break;
    }
    dates.push(new Date(current));
  }
  return dates;
}

export function taskXCoordinate(xDate: Date, dates: Date[], columnWidth: number): number {
  let index = dates.findIndex((d) => d.getTime() >= xDate.getTime()) - 1;
  if (index < 0) index = 0;
  const next = Math.min(index + 1, dates.length - 1);
  const t0 = dates[index].getTime();
  const t1 = dates[next].getTime();
  const span = t1 - t0 || DAY_MS;
  const ratio = (xDate.getTime() - t0) / span;
  return index * columnWidth + ratio * columnWidth;
}

export function barCenterY(
  rowIndex: number,
  rowHeight: number,
  taskHeight: number,
): number {
  const y = rowIndex * rowHeight + (rowHeight - taskHeight) / 2;
  return y + taskHeight / 2;
}

export interface PegArrowPath {
  key: string;
  d: string;
  pegType: string;
  stroke: string;
  dash?: string;
}

export function buildPegArrowPaths(
  tasks: Task[],
  edges: { fromNodeId: string; toNodeId: string; pegType: string }[],
  viewMode: ViewMode,
  columnWidth: number,
  rowHeight: number,
  barFill: number,
): PegArrowPath[] {
  const visible = ganttVisibleTasks(tasks);
  const dates = ganttChartDates(visible, viewMode);
  const taskHeight = (rowHeight * barFill) / 100;
  const indexById = new Map(visible.map((t, i) => [t.id, i]));
  const indent = 20;
  const styles: Record<string, { stroke: string; dash?: string }> = {
    INVENTORY_PEG: { stroke: '#10b981' },
    WORK_ORDER_PEG: { stroke: '#3b82f6' },
    SHORTAGE_PEG: { stroke: '#ef4444', dash: '6 4' },
  };

  return edges
    .map((edge) => {
      const fromIdx = indexById.get(edge.fromNodeId);
      const toIdx = indexById.get(edge.toNodeId);
      if (fromIdx === undefined || toIdx === undefined) return null;

      const from = visible[fromIdx];
      const to = visible[toIdx];
      if (!from?.end || !to?.start) return null;

      const x1 = taskXCoordinate(from.end, dates, columnWidth) + indent;
      const x2 = taskXCoordinate(to.start, dates, columnWidth) - indent;
      const y1 = barCenterY(fromIdx, rowHeight, taskHeight);
      const y2 = barCenterY(toIdx, rowHeight, taskHeight);
      const midX = (x1 + x2) / 2;
      const style = styles[edge.pegType] ?? styles.WORK_ORDER_PEG;

      return {
        key: `${edge.fromNodeId}-${edge.toNodeId}`,
        pegType: edge.pegType,
        d: `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`,
        ...style,
      };
    })
    .filter((p): p is PegArrowPath => p != null);
}
