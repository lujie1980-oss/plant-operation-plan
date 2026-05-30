import { useLayoutEffect, useMemo, useRef } from 'react';
import { ViewMode, type Task } from 'gantt-task-react';
import type { FulfillmentPegEdge } from '../types/api';
import { ganttPegEdges } from '../utils/fulfillmentGantt';
import { buildPegArrowPaths } from '../utils/ganttLayout';

const DEFAULT_ROW_HEIGHT = 35;
const BAR_FILL = 50;
const DEFAULT_HEADER_HEIGHT = 35;

const MARKER_DEFS = `
<defs>
  <marker id="peg-arrow-INVENTORY_PEG" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
    <path d="M0,0 L8,4 L0,8 z" fill="#10b981"/>
  </marker>
  <marker id="peg-arrow-WORK_ORDER_PEG" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
    <path d="M0,0 L8,4 L0,8 z" fill="#3b82f6"/>
  </marker>
  <marker id="peg-arrow-SHORTAGE_PEG" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
    <path d="M0,0 L8,4 L0,8 z" fill="#ef4444"/>
  </marker>
</defs>`;

interface FulfillmentPegArrowsProps {
  tasks: Task[];
  edges: FulfillmentPegEdge[];
  viewMode: ViewMode;
  columnWidth: number;
  rowHeight?: number;
  headerHeight?: number;
  enabled?: boolean;
}

/** 将贝塞尔满足链箭头绘制进 gantt-task-react 条形图 SVG，与条柱同一坐标系 */
export function FulfillmentPegArrows({
  tasks,
  edges,
  viewMode,
  columnWidth,
  rowHeight = DEFAULT_ROW_HEIGHT,
  headerHeight = DEFAULT_HEADER_HEIGHT,
  enabled = true,
}: FulfillmentPegArrowsProps) {
  const chainIds = useMemo(
    () => new Set(tasks.filter((t) => !t.project).map((t) => t.id)),
    [tasks],
  );

  const pegEdges = useMemo(
    () =>
      ganttPegEdges(edges).filter(
        (e) => chainIds.has(e.fromNodeId) && chainIds.has(e.toNodeId),
      ),
    [edges, chainIds],
  );

  const paths = useMemo(
    () =>
      buildPegArrowPaths(tasks, pegEdges, viewMode, columnWidth, rowHeight, BAR_FILL),
    [tasks, pegEdges, viewMode, columnWidth, rowHeight],
  );

  const pathsKey = useRef('');
  const pathsKeyStr = paths.map((p) => p.d).join('|');

  useLayoutEffect(() => {
    const svgs = [
      ...document.querySelectorAll('.fulfillment-gantt .gantt-container svg'),
    ] as SVGSVGElement[];
    const svg =
      svgs.find((s) => Number(s.getAttribute('height') ?? 0) > headerHeight) ??
      svgs[svgs.length - 1];
    if (!svg) return;

    let layer = svg.querySelector<SVGGElement>('g.fulfillment-pegs');
    if (!layer) {
      layer = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      layer.setAttribute('class', 'fulfillment-pegs');
      svg.appendChild(layer);
    }

    if (!enabled) {
      layer.innerHTML = '';
      pathsKey.current = '';
      return;
    }

    if (pathsKey.current === pathsKeyStr && layer.childElementCount > 0) {
      return;
    }
    pathsKey.current = pathsKeyStr;

    layer.innerHTML = MARKER_DEFS;
    for (const p of paths) {
      const pathEl = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      pathEl.setAttribute('d', p.d);
      pathEl.setAttribute('fill', 'none');
      pathEl.setAttribute('stroke', p.stroke);
      pathEl.setAttribute('stroke-width', '2.5');
      if (p.dash) pathEl.setAttribute('stroke-dasharray', p.dash);
      pathEl.setAttribute('marker-end', `url(#peg-arrow-${p.pegType})`);
      pathEl.setAttribute('opacity', '0.92');
      layer.appendChild(pathEl);
    }
  }, [paths, pathsKeyStr, tasks.length, viewMode, columnWidth, headerHeight, enabled]);

  useLayoutEffect(() => {
    return () => {
      document
        .querySelector('.fulfillment-gantt g.fulfillment-pegs')
        ?.remove();
      pathsKey.current = '';
    };
  }, []);

  return null;
}
