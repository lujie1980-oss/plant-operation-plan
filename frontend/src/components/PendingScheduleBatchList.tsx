import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import {
  BatchScheduleStatusIcon,
  BatchScheduleStatusLegend,
} from './BatchScheduleStatusIcon';
import { FilterableTable } from './table/FilterableTable';
import { DemandOrderContextMenu } from './DemandOrderContextMenu';
import type { ProductionBatchKitting } from '../types/api';
import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';
import type { ProductionTask } from '../types/scheduleSession';
import { resolveBatchSchedulePhase } from '../utils/batchSchedulePhase';
import { BATCH_DRAG_MIME, type BatchDragPayload } from '../utils/scheduleSessionInsert';
import './PendingScheduleBatchList.css';
import './BatchScheduleStatusIcon.css';
import './DemandOrderContextMenu.css';

const KITTING_LABEL: Record<string, string> = {
  KITTED: '齐套',
  SHORT: '缺料',
  UNKNOWN: '未评估',
};

export interface PendingScheduleBatchListProps {
  selectedBatchNo: string | null;
  onSelectBatch: (batch: ProductionBatchKitting | null) => void;
  hasSession?: boolean;
  disabled?: boolean;
  previewOperations?: DetailSchedulePlanningPreviewOperation[];
  previewRefreshKey?: string;
  onScheduleBatch?: (batch: ProductionBatchKitting) => void;
  onPickBatchLine?: (batch: ProductionBatchKitting) => void;
  onCancelBatchPlan?: (batch: ProductionBatchKitting) => void;
  /** UI-NAV-04: deep-link filter by work order */
  workOrderNoFilter?: string | null;
  onWorkOrderFilterMiss?: () => void;
}

export function PendingScheduleBatchList({
  selectedBatchNo,
  onSelectBatch,
  hasSession = false,
  disabled = false,
  previewOperations,
  previewRefreshKey,
  onScheduleBatch,
  onPickBatchLine,
  onCancelBatchPlan,
  workOrderNoFilter = null,
  onWorkOrderFilterMiss,
}: PendingScheduleBatchListProps) {
  const [rows, setRows] = useState<ProductionBatchKitting[]>([]);
  const [productionTasks, setProductionTasks] = useState<ProductionTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [onlySchedulable, setOnlySchedulable] = useState(true);
  const [ctx, setCtx] = useState<{
    x: number;
    y: number;
    row: ProductionBatchKitting;
  } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const kitting = await api.schedulingBatches.listKitting();
      setRows(kitting);
    } catch (e: unknown) {
      setRows([]);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const loadProductionTasks = useCallback(async () => {
    try {
      const tasks = await api.listProductionTasks();
      setProductionTasks(tasks);
    } catch {
      setProductionTasks([]);
    }
  }, []);

  useEffect(() => {
    void loadProductionTasks();
  }, [loadProductionTasks, previewRefreshKey]);

  const batchPhase = useCallback(
    (batchNo: string) =>
      resolveBatchSchedulePhase(batchNo, previewOperations, productionTasks),
    [previewOperations, productionTasks],
  );

  const filtered = useMemo(() => {
    let list = rows;
    if (onlySchedulable) {
      list = list.filter((r) => r.pendingScheduleEligible !== false);
    }
    if (workOrderNoFilter) {
      list = list.filter((r) => r.workOrderNo === workOrderNoFilter);
    }
    return [...list].sort((a, b) => {
      const wo = a.workOrderNo.localeCompare(b.workOrderNo, 'zh-CN');
      if (wo !== 0) return wo;
      return a.batchSeq - b.batchSeq;
    });
  }, [rows, onlySchedulable, workOrderNoFilter]);

  useEffect(() => {
    if (!workOrderNoFilter || loading) return;
    if (filtered.length === 0) {
      onWorkOrderFilterMiss?.();
      return;
    }
    const first = filtered[0];
    if (selectedBatchNo !== first.batchNo) {
      onSelectBatch(first);
    }
  }, [
    filtered,
    loading,
    onSelectBatch,
    onWorkOrderFilterMiss,
    selectedBatchNo,
    workOrderNoFilter,
  ]);

  const toggleEligible = async (batchNo: string, eligible: boolean) => {
    try {
      await api.schedulingBatches.updatePendingScheduleEligible(batchNo, eligible);
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const onDragStart = (row: ProductionBatchKitting, e: React.DragEvent) => {
    if (!hasSession || disabled) {
      e.preventDefault();
      return;
    }
    const payload: BatchDragPayload = { batchNo: row.batchNo };
    e.dataTransfer.setData(BATCH_DRAG_MIME, JSON.stringify(payload));
    e.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="pending-batch-list">
      <div className="pending-batch-list-head">
        <h3 className="panel-title">待排产批次</h3>
        <div className="pending-batch-list-actions">
          <label className="pending-batch-check">
            <input
              type="checkbox"
              checked={onlySchedulable}
              onChange={(e) => setOnlySchedulable(e.target.checked)}
            />
            仅可排
          </label>
          <button type="button" className="btn" disabled={loading} onClick={() => void load()}>
            刷新
          </button>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      <p className="pending-batch-drag-hint muted-text">
        {hasSession
          ? '右键菜单或拖拽批次到下方甘特产线'
          : '创建 Session 后可排产与拖拽'}
      </p>
      <BatchScheduleStatusLegend />
      <div className="pending-batch-table-wrap">
        <FilterableTable
          tableId="pending-schedule-batches"
          rows={filtered}
          rowKey={(row) => row.batchNo}
          loading={loading}
          onRowClick={(row) =>
            onSelectBatch(selectedBatchNo === row.batchNo ? null : row)
          }
          getRowClassName={(row) =>
            [
              row.batchNo === selectedBatchNo ? 'pending-batch-row-selected' : '',
              hasSession && !disabled ? 'pending-batch-row-draggable' : '',
            ]
              .filter(Boolean)
              .join(' ')
          }
          getRowProps={(row) => ({
            draggable: hasSession && !disabled,
            onDragStart: (e: React.DragEvent) => onDragStart(row, e),
            onContextMenu: (e: React.MouseEvent) => {
              e.preventDefault();
              setCtx({ x: e.clientX, y: e.clientY, row });
            },
          })}
          columns={[
            {
              key: 'status',
              header: '',
              width: 28,
              render: (r) => (
                <BatchScheduleStatusIcon phase={batchPhase(r.batchNo)} size="md" />
              ),
            },
            { key: 'batch', header: '批次', render: (r) => r.batchNo },
            { key: 'wo', header: '工单', render: (r) => r.workOrderNo },
            { key: 'product', header: '产品', render: (r) => r.productCode },
            { key: 'qty', header: '数量', render: (r) => r.quantity },
            {
              key: 'kitting',
              header: '齐套',
              render: (r) => (
                <span className={`pending-batch-kitting status-${r.kittingStatus.toLowerCase()}`}>
                  {KITTING_LABEL[r.kittingStatus] ?? r.kittingStatus}
                </span>
              ),
            },
            {
              key: 'eligible',
              header: '可排',
              render: (r) => (
                <select
                  className="input pending-batch-eligible-select"
                  value={r.pendingScheduleEligible ? 'yes' : 'no'}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) =>
                    void toggleEligible(r.batchNo, e.target.value === 'yes')
                  }
                >
                  <option value="yes">是</option>
                  <option value="no">否</option>
                </select>
              ),
            },
          ]}
          emptyText="暂无待排产批次"
        />
      </div>
      {ctx && (
        <DemandOrderContextMenu
          x={ctx.x}
          y={ctx.y}
          onClose={() => setCtx(null)}
          items={[
            {
              id: 'select',
              label: '选中批次',
              onSelect: () => onSelectBatch(ctx.row),
            },
            {
              id: 'schedule',
              label: '单独排该批次',
              disabled: !hasSession || disabled || !onScheduleBatch,
              onSelect: () => onScheduleBatch?.(ctx.row),
            },
            {
              id: 'pickLine',
              label: '选择机台…',
              disabled: !hasSession || disabled || !onPickBatchLine,
              onSelect: () => onPickBatchLine?.(ctx.row),
            },
            {
              id: 'cancel',
              label: '取消计划',
              disabled: !hasSession || disabled || !onCancelBatchPlan,
              onSelect: () => void onCancelBatchPlan?.(ctx.row),
            },
          ]}
        />
      )}
    </div>
  );
}
