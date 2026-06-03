import { reasonLabel } from '../utils/planningDiagnosticsModel';
import type { PlanningSignal } from '../types/orderPlanningChain';
import './PlanningSignalBadge.css';

export function PlanningSignalBadge({ signal }: { signal: PlanningSignal }) {
  const cls =
    signal.severity === 'SKIP'
      ? 'opsig-danger'
      : signal.severity === 'WARN'
        ? 'opsig-warn'
        : 'opsig-info';
  return (
    <span className={`opsig-badge ${cls}`} title={signal.message}>
      {reasonLabel(signal.reasonCode)}
    </span>
  );
}
