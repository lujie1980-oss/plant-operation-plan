import { useCallback, useState } from 'react';
import type { FulfillmentChainNode, FulfillmentPegEdge } from '../types/api';
import { FulfillmentMaterialPanel } from './FulfillmentMaterialPanel';
import './FulfillmentMaterialDrawer.css';

const STORAGE_KEY = 'demand-material-drawer-open';

function readOpen(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

interface FulfillmentMaterialDrawerProps {
  nodes: FulfillmentChainNode[];
  edges: FulfillmentPegEdge[];
  selectedTaskId: string | null;
  children: React.ReactNode;
}

export function FulfillmentMaterialDrawer({
  nodes,
  edges,
  selectedTaskId,
  children,
}: FulfillmentMaterialDrawerProps) {
  const [open, setOpen] = useState(readOpen);

  const toggle = useCallback(() => {
    setOpen((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);

  return (
    <div className={`fulfillment-material-drawer-host ${open ? 'is-open' : ''}`.trim()}>
      <div className="fulfillment-material-drawer-main">{children}</div>
      <button
        type="button"
        className="fulfillment-material-drawer-tab"
        onClick={toggle}
        aria-expanded={open}
        title={open ? '收起物料面板' : '展开物料需求'}
      >
        {open ? '›' : '‹'}
        <span className="fulfillment-material-drawer-tab-label">物料</span>
      </button>
      <aside className="fulfillment-material-drawer-panel" aria-hidden={!open}>
        <FulfillmentMaterialPanel nodes={nodes} edges={edges} selectedTaskId={selectedTaskId} />
      </aside>
    </div>
  );
}
