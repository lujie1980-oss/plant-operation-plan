import { VIOLATION_HEADER_ARIA } from './types';
import './ConstraintViolationCell.css';
import './FilterableTable.css';

/** 统一列表第 2 列表头：仅显示预警图标 */
export function ViolationColumnHeader() {
  return (
    <span
      className="ft-th-violation-icon ft-violation ft-violation--warn"
      title={VIOLATION_HEADER_ARIA}
      aria-label={VIOLATION_HEADER_ARIA}
    >
      <span className="ft-violation-icon" aria-hidden>
        ⚠
      </span>
    </span>
  );
}
