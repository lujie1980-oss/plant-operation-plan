import { useMemo, useState } from 'react';
import { FilterableTable } from './table/FilterableTable';
import { DemandOrderContextMenu } from './DemandOrderContextMenu';
import type { DetailSchedulePlanningPreview } from '../types/detailSchedulePlanningPreview';
import type { DetailSchedulePlanningPreviewOperation } from '../types/detailSchedulePlanningPreview';
import './BatchOperationListPanel.css';
import '../components/DemandOrderContextMenu.css';

export interface BatchOperationListPanelProps {
  selectedBatchNo: string | null;
  preview: DetailSchedulePlanningPreview | null;
  hasSession: boolean;
  selectedOperationId: string | null;
  onSelectOperation: (op: DetailSchedulePlanningPreviewOperation | null) => void;
  onScheduleEarliest: (op: DetailSchedulePlanningPreviewOperation) => void;
  onAssignLine: (op: DetailSchedulePlanningPreviewOperation) => void;
  disabled?: boolean;
}

export function BatchOperationListPanel({
  selectedBatchNo,
  preview,
  hasSession,
  selectedOperationId,
  onSelectOperation,
  onScheduleEarliest,
  onAssignLine,
  disabled,
}: BatchOperationListPanelProps) {
  const [ctx, setCtx] = useState<{
    x: number;
    y: number;
    op: DetailSchedulePlanningPreviewOperation;
  } | null>(null);

  const operations = useMemo(() => {
    if (!preview?.operations || !selectedBatchNo) return [];
    return preview.operations
      .filter((op) => op.batchNo === selectedBatchNo)
      .sort((a, b) => a.operationSeq - b.operationSeq);
  }, [preview, selectedBatchNo]);

  return (
    <div className="batch-op-list">
      <div className="batch-op-list-head">
        <h3 className="panel-title">批次工序</h3>
        {selectedBatchNo && (
          <span className="muted-text batch-op-batch-no">{selectedBatchNo}</span>
        )}
      </div>
      {!selectedBatchNo && (
        <p className="empty batch-op-empty">请先在左侧选择批次</p>
      )}
      {selectedBatchNo && !hasSession && (
        <p className="empty batch-op-empty">请先创建 Session 后查看工序并排产</p>
      )}
      {selectedBatchNo && hasSession && (
        <div className="batch-op-table-wrap">
          <FilterableTable
            tableId="batch-operation-steps"
            rows={operations}
            rowKey={(row) => row.operationId}
            emptyText="该批次暂无工序"
            getRowClassName={(row) =>
              row.operationId === selectedOperationId ? 'batch-op-row-selected' : ''
            }
            getRowProps={(row) => ({
              onContextMenu: (e) => {
                e.preventDefault();
                setCtx({ x: e.clientX, y: e.clientY, op: row });
              },
              onDoubleClick: () => {
                if (!disabled) onScheduleEarliest(row);
              },
            })}
            onRowClick={(row) =>
              onSelectOperation(
                selectedOperationId === row.operationId ? null : row,
              )
            }
            columns={[
              {
                key: 'seq',
                header: '序',
                width: 48,
                render: (r) => r.operationSeq,
              },
              {
                key: 'name',
                header: '工序',
                render: (r) => r.operationName || r.operationId,
              },
              {
                key: 'scheduled',
                header: '已排',
                width: 56,
                render: (r) => (r.scheduled ? '是' : '否'),
              },
              {
                key: 'line',
                header: '产线',
                width: 72,
                render: (r) => r.lineId ?? '—',
              },
              {
                key: 'action',
                header: '',
                width: 132,
                render: (r) => (
                  <div className="batch-op-actions">
                    <button
                      type="button"
                      className="btn batch-op-schedule-btn"
                      disabled={disabled}
                      onClick={(e) => {
                        e.stopPropagation();
                        onScheduleEarliest(r);
                      }}
                    >
                      单排
                    </button>
                    <button
                      type="button"
                      className="btn batch-op-schedule-btn"
                      disabled={disabled}
                      onClick={(e) => {
                        e.stopPropagation();
                        onAssignLine(r);
                      }}
                    >
                      选择机台
                    </button>
                  </div>
                ),
              },
            ]}
          />
        </div>
      )}
      {ctx && (
        <DemandOrderContextMenu
          x={ctx.x}
          y={ctx.y}
          onClose={() => setCtx(null)}
          items={[
            {
              id: 'earliest',
              label: '单排产（最早可排）',
              disabled: disabled,
              onSelect: () => onScheduleEarliest(ctx.op),
            },
            {
              id: 'assign',
              label: '选择机台…',
              disabled: disabled,
              onSelect: () => onAssignLine(ctx.op),
            },
          ]}
        />
      )}
    </div>
  );
}
