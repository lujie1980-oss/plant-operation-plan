import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { PpToolbar, PpToolbarHint, PpToolbarRow } from '../components/PpToolbar';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { useScheduleVersion } from '../context/ScheduleVersionContext';
import type {
  InventoryAvailabilitySummary,
  InventoryBatchAllocation,
  ProductionBatchKitting,
  WorkOrderKittingLine,
} from '../types/api';
import './ProductionPlanPage.css';

const KITTING_STATUS_LABEL: Record<string, string> = {
  KITTED: '齐套',
  SHORT: '缺料',
  UNKNOWN: '未评估',
  KITTING_OK: '齐套',
  SHORTAGE: '缺料',
};

function kittingClass(status: string) {
  if (status === 'SHORT' || status === 'SHORTAGE') return 'badge danger';
  if (status === 'KITTED' || status === 'KITTING_OK') return 'badge ok';
  return 'badge muted';
}

function kittingLabel(status: string) {
  return KITTING_STATUS_LABEL[status] ?? status;
}

function isShortage(status: string) {
  return status === 'SHORT' || status === 'SHORTAGE';
}

export function ScheduleKittingPage() {
  useScheduleVersion();
  const [rows, setRows] = useState<ProductionBatchKitting[]>([]);
  const [inventory, setInventory] = useState<InventoryAvailabilitySummary[]>([]);
  const [allocations, setAllocations] = useState<InventoryBatchAllocation[]>([]);
  const [activeBatch, setActiveBatch] = useState<ProductionBatchKitting | null>(null);
  const [activeProductCode, setActiveProductCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [allocLoading, setAllocLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [kitting, inv] = await Promise.all([
        api.schedulingBatches.listKitting(),
        api.workOrders.inventoryAvailability(),
      ]);
      setRows(kitting);
      setInventory(inv);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (rows.length === 0) {
      setActiveBatch(null);
      return;
    }
    setActiveBatch((prev) => {
      if (prev) {
        const match = rows.find((r) => r.batchNo === prev.batchNo);
        if (match) return match;
      }
      return rows[0];
    });
  }, [rows]);

  useEffect(() => {
    if (!activeProductCode) {
      setAllocations([]);
      return;
    }
    let cancelled = false;
    setAllocLoading(true);
    void api.schedulingBatches
      .batchAllocations(activeProductCode)
      .then((data) => {
        if (!cancelled) setAllocations(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : '占用明细加载失败');
      })
      .finally(() => {
        if (!cancelled) setAllocLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeProductCode]);

  const compute = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const kitting = await api.schedulingBatches.computeKitting();
      setRows(kitting);
      const shortage = kitting.filter((r) => isShortage(r.kittingStatus)).length;
      setSuccess(
        shortage > 0
          ? `齐套检查完成：${kitting.length} 个待排批次，${shortage} 个缺料`
          : `齐套检查完成：${kitting.length} 个待排批次全部齐套`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '齐套检查失败');
    } finally {
      setLoading(false);
    }
  };

  const updateEligible = useCallback(async (batchNo: string, eligible: boolean) => {
    setError(null);
    try {
      const updated = await api.schedulingBatches.updatePendingScheduleEligible(batchNo, eligible);
      setRows((prev) => prev.map((r) => (r.batchNo === batchNo ? { ...r, ...updated } : r)));
      setActiveBatch((prev) => (prev?.batchNo === batchNo ? { ...prev, ...updated } : prev));
    } catch (e) {
      setError(e instanceof Error ? e.message : '更新待排状态失败');
    }
  }, []);

  const shortageCount = rows.filter((r) => isShortage(r.kittingStatus)).length;

  const batchColumns = useMemo(
    () => [
      {
        key: 'batchNo',
        header: '批次号',
        className: 'mono',
        render: (row: ProductionBatchKitting) => row.batchNo,
      },
      {
        key: 'quantity',
        header: '批次量',
        render: (row: ProductionBatchKitting) => row.quantity,
      },
      {
        key: 'kittingStatus',
        header: '齐套',
        render: (row: ProductionBatchKitting) => (
          <span className={kittingClass(row.kittingStatus)}>{kittingLabel(row.kittingStatus)}</span>
        ),
      },
      {
        key: 'pendingScheduleEligible',
        header: '待排',
        render: (row: ProductionBatchKitting) => (
          <select
            className={`pwo-eligible-select ${row.pendingScheduleEligible ? '' : 'blocked'}`}
            value={row.pendingScheduleEligible ? 'SCHEDULABLE' : 'NOT_SCHEDULABLE'}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => {
              void updateEligible(row.batchNo, e.target.value === 'SCHEDULABLE');
            }}
          >
            <option value="SCHEDULABLE">可排</option>
            <option value="NOT_SCHEDULABLE">暂排</option>
          </select>
        ),
      },
      {
        key: 'workOrderNo',
        header: '工单（参考）',
        className: 'mono muted-ref',
        render: (row: ProductionBatchKitting) => row.workOrderNo,
      },
      {
        key: 'productCode',
        header: '产品（参考）',
        className: 'mono muted-ref',
        render: (row: ProductionBatchKitting) => row.productCode,
      },
      {
        key: 'workOrderQuantity',
        header: '工单量（参考）',
        className: 'muted-ref',
        render: (row: ProductionBatchKitting) => row.workOrderQuantity,
      },
    ],
    [updateEligible],
  );

  const materialColumns = useMemo(
    () => [
      {
        key: 'component',
        header: '组件料号',
        className: 'mono',
        render: (line: WorkOrderKittingLine) => line.componentProductCode,
      },
      { key: 'required', header: '需求量', render: (line: WorkOrderKittingLine) => line.requiredQty },
      { key: 'available', header: '可用量', render: (line: WorkOrderKittingLine) => line.availableQty },
      {
        key: 'shortage',
        header: '缺料',
        render: (line: WorkOrderKittingLine) => (line.shortage ? '是' : '否'),
      },
    ],
    [],
  );

  const inventoryColumns = useMemo(
    () => [
      {
        key: 'productCode',
        header: '料号',
        className: 'mono',
        render: (row: InventoryAvailabilitySummary) => row.productCode,
      },
      { key: 'available', header: '可用量', render: (row: InventoryAvailabilitySummary) => row.totalAvailable },
      { key: 'onhand', header: '在库量', render: (row: InventoryAvailabilitySummary) => row.totalOnhand },
      {
        key: 'points',
        header: '库存点',
        render: (row: InventoryAvailabilitySummary) => row.stockingPointCount,
      },
    ],
    [],
  );

  const allocationColumns = useMemo(
    () => [
      {
        key: 'batchNo',
        header: '批次号',
        className: 'mono',
        render: (row: InventoryBatchAllocation) => row.batchNo,
      },
      {
        key: 'workOrderNo',
        header: '工单（参考）',
        className: 'mono muted-ref',
        render: (row: InventoryBatchAllocation) => row.workOrderNo,
      },
      {
        key: 'finished',
        header: '成品（参考）',
        className: 'mono muted-ref',
        render: (row: InventoryBatchAllocation) => row.finishedProductCode,
      },
      { key: 'batchQty', header: '批次量', render: (row: InventoryBatchAllocation) => row.batchQuantity },
      { key: 'required', header: '需求量', render: (row: InventoryBatchAllocation) => row.requiredQty },
      {
        key: 'kitting',
        header: '齐套',
        render: (row: InventoryBatchAllocation) => (
          <span className={kittingClass(row.kittingStatus)}>{kittingLabel(row.kittingStatus)}</span>
        ),
      },
    ],
    [],
  );

  return (
    <div className="production-plan-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="物料齐套"
        showScheduleVersionSelector
        description="待排批次的 BOM 关键件齐套与库存占用分析；工单号/产品/工单量为参考属性。"
      />
      <StatusBanner loading={loading || allocLoading} error={error} success={success} />

      <PpToolbar>
        <PpToolbarRow>
          <div className="pp-filters">
            <span className="pp-stat">
              待排批次 <strong>{rows.length}</strong>
            </span>
            <span className="pp-filter-sep" aria-hidden />
            <span className="pp-stat">
              缺料批次 <strong>{shortageCount}</strong>
            </span>
          </div>
          <div className="pp-toolbar-actions">
            <button type="button" className="btn" onClick={() => void load()} disabled={loading}>
              刷新
            </button>
            <button type="button" className="btn primary" onClick={() => void compute()} disabled={loading}>
              齐套检查
            </button>
          </div>
        </PpToolbarRow>
        <PpToolbarHint>
          <p className="pp-hint">
            在 <Link to="/scheduling/batch-plan">批次计划</Link> 拆批后检查齐套；点击物料或库存行可联动查看批次占用。
          </p>
        </PpToolbarHint>
      </PpToolbar>

      {rows.length === 0 && !loading ? (
        <section className="card pp-chain-empty">
          <p className="muted-text">暂无待排批次。</p>
          <p className="muted-text">
            请先在 <Link to="/scheduling/batch-plan">批次计划</Link> 对已下发工单执行拆批。
          </p>
        </section>
      ) : (
        <VerticalResizeSplit
          className="pp-split"
          storageKey="schedule-kitting-split-ratio"
          minTopRatio={0.25}
          maxTopRatio={0.6}
          top={
            <section className="card pp-wo-panel">
              <div className="pp-panel-head">
                <h3 className="panel-title">待排批次列表</h3>
              </div>
              <div className="pp-panel-scroll pp-table-wrap">
                <FilterableTable
                  tableId="schedule-kitting-batches"
                  tableClassName="pp-table data-table"
                  wrapClassName="ft-table-wrap"
                  rows={rows}
                  rowKey={(row) => row.batchNo}
                  getRowClassName={(row) => (row.batchNo === activeBatch?.batchNo ? 'active' : '')}
                  columns={batchColumns}
                  loading={loading}
                  onRowClick={setActiveBatch}
                />
              </div>
            </section>
          }
          bottom={
            <section className="card pp-chain-panel">
              <div className="pp-panel-head">
                <div className="pp-chain-title">
                  <h3 className="panel-title">
                    {activeBatch ? `齐套明细 · ${activeBatch.batchNo}` : '齐套明细'}
                  </h3>
                  {activeBatch && (
                    <span className="pp-chain-sub">
                      批次量 {activeBatch.quantity}
                      {' · '}
                      工单 {activeBatch.workOrderNo}（{activeBatch.productCode} × {activeBatch.workOrderQuantity}）
                      {' · '}
                      <span className={kittingClass(activeBatch.kittingStatus)}>
                        {kittingLabel(activeBatch.kittingStatus)}
                      </span>
                    </span>
                  )}
                </div>
              </div>
              <div className="pp-nested-split-host">
                <HorizontalResizeSplit
                  storageKey="schedule-kitting-bottom-outer"
                  minLeftRatio={0.22}
                  maxLeftRatio={0.45}
                  left={
                    <div className="pp-sub-panel">
                      <h4 className="panel-title">物料需求</h4>
                      {!activeBatch ? (
                        <p className="muted-text">请选择批次</p>
                      ) : activeBatch.lines.length === 0 ? (
                        <p className="muted-text">暂无 BOM 关键件（请先执行齐套检查）</p>
                      ) : (
                        <div className="pp-panel-scroll pp-table-wrap">
                          <FilterableTable
                            tableId={`sk-material-${activeBatch.batchNo}`}
                            tableClassName="pp-table data-table"
                            wrapClassName="ft-table-wrap"
                            rows={activeBatch.lines}
                            rowKey={(line) => line.componentProductCode}
                            getRowClassName={(line) =>
                              line.componentProductCode === activeProductCode
                                ? 'active'
                                : line.shortage
                                  ? 'shortage'
                                  : ''
                            }
                            columns={materialColumns}
                            onRowClick={(line) => setActiveProductCode(line.componentProductCode)}
                          />
                        </div>
                      )}
                    </div>
                  }
                  right={
                    <HorizontalResizeSplit
                      storageKey="schedule-kitting-bottom-inner"
                      minLeftRatio={0.35}
                      maxLeftRatio={0.65}
                      left={
                        <div className="pp-sub-panel">
                          <h4 className="panel-title">可用库存</h4>
                          <div className="pp-panel-scroll pp-table-wrap">
                            <FilterableTable
                              tableId="schedule-kitting-inventory"
                              tableClassName="pp-table data-table"
                              wrapClassName="ft-table-wrap"
                              rows={inventory}
                              rowKey={(row) => row.productCode}
                              columns={inventoryColumns}
                              getRowClassName={(row) =>
                                row.productCode === activeProductCode ? 'active' : ''
                              }
                              onRowClick={(row) => setActiveProductCode(row.productCode)}
                            />
                          </div>
                        </div>
                      }
                      right={
                        <div className="pp-sub-panel">
                          <h4 className="panel-title">
                            批次占用{activeProductCode ? ` · ${activeProductCode}` : ''}
                          </h4>
                          {!activeProductCode ? (
                            <p className="muted-text">请选择库存料号</p>
                          ) : (
                            <div className="pp-panel-scroll pp-table-wrap">
                              <FilterableTable
                                tableId={`sk-alloc-${activeProductCode}`}
                                tableClassName="pp-table data-table"
                                wrapClassName="ft-table-wrap"
                                rows={allocations}
                                rowKey={(row) => row.batchNo}
                                columns={allocationColumns}
                                emptyText="暂无待排批次占用该料号"
                              />
                            </div>
                          )}
                        </div>
                      }
                    />
                  }
                />
              </div>
            </section>
          }
        />
      )}
    </div>
  );
}
