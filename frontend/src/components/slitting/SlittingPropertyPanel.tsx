import type { SlittingAssignment, SlittingRollNode } from '../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle } from '../../utils/slitting/display';

type Props = {
  assignment: SlittingAssignment | null;
  childNode: SlittingRollNode | null;
  sessionActive: boolean;
  onRotate: () => void;
  onTogglePin: () => void;
};

export function SlittingPropertyPanel({
  assignment,
  childNode,
  sessionActive,
  onRotate,
  onTogglePin,
}: Props) {
  if (!assignment || !childNode) {
    return (
      <aside className="slitting-panel slitting-props-panel">
        <h3 className="slitting-panel-title">属性</h3>
        <p className="slitting-props-empty">在画板点击块，或在左侧树悬停联动高亮。</p>
        <p className="slitting-panel-hint">
          快捷键 <kbd>R</kbd> 旋转当前选中块
        </p>
      </aside>
    );
  }

  return (
    <aside className="slitting-panel slitting-props-panel">
      <h3 className="slitting-panel-title">属性</h3>
      <dl className="slitting-props-dl">
        <div>
          <dt>块</dt>
          <dd>
            <strong>{slittingNodeLabel(childNode)}</strong>
            <span className="slitting-props-meta">{slittingNodeSubtitle(childNode)}</span>
          </dd>
        </div>
        <div>
          <dt>位置 (mm)</dt>
          <dd className="slitting-props-nums">
            X {assignment.posXMm.toFixed(1)} · Y {assignment.posYMm.toFixed(1)}
          </dd>
        </div>
        <div>
          <dt>旋转</dt>
          <dd>{assignment.rotated ? '90°' : '0°'}</dd>
        </div>
        {sessionActive ? (
          <div>
            <dt>锁定</dt>
            <dd>{assignment.pinned ? '已锁定（局部重算不移动）' : '未锁定'}</dd>
          </div>
        ) : null}
      </dl>
      <div className="slitting-props-actions">
        <button type="button" className="btn" onClick={onRotate}>
          旋转 (R)
        </button>
        {sessionActive ? (
          <button type="button" className="btn" onClick={onTogglePin}>
            {assignment.pinned ? '取消锁定' : '锁定位置'}
          </button>
        ) : null}
      </div>
    </aside>
  );
}
