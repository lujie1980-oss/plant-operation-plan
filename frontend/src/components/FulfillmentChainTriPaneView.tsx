import { useCallback, useMemo, useRef, useState } from 'react';
import { ViewMode } from 'gantt-task-react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import {
  buildSupplyOrderChainTree,
  flattenSupplyOrderTreeRows,
  formatSupplyOrderTreeDate,
  fulfillmentTreeNodeTypeLabel,
} from '../utils/fulfillmentChainTree';
import { ganttPegEdges, supplyOrderRowsToGanttTasks } from '../utils/fulfillmentGantt';
import { HorizontalResizeSplit } from './HorizontalResizeSplit';
import { FulfillmentGanttPanel } from './FulfillmentGanttPanel';
import { FulfillmentMaterialPanel } from './FulfillmentMaterialPanel';
import './FulfillmentChainTriPaneView.css';

export const SUPPLY_ORDER_ROW_HEIGHT = 36;
export const SUPPLY_ORDER_HEADER_HEIGHT = 50;

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

interface FulfillmentChainTriPaneViewProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  selectedNodeId?: string | null;
  onSelectNode?: (nodeId: string) => void;
  showArrows: boolean;
  viewMode: ViewMode;
}

export function FulfillmentChainTriPaneView({
  nodes,
  edges,
  selectedNodeId,
  onSelectNode,
  showArrows,
  viewMode,
}: FulfillmentChainTriPaneViewProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const ganttScrollRef = useRef<HTMLDivElement>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const tree = useMemo(() => buildSupplyOrderChainTree(nodes, edges), [nodes, edges]);

  const treeRows = useMemo(
    () => flattenSupplyOrderTreeRows(tree, nodes, collapsed),
    [tree, nodes, collapsed],
  );

  const ganttTasks = useMemo(() => supplyOrderRowsToGanttTasks(treeRows), [treeRows]);

  const pegEdges = useMemo(() => ganttPegEdges(edges, nodes), [edges, nodes]);

  const bodyHeight = treeRows.length * SUPPLY_ORDER_ROW_HEIGHT;

  const toggleCollapse = useCallback((nodeId: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  }, []);

  const expandAll = useCallback(() => setCollapsed(new Set()), []);
  const collapseAll = useCallback(() => {
    const ids = new Set<string>();
    function walk(items: typeof tree) {
      for (const n of items) {
        if (n.children.length > 0) {
          ids.add(n.nodeId);
          walk(n.children);
        }
      }
    }
    walk(tree);
    setCollapsed(ids);
  }, [tree]);

  const onTreeScroll = useCallback(() => {
    const tree = scrollRef.current;
    const gantt = ganttScrollRef.current;
    if (!tree || !gantt) return;
    if (Math.abs(gantt.scrollTop - tree.scrollTop) > 1) {
      gantt.scrollTop = tree.scrollTop;
    }
  }, []);

  const onGanttScroll = useCallback(() => {
    const tree = scrollRef.current;
    const gantt = ganttScrollRef.current;
    if (!tree || !gantt) return;
    if (Math.abs(tree.scrollTop - gantt.scrollTop) > 1) {
      tree.scrollTop = gantt.scrollTop;
    }
  }, []);

  const chainBody = (
    <div className="chain-tri-pane-center">
      <div className="chain-tri-toolbar">
        <span className="chain-tri-toolbar-label">供应订单层级 · 甘特对齐</span>
        <div className="chain-tri-toolbar-actions">
          <button type="button" className="btn btn-sm" onClick={expandAll}>
            全部展开
          </button>
          <button type="button" className="btn btn-sm" onClick={collapseAll}>
            全部收拢
          </button>
        </div>
      </div>
      <HorizontalResizeSplit
        className="chain-tri-tree-gantt-split"
        storageKey="demand-chain-tree-gantt"
        minLeftRatio={0.28}
        maxLeftRatio={0.55}
        defaultLeftRatio={0.38}
        left={
          <div className="chain-tri-sync-scroll" ref={scrollRef} onScroll={onTreeScroll}>
            <table className="chain-tri-tree-table">
              <thead>
                <tr style={{ height: SUPPLY_ORDER_HEADER_HEIGHT }}>
                  <th className="col-toggle" />
                  <th className="col-type">类型</th>
                  <th className="col-label">供应订单</th>
                  <th className="col-product">产品</th>
                  <th className="col-qty">数量</th>
                  <th className="col-start">开始</th>
                  <th className="col-end">结束</th>
                </tr>
              </thead>
              <tbody
                style={{
                  minHeight: bodyHeight,
                }}
              >
                {treeRows.map((row) => {
                  const typeClass = row.nodeType.toLowerCase();
                  const selected = selectedNodeId === row.nodeId;
                  return (
                    <tr
                      key={row.nodeId}
                      className={`chain-tri-tree-row type-${typeClass} ${selected ? 'is-selected' : ''}`}
                      style={{ height: SUPPLY_ORDER_ROW_HEIGHT }}
                      onClick={onSelectNode ? () => onSelectNode(row.nodeId) : undefined}
                    >
                      <td className="col-toggle">
                        {row.hasChildren ? (
                          <button
                            type="button"
                            className="chain-tri-toggle"
                            aria-expanded={!collapsed.has(row.nodeId)}
                            aria-label={collapsed.has(row.nodeId) ? '展开' : '收拢'}
                            onClick={(e) => {
                              e.stopPropagation();
                              toggleCollapse(row.nodeId);
                            }}
                          >
                            {collapsed.has(row.nodeId) ? '▸' : '▾'}
                          </button>
                        ) : (
                          <span className="chain-tri-toggle-spacer" />
                        )}
                      </td>
                      <td className="col-type">
                        <span className={`demand-tree-type ${typeClass}`}>
                          {fulfillmentTreeNodeTypeLabel(row.nodeType)}
                        </span>
                      </td>
                      <td className="col-label">
                        <span
                          className="chain-tri-indent"
                          style={{ paddingLeft: `${row.depth}rem` }}
                        />
                        <span className="chain-tri-label" title={row.label}>
                          {row.label}
                        </span>
                      </td>
                      <td className="col-product mono">{row.productCode || '—'}</td>
                      <td className="col-qty">{row.quantity > 0 ? fmtQty(row.quantity) : '—'}</td>
                      <td className="col-start mono">
                        {row.startTs ? formatSupplyOrderTreeDate(row.startTs) : '—'}
                      </td>
                      <td className="col-end mono">
                        {row.endTs ? formatSupplyOrderTreeDate(row.endTs) : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        }
        right={
          <div
            className="chain-tri-sync-scroll chain-tri-gantt-scroll"
            ref={ganttScrollRef}
            onScroll={onGanttScroll}
          >
            <div
              className="chain-tri-gantt-inner"
              style={{ minHeight: SUPPLY_ORDER_HEADER_HEIGHT + bodyHeight }}
            >
              <FulfillmentGanttPanel
                className="demand-gantt sync-gantt chain-tri-gantt-panel"
                tasks={ganttTasks}
                pegEdges={pegEdges}
                showTaskList={false}
                hideToolbar
                showArrows={showArrows}
                viewMode={viewMode}
                rowHeight={SUPPLY_ORDER_ROW_HEIGHT}
                headerHeight={SUPPLY_ORDER_HEADER_HEIGHT}
                ganttHeight={bodyHeight}
                onGanttBodyScroll={onGanttScroll}
                onSelectTask={
                  onSelectNode
                    ? (task, isSelected) => {
                        if (isSelected && task.id !== 'empty') onSelectNode(task.id);
                      }
                    : undefined
                }
              />
            </div>
          </div>
        }
      />
    </div>
  );

  return (
    <div className="chain-tri-pane">
      <HorizontalResizeSplit
        className="chain-tri-main-split"
        storageKey="demand-chain-tri-material"
        minLeftRatio={0.48}
        maxLeftRatio={0.82}
        defaultLeftRatio={0.68}
        left={chainBody}
        right={
          <FulfillmentMaterialPanel
            nodes={nodes}
            edges={edges}
            selectedTaskId={selectedNodeId ?? null}
          />
        }
      />
    </div>
  );
}
