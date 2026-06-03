import type { ScheduleConstraintViolation } from '../types/detailSchedulePlanningPreview';
import './DetailSchedulePlanningPreviewPanel.css';

interface ScheduleViolationsPanelProps {
  violations: ScheduleConstraintViolation[] | undefined;
  title?: string;
  maxItems?: number;
  appliedRules?: string[] | null;
  simulationProfileId?: string | null;
}

export function ScheduleViolationsPanel({
  violations,
  title = '约束校验',
  maxItems = 20,
  appliedRules,
  simulationProfileId,
}: ScheduleViolationsPanelProps) {
  const hasDiagnostics = (violations?.length ?? 0) > 0
    || (appliedRules?.length ?? 0) > 0
    || !!simulationProfileId;
  if (!hasDiagnostics) {
    return null;
  }
  if (!violations?.length) {
    return (
      <div className="ds-planning-violations card">
        <h4>{title}</h4>
        {simulationProfileId && (
          <p className="muted-text">推演配置：{simulationProfileId}</p>
        )}
        {appliedRules && appliedRules.length > 0 && (
          <p className="muted-text">已应用规则：{appliedRules.join(', ')}</p>
        )}
      </div>
    );
  }
  const shown = violations.slice(0, maxItems);
  return (
    <div className="ds-planning-violations card">
      <h4>{title}</h4>
      <ul className="ds-violation-list">
        {shown.map((v, i) => (
          <li key={`${v.ruleCode}-${v.operationId}-${i}`} className={`ds-violation level-${v.level.toLowerCase()}`}>
            <span className="ds-violation-level">{v.level}</span>
            <span className="ds-violation-code">{v.ruleCode}</span>
            <span>{v.operationId ?? '—'}</span>
            <span className="ds-violation-msg">{v.message}</span>
          </li>
        ))}
      </ul>
      {violations.length > maxItems && (
        <p className="muted-text">另有 {violations.length - maxItems} 条未显示</p>
      )}
      {simulationProfileId && (
        <p className="muted-text">推演配置：{simulationProfileId}</p>
      )}
      {appliedRules && appliedRules.length > 0 && (
        <p className="muted-text">已应用规则：{appliedRules.join(', ')}</p>
      )}
    </div>
  );
}
