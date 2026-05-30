import { useEffect, useState } from 'react';
import type { BomTreeNode } from '../utils/bomTree';
import { TableHead } from './table/TableHead';
import type { TableHeadColumn } from './table/types';
import { useTableLayout } from './table/useTableLayout';
import './BomHierarchyTree.css';
import './table/FilterableTable.css';

const BOM_HEAD_COLUMNS: TableHeadColumn[] = [
  { key: 'toggle', header: '', width: 28, defaultWidth: 28, filterable: false, resizable: false },
  { key: 'level', header: '层级', width: 56, defaultWidth: 56 },
  { key: 'productCode', header: '产品代码', width: 120, defaultWidth: 120 },
  { key: 'productName', header: '产品名称', width: 120, defaultWidth: 120 },
  { key: 'materialType', header: '物料类型', width: 90, defaultWidth: 90 },
  { key: 'uomCode', header: '单位', width: 56, defaultWidth: 56 },
  { key: 'qty', header: '用量', width: 64, defaultWidth: 64, align: 'right' },
  { key: 'isCritical', header: '关键件', width: 64, defaultWidth: 64, align: 'center' },
  { key: 'scrapRate', header: '损耗率', width: 72, defaultWidth: 72, align: 'right' },
  { key: 'bomId', header: 'BOM ID', width: 90, defaultWidth: 90 },
  { key: 'bomVersion', header: '版本', width: 56, defaultWidth: 56 },
  { key: 'componentEffectiveFrom', header: '组件生效', width: 96, defaultWidth: 96 },
  { key: 'componentEffectiveTo', header: '组件失效', width: 96, defaultWidth: 96 },
];

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return String(n);
  return n.toLocaleString(undefined, { maximumFractionDigits: 4 });
}

function fmtDate(v: string | null): string {
  return v?.trim() ? v : '—';
}

function bomLevelLabel(depth: number): string {
  if (depth === 0) return '成品';
  if (depth === 1) return '一阶';
  if (depth === 2) return '二阶';
  return `${depth}阶`;
}

function nodeFilterText(node: BomTreeNode, depth: number, key: string): string {
  switch (key) {
    case 'level':
      return bomLevelLabel(depth);
    case 'productCode':
      return node.productCode;
    case 'productName':
      return node.productName ?? '';
    case 'materialType':
      return node.materialType ?? '';
    case 'uomCode':
      return node.uomCode ?? '';
    case 'qty':
      return depth > 0 ? fmtQty(node.qty) : '';
    case 'isCritical':
      return depth > 0 ? (node.isCritical ? '是' : '否') : '';
    case 'scrapRate':
      return node.scrapRate != null && node.scrapRate > 0
        ? `${(node.scrapRate * 100).toFixed(1)}%`
        : '';
    case 'bomId':
      return node.bomId ?? '';
    case 'bomVersion':
      return node.bomVersion ?? '';
    case 'componentEffectiveFrom':
      return fmtDate(node.componentEffectiveFrom);
    case 'componentEffectiveTo':
      return fmtDate(node.componentEffectiveTo);
    default:
      return '';
  }
}

function nodeMatchesFilters(
  node: BomTreeNode,
  depth: number,
  filters: Record<string, string>,
): boolean {
  const active = Object.entries(filters).filter(([, value]) => value.trim().length > 0);
  if (active.length === 0) return true;
  return active.every(([key, query]) =>
    nodeFilterText(node, depth, key).toLowerCase().includes(query.trim().toLowerCase()),
  );
}

function subtreeVisible(node: BomTreeNode, depth: number, filters: Record<string, string>): boolean {
  if (nodeMatchesFilters(node, depth, filters)) return true;
  return node.children.some((child) => subtreeVisible(child, depth + 1, filters));
}

interface BomHierarchyTreeProps {
  root: BomTreeNode | null;
  selectedProductCode: string | null;
  onSelect: (productCode: string) => void;
}

function BomTreeTableRow({
  node,
  depth,
  nodePath,
  selectedProductCode,
  collapsed,
  filters,
  onToggleCollapse,
  onSelect,
}: {
  node: BomTreeNode;
  depth: number;
  nodePath: string;
  selectedProductCode: string | null;
  collapsed: Set<string>;
  filters: Record<string, string>;
  onToggleCollapse: (path: string) => void;
  onSelect: (productCode: string) => void;
}) {
  if (!subtreeVisible(node, depth, filters)) {
    return null;
  }

  const selected = selectedProductCode === node.productCode;
  const hasChildren = node.children.length > 0;
  const isCollapsed = collapsed.has(nodePath);

  return (
    <>
      <tr className={`bom-tree-tr ${selected ? 'is-selected' : ''}`} onClick={() => onSelect(node.productCode)}>
        <td className="bom-tree-td bom-tree-td-toggle">
          {hasChildren ? (
            <button
              type="button"
              className="bom-tree-toggle"
              aria-expanded={!isCollapsed}
              aria-label={isCollapsed ? '展开下级' : '折叠下级'}
              onClick={(e) => {
                e.stopPropagation();
                onToggleCollapse(nodePath);
              }}
            >
              {isCollapsed ? '▸' : '▾'}
            </button>
          ) : (
            <span className="bom-tree-toggle spacer" aria-hidden />
          )}
        </td>
        <td className="bom-tree-td">
          <span className="bom-tree-level">{bomLevelLabel(depth)}</span>
        </td>
        <td className="bom-tree-td bom-tree-td-code">{node.productCode}</td>
        <td className="bom-tree-td">{node.productName ?? '—'}</td>
        <td className="bom-tree-td">{node.materialType ?? '—'}</td>
        <td className="bom-tree-td">{node.uomCode ?? '—'}</td>
        <td className="bom-tree-td bom-tree-td-num">{depth > 0 ? fmtQty(node.qty) : '—'}</td>
        <td className="bom-tree-td bom-tree-td-center">
          {depth > 0 ? (node.isCritical ? '是' : '否') : '—'}
        </td>
        <td className="bom-tree-td bom-tree-td-num">
          {node.scrapRate != null && node.scrapRate > 0
            ? `${(node.scrapRate * 100).toFixed(1)}%`
            : '—'}
        </td>
        <td className="bom-tree-td">{node.bomId ?? '—'}</td>
        <td className="bom-tree-td">{node.bomVersion ?? '—'}</td>
        <td className="bom-tree-td bom-tree-td-date">{fmtDate(node.componentEffectiveFrom)}</td>
        <td className="bom-tree-td bom-tree-td-date">{fmtDate(node.componentEffectiveTo)}</td>
      </tr>
      {hasChildren &&
        !isCollapsed &&
        node.children.map((child) => (
          <BomTreeTableRow
            key={`${nodePath}/${child.productCode}`}
            node={child}
            depth={depth + 1}
            nodePath={`${nodePath}/${child.productCode}`}
            selectedProductCode={selectedProductCode}
            collapsed={collapsed}
            filters={filters}
            onToggleCollapse={onToggleCollapse}
            onSelect={onSelect}
          />
        ))}
    </>
  );
}

export function BomHierarchyTree({ root, selectedProductCode, onSelect }: BomHierarchyTreeProps) {
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const { filters, setFilter, getColumnWidth, onResizeStart } = useTableLayout(
    'bom-hierarchy-tree',
    BOM_HEAD_COLUMNS,
  );

  useEffect(() => {
    setCollapsed(new Set());
  }, [root?.productCode]);

  const onToggleCollapse = (path: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  };

  if (!root) {
    return <p className="md-summary-empty">暂无 BOM 结构</p>;
  }

  return (
    <div className="bom-tree-panel">
      <table className="bom-tree-table ft-table data-table">
        <thead>
          <TableHead
            columns={BOM_HEAD_COLUMNS}
            filters={filters}
            setFilter={setFilter}
            getColumnWidth={getColumnWidth}
            onResizeStart={onResizeStart}
          />
        </thead>
        <tbody>
          <BomTreeTableRow
            node={root}
            depth={0}
            nodePath={root.productCode}
            selectedProductCode={selectedProductCode}
            collapsed={collapsed}
            filters={filters}
            onToggleCollapse={onToggleCollapse}
            onSelect={onSelect}
          />
        </tbody>
      </table>
    </div>
  );
}