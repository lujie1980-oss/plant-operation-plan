import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { HorizontalResizeSplit } from '../components/HorizontalResizeSplit';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable, type TableColumnDef } from '../components/table/FilterableTable';
import { applyColumnFilters } from '../components/table/filterRows';
import type { TableHeadColumn } from '../components/table/types';
import { useTableLayout } from '../components/table/useTableLayout';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import type {
  MasterPlanDataModelTree,
  MasterPlanPispRoutingDetail,
  RoutingStepDetail,
  RoutingStepInputMaterial,
  RoutingStepOnStandardResource,
  RoutingStepOutputMaterial,
  RoutingSummary,
} from '../types/masterPlanDataModel';
import {
  flattenSpPispTree,
  visibleSpPispRows,
  type SpPispTreeRow,
} from '../utils/masterPlanDataModelTree';
import './MasterPlanDataModelPage.css';
import '../components/table/FilterableTable.css';

function fmtNum(n: number | null | undefined, digits = 2): string {
  if (n == null || Number.isNaN(n)) return '—';
  return n.toLocaleString(undefined, { maximumFractionDigits: digits });
}

const resourceColumns: TableColumnDef<RoutingStepOnStandardResource>[] = [
  { key: 'id', header: 'ID', render: (r) => <code>{r.id}</code> },
  { key: 'resource', header: '标准资源', render: (r) => r.standardResourceId },
  { key: 'priority', header: '优先级', render: (r) => r.resourcePriority ?? '—', width: 72 },
  { key: 'setup', header: '换型(min)', render: (r) => r.setupTimeMinutes, width: 88 },
  { key: 'process', header: '制造 CT(s)', render: (r) => fmtNum(r.processTimeSeconds), width: 96 },
];

const inputColumns: TableColumnDef<RoutingStepInputMaterial>[] = [
  { key: 'id', header: 'ID', render: (r) => <code>{r.id}</code> },
  { key: 'component', header: '组件', render: (r) => r.componentProductCode },
  { key: 'qty', header: '单耗', render: (r) => fmtNum(r.componentQtyPer), width: 80 },
  { key: 'critical', header: '关键件', render: (r) => (r.critical ? '是' : '否'), width: 72 },
];

const outputColumns: TableColumnDef<RoutingStepOutputMaterial>[] = [
  { key: 'id', header: 'ID', render: (r) => <code>{r.id}</code> },
  { key: 'product', header: '产出物料', render: (r) => r.outputProductCode },
  { key: 'qty', header: '产出系数', render: (r) => fmtNum(r.outputQtyPer), width: 88 },
];

const routingColumns: TableColumnDef<RoutingSummary>[] = [
  { key: 'id', header: 'Routing ID', render: (r) => <code>{r.id}</code> },
  { key: 'name', header: '名称', render: (r) => r.routingName },
  { key: 'product', header: '产品', render: (r) => <code>{r.productCode}</code> },
  { key: 'pisp', header: 'PISP', render: (r) => <code>{r.pispId}</code> },
  { key: 'steps', header: '工序数', render: (r) => r.stepCount, width: 72 },
];

type StepRow = RoutingStepDetail & { resourceCount: number; inputCount: number; outputCount: number };

const stepColumns: TableColumnDef<StepRow>[] = [
  { key: 'seq', header: '序号', render: (r) => r.sequenceNo, width: 56 },
  { key: 'id', header: 'Step ID', render: (r) => <code>{r.id}</code> },
  { key: 'name', header: '工序', render: (r) => r.operationName },
  { key: 'res', header: '资源', render: (r) => r.resourceCount, width: 56 },
  { key: 'in', header: '投入', render: (r) => r.inputCount, width: 56 },
  { key: 'out', header: '产出', render: (r) => r.outputCount, width: 56 },
];

export function MasterPlanDataModelPage() {
  const [tree, setTree] = useState<MasterPlanDataModelTree | null>(null);
  const [collapsedSp, setCollapsedSp] = useState<Set<string>>(() => new Set());
  const [selectedPispId, setSelectedPispId] = useState<string | null>(null);
  const [routingDetail, setRoutingDetail] = useState<MasterPlanPispRoutingDetail | null>(null);
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [loadingTree, setLoadingTree] = useState(false);
  const [loadingRouting, setLoadingRouting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTree = useCallback(async () => {
    setLoadingTree(true);
    setError(null);
    try {
      const data = await api.masterPlanDataModelTree();
      setTree(data);
      setCollapsedSp(new Set());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载数据模型树失败');
    } finally {
      setLoadingTree(false);
    }
  }, []);

  const loadRouting = useCallback(async (pispId: string) => {
    setLoadingRouting(true);
    setError(null);
    try {
      const detail = await api.masterPlanPispRouting(pispId);
      setRoutingDetail(detail);
      setSelectedStepId(detail.steps[0]?.id ?? null);
    } catch (e) {
      setRoutingDetail(null);
      setSelectedStepId(null);
      setError(e instanceof Error ? e.message : '加载工艺路线失败');
    } finally {
      setLoadingRouting(false);
    }
  }, []);

  useEffect(() => {
    void loadTree();
  }, [loadTree]);

  useEffect(() => {
    if (!selectedPispId) {
      setRoutingDetail(null);
      setSelectedStepId(null);
      return;
    }
    void loadRouting(selectedPispId);
  }, [selectedPispId, loadRouting]);

  const treeRows = useMemo(() => visibleSpPispRows(flattenSpPispTree(tree), collapsedSp), [tree, collapsedSp]);

  const stepRows: StepRow[] = useMemo(
    () =>
      (routingDetail?.steps ?? []).map((s) => ({
        ...s,
        resourceCount: s.standardResources.length,
        inputCount: s.inputMaterials.length,
        outputCount: s.outputMaterials.length,
      })),
    [routingDetail],
  );

  const selectedStep = useMemo(
    () => routingDetail?.steps.find((s) => s.id === selectedStepId) ?? null,
    [routingDetail, selectedStepId],
  );

  const toggleSp = (spId: string) => {
    setCollapsedSp((prev) => {
      const next = new Set(prev);
      if (next.has(spId)) next.delete(spId);
      else next.add(spId);
      return next;
    });
  };

  return (
    <div className="master-plan-data-model-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="订单协同计划数据模型"
        description="四级库存点 RAW / SFG-A / SFG-B / FG → PISP → Routing → RoutingStep"
        actions={
          <button type="button" className="btn secondary" onClick={() => void loadTree()} disabled={loadingTree}>
            刷新
          </button>
        }
      />

      {error ? <StatusBanner error={error} /> : null}

      <HorizontalResizeSplit
        storageKey="mpdm-main-split"
        defaultLeftRatio={0.34}
        minLeftRatio={0.22}
        maxLeftRatio={0.55}
        className="mpdm-main-split"
        left={
          <div className="card mpdm-panel mpdm-tree-table-panel">
            <div className="mpdm-panel-title">StockingPoint → ProductInStockingPoint</div>
            <SpPispTreeTable
              rows={treeRows}
              loading={loadingTree}
              collapsedSp={collapsedSp}
              selectedPispId={selectedPispId}
              onToggleSp={toggleSp}
              onSelectPisp={setSelectedPispId}
            />
          </div>
        }
        right={
          !selectedPispId ? (
            <div className="card mpdm-panel mpdm-empty-hint">请在左侧选择 PISP 查看 Routing 结构</div>
          ) : (
            <VerticalResizeSplit
              storageKey="mpdm-detail-split"
              defaultTopRatio={0.4}
              minTopRatio={0.25}
              maxTopRatio={0.65}
              className="mpdm-detail-split"
              top={
                <HorizontalResizeSplit
                  storageKey="mpdm-top-split"
                  defaultLeftRatio={0.42}
                  minLeftRatio={0.28}
                  maxLeftRatio={0.62}
                  className="mpdm-top-split"
                  left={
                    <div className="card mpdm-panel">
                      <div className="mpdm-panel-title">Routing</div>
                      <FilterableTable
                        tableId="mpdm-routing"
                        columns={routingColumns}
                        rows={routingDetail?.routing ? [routingDetail.routing] : []}
                        rowKey={(r) => r.id}
                        loading={loadingRouting}
                        emptyText="无工艺路线"
                        unifiedChrome={false}
                        enableSort={false}
                      />
                    </div>
                  }
                  right={
                    <div className="card mpdm-panel">
                      <div className="mpdm-panel-title">RoutingStep</div>
                      <FilterableTable
                        tableId="mpdm-steps"
                        columns={stepColumns}
                        rows={stepRows}
                        rowKey={(r) => r.id}
                        loading={loadingRouting}
                        emptyText="该产品尚未维护工艺"
                        unifiedChrome={false}
                        enableSort={false}
                        onRowClick={(row) => setSelectedStepId(row.id)}
                        getRowClassName={(row) => (row.id === selectedStepId ? 'mpdm-row-selected' : '')}
                      />
                    </div>
                  }
                />
              }
              bottom={
                selectedStep ? (
                  <HorizontalResizeSplit
                    storageKey="mpdm-bottom-split"
                    defaultLeftRatio={0.5}
                    minLeftRatio={0.3}
                    maxLeftRatio={0.7}
                    className="mpdm-bottom-split"
                    left={
                      <div className="card mpdm-panel">
                        <div className="mpdm-panel-title">RoutingStepOnStandardResource</div>
                        <FilterableTable
                          tableId="mpdm-rsosr"
                          columns={resourceColumns}
                          rows={selectedStep.standardResources}
                          rowKey={(r) => r.id}
                          emptyText="该工序未配置标准资源"
                          unifiedChrome={false}
                          enableSort={false}
                        />
                      </div>
                    }
                    right={
                      <VerticalResizeSplit
                        storageKey="mpdm-io-split"
                        defaultTopRatio={0.5}
                        minTopRatio={0.25}
                        maxTopRatio={0.75}
                        className="mpdm-io-split"
                        top={
                          <div className="card mpdm-panel">
                            <div className="mpdm-panel-title">RoutingStepInputMaterial</div>
                            <FilterableTable
                              tableId="mpdm-input"
                              columns={inputColumns}
                              rows={selectedStep.inputMaterials}
                              rowKey={(r) => r.id}
                              emptyText="该工序无投入物料"
                              unifiedChrome={false}
                              enableSort={false}
                            />
                          </div>
                        }
                        bottom={
                          <div className="card mpdm-panel">
                            <div className="mpdm-panel-title">RoutingStepOutputMaterial</div>
                            <FilterableTable
                              tableId="mpdm-output"
                              columns={outputColumns}
                              rows={selectedStep.outputMaterials}
                              rowKey={(r) => r.id}
                              emptyText="该工序无产出物料"
                              unifiedChrome={false}
                              enableSort={false}
                            />
                          </div>
                        }
                      />
                    }
                  />
                ) : (
                  <div className="card mpdm-panel mpdm-empty-hint">请选择 RoutingStep 查看资源与物料</div>
                )
              }
            />
          )
        }
      />
    </div>
  );
}

function spPispFilterText(row: SpPispTreeRow, key: string): string {
  switch (key) {
    case 'type':
      return row.nodeType === 'SP' ? 'StockingPoint' : 'PISP';
    case 'stockingPoint':
      return row.nodeType === 'SP'
        ? `${row.stockingPointCode} ${row.displayName ?? ''}`.trim()
        : row.stockingPointCode;
    case 'productCode':
      return row.productCode ?? '';
    case 'productName':
      return row.productName ?? '';
    case 'bomTier':
      return row.bomTierLabel ?? '';
    case 'pispId':
      return row.pispId ?? '';
    case 'routing':
      if (row.nodeType === 'SP' || row.hasRouting == null) return '';
      return row.hasRouting ? '有' : '无';
    default:
      return '';
  }
}

const SP_PISP_HEAD_COLUMNS: TableHeadColumn[] = [
  { key: '_toggle', header: '', filterable: false, width: 28, resizable: false },
  { key: 'type', header: '类型' },
  { key: 'stockingPoint', header: '库存点' },
  { key: 'productCode', header: '产品代码' },
  { key: 'productName', header: '产品名称' },
  { key: 'bomTier', header: 'BOM 阶' },
  { key: 'pispId', header: 'PISP ID' },
  { key: 'routing', header: '工艺' },
  { key: 'count', header: '数量', filterable: false, align: 'right', width: 56 },
];

function SpPispTreeTable({
  rows,
  loading,
  collapsedSp,
  selectedPispId,
  onToggleSp,
  onSelectPisp,
}: {
  rows: SpPispTreeRow[];
  loading: boolean;
  collapsedSp: Set<string>;
  selectedPispId: string | null;
  onToggleSp: (spId: string) => void;
  onSelectPisp: (pispId: string) => void;
}) {
  const { filters } = useTableLayout('mpdm-sp-pisp-tree', SP_PISP_HEAD_COLUMNS);

  const filteredRows = useMemo(
    () =>
      applyColumnFilters(rows, filters, SP_PISP_HEAD_COLUMNS, (row, key) => spPispFilterText(row, key)),
    [rows, filters],
  );

  if (loading && rows.length === 0) {
    return <p className="mpdm-muted">加载中…</p>;
  }
  if (filteredRows.length === 0) {
    return <p className="mpdm-muted">当前工作区暂无物料主数据</p>;
  }

  return (
    <div className="mpdm-tree-table-wrap filterable-table-wrap">
      <table
        className="ft-table ft-table-clip-rows data-table mpdm-tree-table"
        data-table-id="mpdm-sp-pisp-tree"
      >
        <thead>
          <tr>
            <th data-col-key="_toggle" style={{ width: 28 }} />
            <th data-col-key="type">类型</th>
            <th data-col-key="stockingPoint">库存点</th>
            <th data-col-key="productCode">产品代码</th>
            <th data-col-key="productName">产品名称</th>
            <th data-col-key="bomTier">BOM 阶</th>
            <th data-col-key="pispId">PISP ID</th>
            <th data-col-key="routing">工艺</th>
            <th data-col-key="count" style={{ width: 56 }}>
              数量
            </th>
          </tr>
        </thead>
        <tbody>
          {filteredRows.map((row) => {
            const isSp = row.nodeType === 'SP';
            const expanded = isSp && !collapsedSp.has(row.stockingPointId);
            const selected = !isSp && row.pispId === selectedPispId;
            return (
              <tr
                key={row.rowKey}
                className={`${selected ? 'mpdm-row-selected' : ''}${isSp ? ' mpdm-tree-sp-row' : ''}`}
                onClick={() => {
                  if (isSp) onToggleSp(row.stockingPointId);
                  else if (row.pispId) onSelectPisp(row.pispId);
                }}
              >
                <td className="mpdm-tree-toggle">
                  {isSp ? (
                    <button
                      type="button"
                      className="mpdm-tree-toggle-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        onToggleSp(row.stockingPointId);
                      }}
                    >
                      {expanded ? '▾' : '▸'}
                    </button>
                  ) : (
                    <span className="mpdm-tree-indent" />
                  )}
                </td>
                <td data-col-key="type">{isSp ? 'StockingPoint' : 'PISP'}</td>
                <td data-col-key="stockingPoint">
                  {isSp ? (
                    <>
                      <code>{row.stockingPointCode}</code>
                      <span className="mpdm-sp-label"> {row.displayName}</span>
                    </>
                  ) : (
                    row.stockingPointCode
                  )}
                </td>
                <td data-col-key="productCode">{row.productCode ? <code>{row.productCode}</code> : '—'}</td>
                <td data-col-key="productName">{row.productName ?? '—'}</td>
                <td data-col-key="bomTier">{row.bomTierLabel ?? '—'}</td>
                <td data-col-key="pispId">{row.pispId ? <code>{row.pispId}</code> : '—'}</td>
                <td data-col-key="routing">
                  {!isSp && row.hasRouting != null ? (
                    row.hasRouting ? (
                      <span className="badge ok">有</span>
                    ) : (
                      <span className="badge muted">无</span>
                    )
                  ) : (
                    '—'
                  )}
                </td>
                <td className="num" data-col-key="count">
                  {isSp ? row.pispCount : '—'}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
