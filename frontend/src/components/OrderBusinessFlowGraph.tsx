import type { DemandTrackingFlowStep } from '../types/api';
import './OrderBusinessFlowGraph.css';

interface OrderBusinessFlowGraphProps {
  steps: DemandTrackingFlowStep[];
  compact?: boolean;
}

function stepClass(status: string) {
  if (status === 'done') return 'done';
  if (status === 'active') return 'active';
  if (status === 'risk') return 'risk';
  return 'pending';
}

export function OrderBusinessFlowGraph({ steps, compact = false }: OrderBusinessFlowGraphProps) {
  if (steps.length === 0) {
    return <span className="empty">—</span>;
  }

  return (
    <div className={`biz-flow-graph ${compact ? 'compact' : ''}`.trim()} role="list">
      {steps.map((step, index) => (
        <div key={step.stepId} className="biz-flow-segment" role="listitem">
          <div className={`biz-flow-node ${stepClass(step.status)}`} title={step.detail}>
            <span className="biz-flow-label">{step.label}</span>
            {!compact && <span className="biz-flow-detail">{step.detail}</span>}
          </div>
          {index < steps.length - 1 && <span className="biz-flow-edge" aria-hidden />}
        </div>
      ))}
    </div>
  );
}
