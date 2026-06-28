import type { RowViolation } from './types';
import './ConstraintViolationCell.css';

function levelClass(level: RowViolation['level']) {
  if (level === 'error') return 'ft-violation--error';
  if (level === 'warn') return 'ft-violation--warn';
  return 'ft-violation--info';
}

function worstLevel(violations: RowViolation[]): RowViolation['level'] {
  if (violations.some((v) => v.level === 'error')) return 'error';
  if (violations.some((v) => v.level === 'warn')) return 'warn';
  return 'info';
}

export function ConstraintViolationCell({ violations }: { violations: RowViolation[] }) {
  if (violations.length === 0) {
    return <span className="ft-violation ft-violation--ok" title="无预警">✓</span>;
  }

  const level = worstLevel(violations);
  const title = violations.map((v) => (v.ruleCode ? `[${v.ruleCode}] ` : '') + v.message).join('\n');

  return (
    <span className={`ft-violation ${levelClass(level)}`} title={title} aria-label={title}>
      <span className="ft-violation-icon" aria-hidden>
        {level === 'error' ? '⛔' : level === 'warn' ? '⚠' : 'ℹ'}
      </span>
      <span className="ft-violation-count">{violations.length}</span>
    </span>
  );
}
