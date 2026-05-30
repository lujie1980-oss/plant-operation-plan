import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import { formatPegEdges } from '../utils/fulfillmentGantt';

interface PeggingChainListProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
}

export function PeggingChainList({ nodes, edges }: PeggingChainListProps) {
  const lines = formatPegEdges(nodes, edges);
  if (lines.length === 0) {
    return <p className="empty">暂无满足关系</p>;
  }

  return (
    <ol className="pegging-chain-list">
      {lines.map((line, i) => (
        <li key={i} className={`pegging-line peg-${line.pegType.toLowerCase()}`}>
          {line.text}
        </li>
      ))}
    </ol>
  );
}
