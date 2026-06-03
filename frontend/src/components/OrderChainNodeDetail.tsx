import type { OrderPlanningChainNode } from '../types/orderPlanningChain';
import { PlanningSignalBadge } from './PlanningSignalBadge';
import './OrderChainNodeDetail.css';

export function OrderChainNodeDetail({ node }: { node: OrderPlanningChainNode | null }) {
  if (!node) {
    return (
      <div className="opchain-detail opchain-detail-empty">
        <p>选择链条节点查看推演信号</p>
      </div>
    );
  }

  return (
    <div className="opchain-detail">
      <h4>{node.label}</h4>
      <dl className="opchain-detail-meta">
        <div>
          <dt>类型</dt>
          <dd>{node.nodeType}</dd>
        </div>
        <div>
          <dt>推演层</dt>
          <dd>{node.planningLayer}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{node.status}</dd>
        </div>
        <div>
          <dt>时间窗</dt>
          <dd>
            {node.windowStart ?? '—'} → {node.windowEnd ?? '—'}
          </dd>
        </div>
      </dl>
      {node.planningSignals.length > 0 && (
        <div className="opchain-signals">
          <h5>推演信号</h5>
          <ul>
            {node.planningSignals.map((s, i) => (
              <li key={`${s.reasonCode}-${s.entityId}-${i}`}>
                <PlanningSignalBadge signal={s} />
                <span className="opchain-signal-msg">{s.message}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {node.operations.length > 0 && (
        <div className="opchain-ops">
          <h5>工序 ({node.operations.length})</h5>
          <ul>
            {node.operations.map((op) => (
              <li key={op.operationId}>
                #{op.sequenceNo} {op.operationName} · {op.resourceId ?? '—'}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
