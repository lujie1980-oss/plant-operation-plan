import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable, type TableColumnDef } from '../components/table/FilterableTable';
import { usePlan } from '../context/PlanContext';
import type {
  DemandPoolKpi,
  MaterialBalanceDay,
  MaterialBalanceRow,
  MaterialDemandDetail,
  MaterialDemandTreeNode,
  MaterialRequirementReport,
} from '../types/api';
import './KittingPage.css';

const METRIC_ROWS = [
  { key: 'openingQty' as const, label: '期初', className: 'opening' },
  { key: 'demandQty' as const, label: '需求', className: 'demand' },
  { key: 'supplyQty' as const, label: '供应', className: 'supply' },
  { key: 'closingQty' as const, label: '期末', className: 'closing' },
  { key: 'shortageQty' as const, label: '缺口', className: 'gap' },
];

type MaterialMetricRow = {
  mat: MaterialBalanceRow;
  metric: (typeof METRIC_ROWS)[number];
  metricIndex: number;
};

type ScopeFilter = 'all' | 'critical' | 'shortage';

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function fmtDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00');
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

function dayByDate(row: MaterialBalanceRow, date: string): MaterialBalanceDay | undefined {
  return row.days.find((d) => d.date === date);
}

function metricValue(day: MaterialBalanceDay | undefined, key: (typeof METRIC_ROWS)[number]['key']): number {
  if (!day) return 0;
  return day[key];
}

export function KittingPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [report, setReport] = useState<MaterialRequirementReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [search, setSearch] = useState('');
  const [scope, setScope] = useState<ScopeFilter>('all');
  const [pickedMaterials, setPickedMaterials] = useState<string[]>([]);

  const [selectedMaterial, setSelectedMaterial] = useState<string | null>(null);
  const [demandDetail, setDemandDetail] = useState<MaterialDemandDetail | null>(null);
  const [usagesLoading, setUsagesLoading] = useState(false);

  const load = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      setReport(await api.materialBalance(versionId ?? undefined));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const compute = async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const data = await api.computeMaterialRequirements(activePlanVersionId ?? undefined);
      setReport(data);
      setSuccess(`物料需求测算完成，覆盖 ${data.materials.length} 种物料`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '测算失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(activePlanVersionId);
  }, [activePlanVersionId, load]);

  const kpis: DemandPoolKpi[] = report?.kpis ?? [];
  const dates = report?.dates ?? [];
  const allMaterials = report?.materials ?? [];

  const filteredMaterials = useMemo(() => {
    let list = allMaterials;
    const q = search.trim().toLowerCase();
    if (q) {
      list = list.filter((m) => m.productCode.toLowerCase().includes(q));
    }
    if (scope === 'critical') {
      list = list.filter((m) => m.critical);
    } else if (scope === 'shortage') {
      list = list.filter((m) => m.totalShortageQty > 0);
    }
    if (pickedMaterials.length > 0) {
      const set = new Set(pickedMaterials);
      list = list.filter((m) => set.has(m.productCode));
    }
    return list;
  }, [allMaterials, search, scope, pickedMaterials]);

  const balanceRows = useMemo(
    () =>
      filteredMaterials.flatMap((mat) =>
        METRIC_ROWS.map((metric, metricIndex) => ({ mat, metric, metricIndex })),
      ),
    [filteredMaterials],
  );

  const balanceColumns = useMemo((): TableColumnDef<MaterialMetricRow>[] => {
    const dateCols: TableColumnDef<MaterialMetricRow>[] = dates.map((d) => ({
      key: d,
      header: fmtDate(d),
      className: 'date-col num',
      align: 'right',
      render: ({ mat, metric }) => {
        const day = dayByDate(mat, d);
        const val = metricValue(day, metric.key);
        const highlightGap = metric.key === 'shortageQty' && val > 0;
        return (
          <span className={`${metric.className} ${highlightGap ? 'has-gap' : ''} ${!day ? 'muted' : ''}`}>
            {day ? fmtQty(val) : '—'}
          </span>
        );
      },
    }));
    return [
      {
        key: 'material',
        header: '物料',
        className: 'sticky-col material-col',
        filterable: false,
        render: ({ mat, metricIndex }) =>
          metricIndex === 0 ? (
            <div
              className={selectedMaterial === mat.productCode ? 'is-selected' : ''}
              onClick={() => setSelectedMaterial(mat.productCode)}
              onKeyDown={(e) => e.key === 'Enter' && setSelectedMaterial(mat.productCode)}
              role="button"
              tabIndex={0}
              title="点击查看该物料所满足的需求"
            >
              <span className="material-code">{mat.productCode}</span>
              {mat.critical && <span className="tag-critical">关键</span>}
              {mat.totalShortageQty > 0 && (
                <span className="tag-gap">缺 {fmtQty(mat.totalShortageQty)}</span>
              )}
            </div>
          ) : null,
        getFilterText: ({ mat }) => mat.productCode,
      },
      {
        key: 'metric',
        header: '指标',
        className: 'sticky-col metric-col',
        filterable: false,
        render: ({ metric }) => <span className={`metric-label ${metric.className}`}>{metric.label}</span>,
      },
      ...dateCols,
    ];
  }, [dates, selectedMaterial]);

  useEffect(() => {
    if (!selectedMaterial) {
      setDemandDetail(null);
      return;
    }
    if (!filteredMaterials.some((m) => m.productCode === selectedMaterial)) {
      setSelectedMaterial(null);
      setDemandDetail(null);
      return;
    }
    let cancelled = false;
    setUsagesLoading(true);
    void api
      .materialDemandUsages(selectedMaterial, activePlanVersionId ?? undefined)
      .then((data) => {
        if (!cancelled) setDemandDetail(data);
      })
      .catch(() => {
        if (!cancelled) setDemandDetail(null);
      })
      .finally(() => {
        if (!cancelled) setUsagesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedMaterial, filteredMaterials, activePlanVersionId]);

  useEffect(() => {
    if (filteredMaterials.length > 0 && selectedMaterial == null) {
      setSelectedMaterial(filteredMaterials[0].productCode);
    }
  }, [filteredMaterials, selectedMaterial]);

  const onPickChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const opts = [...e.target.selectedOptions].map((o) => o.value);
    setPickedMaterials(opts);
  };

  const clearPick = () => setPickedMaterials([]);

  const currentScenario = scenarios.find((s) => s.scenarioId === selectedScenarioId);
  const scenarioHint = currentScenario
    ? `场景「${currentScenario.name}」${activePlanVersionId ? ` · ${activePlanVersionId}` : ''}`
    : '请先在顶部选择计划场景';

  return (
    <div className={`mrp-page ${embedded ? 'mrp-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          title="物料需求"
          showScenarioSelector
          description={`按订单 BOM 展开需求量，结合库存与工单完工供应，逐日滚动物料平衡。${scenarioHint}`}
          actions={
            <>
              <button
                type="button"
                className="btn"
                onClick={() => void load(activePlanVersionId)}
                disabled={loading}
              >
              刷新
            </button>
            <button type="button" className="btn primary" onClick={() => void compute()} disabled={loading}>
              测算需求
            </button>
            </>
          }
        />
      )}
      {!embedded && <StatusBanner loading={loading} error={error} success={success} />}

      {report && (
        <p className="mrp-horizon">
          平衡区间：{report.horizonStart} ～ {report.horizonEnd}
          <span className="mrp-filter-summary">
            显示 {filteredMaterials.length} / {allMaterials.length} 种物料
          </span>
        </p>
      )}

      <div className="mrp-layout">
        <aside className="mrp-kpi-panel card">
          <h3 className="panel-title">关键 KPI</h3>
          <div className="panel-scroll">
            <ul className="kpi-list">
              {kpis.map((k) => (
                <li key={k.metricId} className={`kpi-item severity-${k.severity}`}>
                  <span className="kpi-item-label">{k.label}</span>
                  <span className="kpi-item-value">
                    {k.value.toLocaleString(undefined, { maximumFractionDigits: 1 })}
                    <small>{k.unit}</small>
                  </span>
                </li>
              ))}
            </ul>
            {kpis.length === 0 && <p className="empty-hint">暂无 KPI，请先测算</p>}
          </div>
        </aside>

        <section className="mrp-balance-panel card">
          <div className="mrp-balance-head">
            <h3 className="panel-title">物料平衡表</h3>
            <span className="mrp-legend">
              <span className="leg opening">期初</span>
              <span className="leg demand">需求</span>
              <span className="leg supply">供应</span>
              <span className="leg closing">期末</span>
              <span className="leg gap">缺口</span>
            </span>
          </div>

          <div className="mrp-filter-bar">
            <input
              type="search"
              className="mrp-search"
              placeholder="搜索物料编码…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="mrp-scope-chips">
              {(
                [
                  ['all', '全部'],
                  ['critical', '关键料'],
                  ['shortage', '有缺口'],
                ] as const
              ).map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  className={`chip ${scope === id ? 'active' : ''}`}
                  onClick={() => setScope(id)}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="mrp-pick-materials">
              <select
                multiple
                className="mrp-multi-select"
                value={pickedMaterials}
                onChange={onPickChange}
                title="按住 Ctrl 多选指定物料"
              >
                {allMaterials.map((m) => (
                  <option key={m.productCode} value={m.productCode}>
                    {m.productCode}
                    {m.totalShortageQty > 0 ? ' ⚠' : ''}
                  </option>
                ))}
              </select>
              {pickedMaterials.length > 0 && (
                <button type="button" className="btn btn-sm" onClick={clearPick}>
                  清除指定 ({pickedMaterials.length})
                </button>
              )}
            </div>
          </div>

          <VerticalResizeSplit
            storageKey="mrp-balance-detail-ratio"
            minTopRatio={0.35}
            maxTopRatio={0.82}
            className="mrp-balance-split"
            top={
              <div className="mrp-table-scroll panel-scroll">
                {filteredMaterials.length === 0 ? (
                  <p className="empty-hint">无匹配物料，请调整筛选条件</p>
                ) : (
                  <FilterableTable
                    tableId="kitting-material-balance"
                    tableClassName="mrp-balance-table"
                    wrapClassName="ft-table-wrap"
                    rows={balanceRows}
                    rowKey={({ mat, metric }) => `${mat.productCode}-${metric.key}`}
                    getRowClassName={({ mat, metric, metricIndex }) => {
                      const parts = ['metric-row', metric.className];
                      if (selectedMaterial === mat.productCode) parts.push('material-selected');
                      if (metricIndex === 0) parts.push('material-group-start');
                      if (metricIndex === METRIC_ROWS.length - 1) parts.push('material-group-end');
                      return parts.join(' ');
                    }}
                    columns={balanceColumns}
                  />
                )}
              </div>
            }
            bottom={
              <MaterialDemandPanel
                productCode={selectedMaterial}
                detail={demandDetail}
                loading={usagesLoading}
              />
            }
          />
        </section>
      </div>
    </div>
  );
}

function MaterialDemandPanel({
  productCode,
  detail,
  loading,
}: {
  productCode: string | null;
  detail: MaterialDemandDetail | null;
  loading: boolean;
}) {
  if (!productCode) {
    return (
      <div className="mrp-demand-panel">
        <p className="empty-hint">在平衡表中点击物料，查看其对应的需求</p>
      </div>
    );
  }

  const roots = detail?.roots ?? [];

  return (
    <div className="mrp-demand-panel">
      <div className="mrp-demand-head">
        <h4>
          物料 <strong>{productCode}</strong> 满足链
        </h4>
        {loading ? (
          <span className="mrp-demand-meta">加载中…</span>
        ) : (
          <span className="mrp-demand-meta">
            {roots.length} 条订单链 · {detail?.pathCount ?? 0} 条 BOM 路径 · 合计{' '}
            {fmtQty(detail?.totalQuantity ?? 0)}
          </span>
        )}
      </div>
      <div className="mrp-demand-scroll panel-scroll">
        {loading ? (
          <p className="empty-hint">加载满足链…</p>
        ) : roots.length === 0 ? (
          <p className="empty-hint">当前开放订单中无该物料的 BOM 需求</p>
        ) : (
          <ul className="demand-tree-forest">
            {roots.map((root) => (
              <DemandTreeBranch key={root.nodeId} node={root} depth={0} />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function nodeTypeLabel(type: string): string {
  if (type === 'SALES_ORDER') return '销售订单';
  if (type === 'WORK_ORDER') return '工单';
  if (type === 'MATERIAL') return '物料需求';
  return '计划';
}

function DemandTreeBranch({ node, depth }: { node: MaterialDemandTreeNode; depth: number }) {
  const hasChildren = node.children.length > 0;

  return (
    <li className={`demand-tree-item depth-${depth} type-${node.nodeType.toLowerCase()}`}>
      <div className="demand-tree-row">
        <span className={`demand-tree-type ${node.nodeType.toLowerCase()}`}>
          {nodeTypeLabel(node.nodeType)}
        </span>
        <span className="demand-tree-label">{node.label}</span>
        <span className="demand-tree-meta">
          <span className="demand-tree-date">{node.needDate}</span>
          <span className="demand-tree-qty">×{fmtQty(node.quantity)}</span>
        </span>
      </div>
      {hasChildren && (
        <ul className="demand-tree-children">
          {node.children.map((child) => (
            <DemandTreeBranch key={child.nodeId} node={child} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}
