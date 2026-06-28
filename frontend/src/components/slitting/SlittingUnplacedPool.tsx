import type { SlittingRollNode } from '../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle, slittingNodeTypeLabel } from '../../utils/slitting/display';

type Props = {
  nodes: SlittingRollNode[];
  hoveredNodeId: string | null;
  onHover: (nodeId: string | null) => void;
};

export function SlittingUnplacedPool({ nodes, hoveredNodeId, onHover }: Props) {
  return (
    <aside className="slitting-panel slitting-pool-panel">
      <h3 className="slitting-panel-title">未放置</h3>
      <p className="slitting-panel-hint">当前父卷下尚无坐标的块；Auto-Nest 可尝试填入空白区。</p>
      {nodes.length === 0 ? (
        <p className="slitting-props-empty">本层已全部放置。</p>
      ) : (
        <ul className="slitting-pool-list">
          {nodes.map((n) => (
            <li
              key={n.nodeId}
              className={hoveredNodeId === n.nodeId ? 'slitting-pool-item is-hovered' : 'slitting-pool-item'}
              onMouseEnter={() => onHover(n.nodeId)}
              onMouseLeave={() => onHover(null)}
            >
              <span className="slitting-pool-type">{slittingNodeTypeLabel(n.nodeType)}</span>
              <span className="slitting-pool-name">{slittingNodeLabel(n)}</span>
              <span className="slitting-pool-size">{slittingNodeSubtitle(n)}</span>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
