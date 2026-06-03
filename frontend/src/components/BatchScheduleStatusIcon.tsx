import {
  BATCH_SCHEDULE_PHASE_LABEL,
  type BatchSchedulePhase,
} from '../utils/batchSchedulePhase';
import './BatchScheduleStatusIcon.css';

export interface BatchScheduleStatusIconProps {
  phase: BatchSchedulePhase;
  showLabel?: boolean;
  size?: 'sm' | 'md';
}

export function BatchScheduleStatusIcon({
  phase,
  showLabel = false,
  size = 'md',
}: BatchScheduleStatusIconProps) {
  const label = BATCH_SCHEDULE_PHASE_LABEL[phase];
  return (
    <span
      className={`batch-phase-badge size-${size}`}
      title={label}
      aria-label={label}
    >
      <span className={`batch-phase-icon phase-${phase.toLowerCase()}`} aria-hidden />
      {showLabel && <span className="batch-phase-text">{label}</span>}
    </span>
  );
}

export function BatchScheduleStatusLegend() {
  const phases: BatchSchedulePhase[] = [
    'UNPLANNED',
    'PLANNED',
    'RELEASED',
    'EXECUTING',
  ];
  return (
    <div className="batch-phase-legend" aria-label="批次排产状态图例">
      {phases.map((phase) => (
        <BatchScheduleStatusIcon key={phase} phase={phase} showLabel size="sm" />
      ))}
    </div>
  );
}
