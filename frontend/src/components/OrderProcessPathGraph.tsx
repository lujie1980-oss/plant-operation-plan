import type { DemandTrackingProcessEdge, DemandTrackingProcessNode } from '../types/api';
import './OrderProcessPathGraph.css';

interface OrderProcessPathGraphProps {
  nodes: DemandTrackingProcessNode[];
  edges: DemandTrackingProcessEdge[];
}

function statusClass(planStatus: string) {
  if (planStatus === 'COMPLETED') return 'completed';
  if (planStatus === 'PLANNED') return 'planned';
  return 'unplanned';
}

function typeLabel(nodeType: string) {
  if (nodeType === 'RAW_MATERIAL') return '原料';
  if (nodeType === 'OPERATION') return '工序';
  if (nodeType === 'ORDER') return '订单';
  return nodeType;
}

function fmtTs(ts: string | null | undefined): string {
  if (!ts) return '—';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function OrderProcessPathGraph({ nodes, edges }: OrderProcessPathGraphProps) {
  if (nodes.length === 0) {
    return <p className="empty">暂无工艺路径数据</p>;
  }

  const edgeSet = new Set(edges.map((e) => `${e.fromNodeId}->${e.toNodeId}`));

  return (
    <div className="process-path-graph">
      <div className="process-path-legend">
        <span><i className="dot unplanned" /> 未计划</span>
        <span><i className="dot planned" /> 已计划</span>
        <span><i className="dot completed" /> 已完成</span>
      </div>
      <div className="process-path-scroll">
        <div className="process-path-track">
          {nodes.map((node, index) => {
            const next = nodes[index + 1];
            const hasEdge = next && edgeSet.has(`${node.nodeId}->${next.nodeId}`);
            return (
              <div key={node.nodeId} className="process-path-segment">
                <div className={`process-path-node ${statusClass(node.planStatus)}`}>
                  <span className="process-path-type">{typeLabel(node.nodeType)}</span>
                  <span className="process-path-label">{node.label}</span>
                  <div className="process-path-times">
                    <div>
                      <small>计划</small>
                      <span>{fmtTs(node.plannedStart)} → {fmtTs(node.plannedEnd)}</span>
                    </div>
                    <div>
                      <small>生产</small>
                      <span>
                        {node.productionStart || node.productionEnd
                          ? `${fmtTs(node.productionStart)} → ${fmtTs(node.productionEnd)}`
                          : '—'}
                      </span>
                    </div>
                  </div>
                </div>
                {hasEdge && <span className="process-path-edge" aria-hidden />}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
