import { useCallback, useMemo, useRef } from 'react';
import { ViewMode, type Task } from 'gantt-task-react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import {
  buildFulfillmentSyncRows,
  fulfillmentTreeNodeTypeLabel,
} from '../utils/fulfillmentChainTree';
import { FulfillmentGanttPanel } from './FulfillmentGanttPanel';
import './FulfillmentChainSyncView.css';

export const CHAIN_ROW_HEIGHT = 35;
export const CHAIN_HEADER_HEIGHT = 50;

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

interface FulfillmentChainSyncViewProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  tasks: Task[];
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string) => void;
  onTasksChange?: (tasks: Task[]) => void;
  showArrows: boolean;
  viewMode: ViewMode;
}

export function FulfillmentChainSyncView({
  nodes,
  edges,
  tasks,
  selectedNodeId,
  onSelectNode,
  onTasksChange,
  showArrows,
  viewMode,
}: FulfillmentChainSyncViewProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const ganttScrollRef = useRef<HTMLDivElement>(null);

  const syncRows = useMemo(
    () => buildFulfillmentSyncRows(nodes, edges, tasks),
    [nodes, edges, tasks],
  );

  const bodyHeight = syncRows.length * CHAIN_ROW_HEIGHT;

  const onOuterScroll = useCallback(() => {
    const outer = scrollRef.current;
    const inner = ganttScrollRef.current?.querySelector<HTMLElement>(
      'div[class*="horizontalContainer"]',
    );
    if (!outer || !inner) return;
    if (Math.abs(inner.scrollTop - outer.scrollTop) > 1) {
      inner.scrollTop = outer.scrollTop;
    }
  }, []);

  const onGanttScroll = useCallback(() => {
    const outer = scrollRef.current;
    const inner = ganttScrollRef.current?.querySelector<HTMLElement>(
      'div[class*="horizontalContainer"]',
    );
    if (!outer || !inner) return;
    if (Math.abs(outer.scrollTop - inner.scrollTop) > 1) {
      outer.scrollTop = inner.scrollTop;
    }
  }, []);

  return (
    <div className="chain-sync-view">
      <div className="chain-sync-scroll" ref={scrollRef} onScroll={onOuterScroll}>
        <div
          className="chain-sync-layout"
          style={{ minHeight: CHAIN_HEADER_HEIGHT + bodyHeight }}
        >
          <div className="chain-sync-left">
            <div
              className="chain-sync-left-header"
              style={{ height: CHAIN_HEADER_HEIGHT }}
            >
              满足节点
            </div>
            {syncRows.map((row) => {
              const typeClass = row.nodeType.toLowerCase();
              const selected = selectedNodeId === row.taskId;
              return (
                <div
                  key={row.taskId}
                  className={`chain-sync-row type-${typeClass} ${selected ? 'is-selected' : ''}`}
                  style={{ height: CHAIN_ROW_HEIGHT }}
                  role={onSelectNode ? 'button' : undefined}
                  tabIndex={onSelectNode ? 0 : undefined}
                  onClick={onSelectNode ? () => onSelectNode(row.taskId) : undefined}
                  onKeyDown={
                    onSelectNode
                      ? (e) => {
                          if (e.key === 'Enter') onSelectNode(row.taskId);
                        }
                      : undefined
                  }
                >
                  <span
                    className="chain-sync-indent"
                    style={{ width: `${0.75 + row.depth * 1.15}rem` }}
                  />
                  <span className={`demand-tree-type ${typeClass}`}>
                    {fulfillmentTreeNodeTypeLabel(row.nodeType)}
                  </span>
                  <span className="chain-sync-label">{row.label}</span>
                  <span className="chain-sync-meta-cell">
                    <span className="demand-tree-date">{row.displayDate}</span>
                    {row.quantity > 0 && (
                      <span className="demand-tree-qty">×{fmtQty(row.quantity)}</span>
                    )}
                  </span>
                </div>
              );
            })}
          </div>

          <div className="chain-sync-right" ref={ganttScrollRef}>
            <FulfillmentGanttPanel
              className="demand-gantt sync-gantt"
              tasks={tasks}
              pegEdges={edges}
              onTasksChange={onTasksChange}
              showTaskList={false}
              hideToolbar
              showArrows={showArrows}
              viewMode={viewMode}
              rowHeight={CHAIN_ROW_HEIGHT}
              headerHeight={CHAIN_HEADER_HEIGHT}
              ganttHeight={bodyHeight}
              onGanttBodyScroll={onGanttScroll}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
