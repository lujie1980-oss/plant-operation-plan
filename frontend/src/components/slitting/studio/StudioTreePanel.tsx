import { useCallback, useEffect, useState } from 'react';
import type { SlittingAssignment, SlittingRollNode } from '../../../types/slitting';
import { slittingNodeLabel, slittingNodeSubtitle, slittingNodeTypeLabel } from '../../../utils/slitting/display';
import { isNodeLocked, collectDescendantNodeIds } from '../../../utils/slitting/studioLock';
import { MASTER_ROLL_DRAG_TYPE, parseMasterRollDrag } from './MasterRollPool';
import { BOM_MATERIAL_DRAG_TYPE, parseBomMaterialDrag } from './bomMaterialDrag';
import { ORDER_DRAG_TYPE, parseOrderDrag } from './OrderPool';

type ContextMenuState = {
  x: number;
  y: number;
  nodeId: string;
} | null;

type Props = {
  nodes: SlittingRollNode[];
  assignments: SlittingAssignment[];
  selectedNodeId: string | null;
  optimizing: boolean;
  allMasters: { rollCode: string }[];
  onSelect: (nodeId: string) => void;
  onMasterDrop: (rollCode: string) => void;
  onBomMaterialDrop: (productCode: string) => void;
  onOrderDropOnRegion: (orderCode: string, regionNodeId: string) => void;
  onOrderDropOnMaster: (orderCode: string, masterNodeId: string) => void;
  onCreateRegion: (nodeId: string) => void;
  onCreateFullRegion: (nodeId: string) => void;
  onResizeRegion: (nodeId: string) => void;
  onDelete: (nodeId: string) => void;
  onToggleLock: (nodeId: string) => void;
  onAutoSlitMaster: (masterNodeId: string) => void;
};

function childrenOf(nodes: SlittingRollNode[], parentId: string | null) {
  return nodes.filter((n) => n.parentNodeId === parentId);
}

export function StudioTreePanel({
  nodes,
  assignments,
  selectedNodeId,
  optimizing,
  allMasters,
  onSelect,
  onMasterDrop,
  onBomMaterialDrop,
  onOrderDropOnRegion,
  onOrderDropOnMaster,
  onCreateRegion,
  onCreateFullRegion,
  onResizeRegion,
  onDelete,
  onToggleLock,
  onAutoSlitMaster,
}: Props) {
  const [menu, setMenu] = useState<ContextMenuState>(null);

  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      const master = e.dataTransfer.getData(MASTER_ROLL_DRAG_TYPE);
      if (master) {
        const p = parseMasterRollDrag(master);
        if (p) onMasterDrop(p.rollCode);
        return;
      }
      const bom = e.dataTransfer.getData(BOM_MATERIAL_DRAG_TYPE);
      if (bom) {
        const p = parseBomMaterialDrag(bom);
        if (p) onBomMaterialDrop(p.productCode);
      }
    },
    [onMasterDrop, onBomMaterialDrop],
  );

  const renderNode = (node: SlittingRollNode, depth: number) => {
    const kids = childrenOf(nodes, node.nodeId);
    const selected = selectedNodeId === node.nodeId;
    const locked = isNodeLocked(node.nodeId, nodes, assignments);
    const canSplit = node.nodeType === 'MASTER' || node.nodeType === 'INTERMEDIATE';
    const canDelete = node.nodeType !== 'MASTER' || nodes.filter((n) => n.nodeType === 'MASTER').length > 1;
    const hasAssignment = assignments.some((a) => a.childNodeId === node.nodeId);
    const canLock =
      node.nodeType === 'MASTER'
        ? assignments.some((a) => collectDescendantNodeIds(nodes, node.nodeId).has(a.childNodeId))
        : hasAssignment;

    return (
      <div key={node.nodeId} className="slitting-studio-tree-node" style={{ paddingLeft: depth * 12 }}>
        <button
          type="button"
          className={[
            'slitting-tree-btn',
            selected && 'active',
            selected && 'is-selected',
            locked && 'is-locked',
          ]
            .filter(Boolean)
            .join(' ')}
          onClick={() => onSelect(node.nodeId)}
          onContextMenu={(e) => {
            e.preventDefault();
            setMenu({ x: e.clientX, y: e.clientY, nodeId: node.nodeId });
          }}
          onDragOver={(e) => {
            if (node.nodeType === 'MASTER' || node.nodeType === 'INTERMEDIATE') {
              e.preventDefault();
            }
          }}
          onDrop={(e) => {
            e.preventDefault();
            e.stopPropagation();
            const raw = e.dataTransfer.getData(ORDER_DRAG_TYPE);
            const p = parseOrderDrag(raw);
            if (!p) return;
            if (node.nodeType === 'MASTER') {
              onOrderDropOnMaster(p.orderCode, node.nodeId);
            } else if (node.nodeType === 'INTERMEDIATE') {
              onOrderDropOnRegion(p.orderCode, node.nodeId);
            }
          }}
        >
          <span className="slitting-tree-btn-label">
            {locked ? '🔒 ' : ''}
            {slittingNodeTypeLabel(node.nodeType)} · {slittingNodeLabel(node)}
          </span>
          <span className="slitting-tree-btn-meta">{slittingNodeSubtitle(node)}</span>
        </button>
        {kids.map((k) => renderNode(k, depth + 1))}
        {menu?.nodeId === node.nodeId ? (
          <ul
            className="slitting-context-menu"
            style={{ top: menu.y, left: menu.x }}
            onClick={(e) => e.stopPropagation()}
          >
            {node.nodeType === 'MASTER' ? (
              <li>
                <button
                  type="button"
                  disabled={optimizing}
                  onClick={() => {
                    setMenu(null);
                    onAutoSlitMaster(node.nodeId);
                  }}
                >
                  自动分切
                </button>
              </li>
            ) : null}
            {canSplit ? (
              <>
                <li>
                  <button type="button" onClick={() => onCreateFullRegion(node.nodeId)}>
                    创建整卷区域（同尺寸）
                  </button>
                </li>
                <li>
                  <button type="button" onClick={() => onCreateRegion(node.nodeId)}>
                    创建区域（一分为二）…
                  </button>
                </li>
              </>
            ) : null}
            {canLock ? (
              <li>
                <button
                  type="button"
                  onClick={() => {
                    setMenu(null);
                    onToggleLock(node.nodeId);
                  }}
                >
                  {locked ? '取消锁定' : '锁定'}
                </button>
              </li>
            ) : null}
            {node.nodeType === 'INTERMEDIATE' ? (
              <li>
                <button type="button" onClick={() => onResizeRegion(node.nodeId)}>
                  调整区域尺寸…
                </button>
              </li>
            ) : null}
            {(canDelete || node.nodeType !== 'MASTER') && (
              <li>
                <button type="button" onClick={() => onDelete(node.nodeId)}>
                  删除
                </button>
              </li>
            )}
          </ul>
        ) : null}
      </div>
    );
  };

  const roots = childrenOf(nodes, null);

  return (
    <section
      className="slitting-studio-panel slitting-studio-panel--tree"
      onDragOver={onDragOver}
      onDrop={onDrop}
    >
      <h3 className="slitting-panel-title">分切树</h3>
      <p className="slitting-panel-hint">
        右键母卷可自动分切；右键节点可锁定（优化时不移动）；拖入母卷、BOM 物料或订单。
      </p>
      {roots.length === 0 ? (
        <p className="slitting-props-empty">将母卷或 BOM 物料拖入此处开始</p>
      ) : (
        roots.map((r) => renderNode(r, 0))
      )}
      {roots.length === 0 && allMasters.length > 0 ? (
        <p className="slitting-panel-hint">可拖入：{allMasters.map((m) => m.rollCode).join('、')}</p>
      ) : null}
    </section>
  );
}
