import { useState } from 'react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import { PeggingChainList } from './PeggingChainList';
import './CollapsiblePeggingSidebar.css';

interface CollapsiblePeggingSidebarProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
}

export function CollapsiblePeggingSidebar({ nodes, edges }: CollapsiblePeggingSidebarProps) {
  const [open, setOpen] = useState(false);

  return (
    <aside className={`pegging-sidebar ${open ? 'open' : 'collapsed'}`}>
      <button
        type="button"
        className="pegging-sidebar-toggle"
        onClick={() => setOpen((v) => !v)}
        title={open ? '收起满足关系' : '展开满足关系'}
        aria-expanded={open}
      >
        {open ? '›' : '‹'}
        <span className="toggle-label">满足关系</span>
      </button>
      {open && (
        <div className="pegging-sidebar-body">
          <h4>谁满足谁</h4>
          <PeggingChainList nodes={nodes} edges={edges} />
        </div>
      )}
    </aside>
  );
}
