import type { SlittingRollNode } from '../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle } from '../../utils/slitting/display';

type Props = {
  nodes: SlittingRollNode[];
  hoveredNodeId: string | null;
  onHover: (nodeId: string | null) => void;
};

export function UnplacedPoolPanel({ nodes, hoveredNodeId, onHover }: Props) {
  return (
    <aside className="slitting-panel slitting-pool-panel">
      <h3 className="slitting-panel-title">未放置</h3>
      <p className="slitting-panel-hint">当前母卷/中间卷下尚无排样位置的子卷；Auto-Nest 将尝试填入空白区。</p>
      {nodes.length === 0 ? (
        <p className="slitting-pool-empty">本层已全部放置</p>
      ) : (
        <ul className="slitting-pool-list">
          {nodes.map((n) => (
            <li
              key={n.nodeId}
              className={hoveredNodeId === n.nodeId ? 'slitting-pool-item is-hovered' : 'slitting-pool-item'}
              onMouseEnter={() => onHover(n.nodeId)}
              onMouseLeave={() => onHover(null)}
            >
              <span className="slitting-pool-item-label">{slittingNodeLabel(n)}</span>
              <span className="slitting-pool-item-meta">{slittingNodeSubtitle(n)}</span>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
