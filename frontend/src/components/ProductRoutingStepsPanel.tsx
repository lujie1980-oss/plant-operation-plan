import { useEffect, useMemo, useState } from 'react';
import type { ProductResourceMd, ProductionLineMd, ResourceMd } from '../types/masterData';
import { applyColumnFilters } from './table/filterRows';
import { TableHead } from './table/TableHead';
import type { TableHeadColumn } from './table/types';
import { useTableLayout } from './table/useTableLayout';
import './ProductRoutingStepsPanel.css';
import './table/FilterableTable.css';

const ROUTING_HEAD_COLUMNS: TableHeadColumn[] = [
  { key: 'toggle', header: '', width: 28, defaultWidth: 28, filterable: false, resizable: false },
  { key: 'seq', header: '序号', width: 48, defaultWidth: 48, align: 'center' },
  { key: 'operationName', header: '工序名称', width: 100, defaultWidth: 100 },
  { key: 'refLabel', header: '设备组/产线', width: 100, defaultWidth: 100 },
  { key: 'areaId', header: '区域', width: 72, defaultWidth: 72 },
  { key: 'processTimeSeconds', header: 'CT(秒)', width: 72, defaultWidth: 72, align: 'right' },
  { key: 'setupTimeMinutes', header: '换型(分)', width: 72, defaultWidth: 72, align: 'right' },
  { key: 'bomLevel', header: 'A/B料', width: 64, defaultWidth: 64 },
  { key: 'wireMaterial', header: '线材', width: 80, defaultWidth: 80 },
  { key: 'keyMaterial', header: '关键物料', width: 80, defaultWidth: 80 },
  { key: 'standardLabor', header: '制造人力', width: 80, defaultWidth: 80, align: 'right' },
];

interface ProductRoutingStepsPanelProps {
  productCode: string | null;
  productName: string | null;
  steps: ProductResourceMd[];
  resources: ResourceMd[];
  lines: ProductionLineMd[];
}

interface StepBinding {
  line: ProductionLineMd | null;
  resource: ResourceMd | null;
  isLineRef: boolean;
}

interface StepViewRow {
  step: ProductResourceMd;
  seq: number;
  opName: string;
  refLabel: string;
  binding: StepBinding;
  rowKey: string;
}

function resolveStepBinding(
  step: ProductResourceMd,
  resources: ResourceMd[],
  lines: ProductionLineMd[],
): StepBinding {
  const line = lines.find((l) => l.lineId === step.resourceId) ?? null;
  if (line) {
    const resource = resources.find((r) => r.resourceId === line.resourceId) ?? null;
    return { line, resource, isLineRef: true };
  }
  const resource = resources.find((r) => r.resourceId === step.resourceId) ?? null;
  return { line: null, resource, isLineRef: false };
}

function fmtNum(v: number | null | undefined): string {
  if (v == null) return '—';
  return String(v);
}

function stepFilterText(row: StepViewRow, key: string): string {
  const { step, seq, opName, refLabel, binding } = row;
  switch (key) {
    case 'seq':
      return String(seq);
    case 'operationName':
      return opName;
    case 'refLabel':
      return refLabel;
    case 'areaId':
      return binding.line?.areaId ?? binding.resource?.areaId ?? '';
    case 'processTimeSeconds':
      return step.processTimeSeconds != null ? String(step.processTimeSeconds) : '';
    case 'setupTimeMinutes':
      return step.setupTimeMinutes != null ? String(step.setupTimeMinutes) : '';
    case 'bomLevel':
      return step.bomLevel ?? '';
    case 'wireMaterial':
      return step.wireMaterial ?? '';
    case 'keyMaterial':
      return step.keyMaterial ?? '';
    case 'standardLabor':
      return step.standardLabor != null ? String(step.standardLabor) : '';
    default:
      return '';
  }
}

export function ProductRoutingStepsPanel({
  productCode,
  productName,
  steps,
  resources,
  lines,
}: ProductRoutingStepsPanelProps) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const { filters, setFilter, getColumnWidth, onResizeStart } = useTableLayout(
    'product-routing-steps',
    ROUTING_HEAD_COLUMNS,
  );

  useEffect(() => {
    setExpanded(new Set());
  }, [productCode]);

  const viewRows = useMemo((): StepViewRow[] => {
    return [...steps]
      .sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))
      .map((step, index) => {
        const seq = step.sequenceNo ?? index + 1;
        const binding = resolveStepBinding(step, resources, lines);
        const opName = step.operationName?.trim() || `工序 ${seq}`;
        const refLabel = binding.isLineRef
          ? (binding.line?.lineId ?? step.resourceId)
          : (binding.resource?.resourceId ?? step.resourceId);
        return {
          step,
          seq,
          opName,
          refLabel,
          binding,
          rowKey: `${step.productCode}-${seq}-${step.resourceId}`,
        };
      });
  }, [steps, resources, lines]);

  const filteredRows = useMemo(
    () =>
      applyColumnFilters(viewRows, filters, ROUTING_HEAD_COLUMNS, (row, key) =>
        stepFilterText(row, key),
      ),
    [viewRows, filters],
  );

  if (!productCode) {
    return <p className="md-summary-empty">请在 BOM 树中选择物料节点</p>;
  }

  if (viewRows.length === 0) {
    return (
      <div className="routing-steps-panel">
        <p className="routing-steps-head">
          <strong>{productCode}</strong>
          {productName && <span> · {productName}</span>}
        </p>
        <p className="md-summary-empty">该产品暂无工艺路径</p>
      </div>
    );
  }

  const toggle = (key: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  return (
    <div className="routing-steps-panel">
      <p className="routing-steps-head">
        <strong>{productCode}</strong>
        {productName && <span> · {productName}</span>}
        <span className="routing-steps-count">{viewRows.length} 道工序</span>
      </p>
      <div className="routing-tree-wrap">
        <table className="routing-tree-table ft-table data-table">
          <thead>
            <TableHead
              columns={ROUTING_HEAD_COLUMNS}
              filters={filters}
              setFilter={setFilter}
              getColumnWidth={getColumnWidth}
              onResizeStart={onResizeStart}
            />
          </thead>
          <tbody>
            {filteredRows.map((row) => {
              const hasChildren = row.binding.resource != null || row.binding.line != null;
              const isOpen = expanded.has(row.rowKey);
              return (
                <StepRows
                  key={row.rowKey}
                  row={row}
                  hasChildren={hasChildren}
                  isOpen={isOpen}
                  onToggle={() => toggle(row.rowKey)}
                />
              );
            })}
          </tbody>
        </table>
        {filteredRows.length === 0 && <p className="md-summary-empty">无匹配工序</p>}
      </div>
    </div>
  );
}

function StepRows({
  row,
  hasChildren,
  isOpen,
  onToggle,
}: {
  row: StepViewRow;
  hasChildren: boolean;
  isOpen: boolean;
  onToggle: () => void;
}) {
  const { step, seq, opName, refLabel, binding } = row;
  return (
    <>
      <tr className="routing-tr routing-tr-op">
        <td className="routing-td routing-td-toggle">
          {hasChildren ? (
            <button type="button" className="routing-toggle" aria-expanded={isOpen} onClick={onToggle}>
              {isOpen ? '▾' : '▸'}
            </button>
          ) : (
            <span className="routing-toggle spacer" aria-hidden />
          )}
        </td>
        <td className="routing-td routing-td-seq">{seq}</td>
        <td className="routing-td routing-td-op">{opName}</td>
        <td className="routing-td">{refLabel}</td>
        <td className="routing-td">{binding.line?.areaId ?? binding.resource?.areaId ?? '—'}</td>
        <td className="routing-td routing-td-num">{fmtNum(step.processTimeSeconds)}</td>
        <td className="routing-td routing-td-num">{fmtNum(step.setupTimeMinutes)}</td>
        <td className="routing-td">{step.bomLevel ?? '—'}</td>
        <td className="routing-td">{step.wireMaterial ?? '—'}</td>
        <td className="routing-td">{step.keyMaterial ?? '—'}</td>
        <td className="routing-td routing-td-num">{fmtNum(step.standardLabor)}</td>
      </tr>
      {hasChildren && isOpen && binding.resource && (
        <tr className="routing-tr routing-tr-child routing-tr-resource">
          <td className="routing-td" colSpan={2} />
          <td className="routing-td routing-td-child-label" colSpan={2}>
            生产资源
          </td>
          <td className="routing-td">{binding.resource.areaId}</td>
          <td className="routing-td" colSpan={2}>
            组 {binding.resource.resourceGroup ?? '—'}
            {binding.resource.bottleneck ? ' · 瓶颈' : ''}
          </td>
          <td className="routing-td" colSpan={2}>
            产能 {binding.resource.runRatePerHour}/h
          </td>
          <td className="routing-td routing-td-id">{binding.resource.resourceId}</td>
        </tr>
      )}
      {hasChildren && isOpen && binding.line && (
        <tr className="routing-tr routing-tr-child routing-tr-line">
          <td className="routing-td" colSpan={2} />
          <td className="routing-td routing-td-child-label" colSpan={2}>
            产线
          </td>
          <td className="routing-td">{binding.line.areaId}</td>
          <td className="routing-td" colSpan={2}>
            最小人数 {binding.line.lineMinHeadcount}
          </td>
          <td className="routing-td" colSpan={2}>
            每班产能 {binding.line.lineCapacityPerShift} 分钟
          </td>
          <td className="routing-td routing-td-id">
            {binding.line.lineId} → {binding.line.resourceId}
          </td>
        </tr>
      )}
      {hasChildren && isOpen && !binding.resource && !binding.line && (
        <tr className="routing-tr routing-tr-child">
          <td className="routing-td" colSpan={11}>
            未匹配到资源/产线主数据（引用 ID：{step.resourceId}）
          </td>
        </tr>
      )}
    </>
  );
}
