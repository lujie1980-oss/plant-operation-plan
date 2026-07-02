import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable, type TableColumnDef } from '../components/table/FilterableTable';
import { usePlan } from '../context/PlanContext';
import type {
  MaterialBalancePeriod,
  MaterialBalanceRow,
  MaterialPeriodHeader,
  MaterialRequirementReport,
  SupplyRoutingCandidate,
} from '../types/api';
import './MaterialPlanningPage.css';

const METRIC_ROWS = [
  { key: 'openingQty' as const, label: '期初', className: 'opening' },
  { key: 'demandQty' as const, label: '需求', className: 'demand' },
  { key: 'supplyQty' as const, label: '供应', className: 'supply' },
  { key: 'closingQty' as const, label: '期末', className: 'closing' },
  { key: 'shortageQty' as const, label: '缺口', className: 'gap' },
];

type PeriodMetricRow = {
  mat: MaterialBalanceRow;
  metric: (typeof METRIC_ROWS)[number];
};

function fmtQty(n: number): string {
  if (Number.isInteger(n)) return n.toLocaleString();
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function periodById(row: MaterialBalanceRow, periodId: string): MaterialBalancePeriod | undefined {
  return row.periods?.find((p) => p.periodId === periodId);
}

function metricValue(
  period: MaterialBalancePeriod | undefined,
  key: (typeof METRIC_ROWS)[number]['key'],
): number {
  if (!period) return 0;
  return period[key];
}

export function SupplyDemandBalancePage() {
  const { activePlanVersionId } = usePlan();
  const [report, setReport] = useState<MaterialRequirementReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [selectedPispId, setSelectedPispId] = useState<string | null>(null);
  const [periodFrom, setPeriodFrom] = useState<string | null>(null);
  const [periodTo, setPeriodTo] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<SupplyRoutingCandidate[]>([]);
  const [actionLoading, setActionLoading] = useState(false);

  const load = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.ontologyMaterialPlanningBalance(versionId ?? undefined);
      setReport(data);
      if (data.periodHeaders.length > 0) {
        setPeriodFrom((prev) => prev ?? data.periodHeaders[0].periodId);
        setPeriodTo((prev) => prev ?? data.periodHeaders[data.periodHeaders.length - 1].periodId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(activePlanVersionId);
  }, [activePlanVersionId, load]);

  const periodHeaders: MaterialPeriodHeader[] = report?.periodHeaders ?? [];

  const filteredMaterials = useMemo(() => {
    const materials = report?.materials ?? [];
    const q = search.trim().toLowerCase();
    return materials.filter((m) => !q || m.productCode.toLowerCase().includes(q));
  }, [report, search]);

  useEffect(() => {
    if (filteredMaterials.length > 0 && !selectedPispId) {
      setSelectedPispId(filteredMaterials[0].pispId ?? null);
    }
  }, [filteredMaterials, selectedPispId]);

  const selectedMaterial = useMemo(
    () => filteredMaterials.find((m) => m.pispId === selectedPispId) ?? null,
    [filteredMaterials, selectedPispId],
  );

  const tableRows: PeriodMetricRow[] = useMemo(() => {
    if (!selectedMaterial) return [];
    return METRIC_ROWS.map((metric) => ({ mat: selectedMaterial, metric }));
  }, [selectedMaterial]);

  const materialColumns: TableColumnDef<MaterialBalanceRow>[] = useMemo(
    () => [
      {
        key: 'productCode',
        header: '物料',
        render: (row) => (
          <span className={row.pispId === selectedPispId ? 'is-selected' : ''}>{row.productCode}</span>
        ),
        getFilterText: (row) => row.productCode,
      },
      {
        key: 'shortage',
        header: '缺口合计',
        className: 'num',
        align: 'right',
        render: (row) => fmtQty(row.totalShortageQty),
      },
    ],
    [selectedPispId],
  );

  const periodColumns: TableColumnDef<PeriodMetricRow>[] = useMemo(
    () =>
      periodHeaders.map((header) => ({
        key: header.periodId,
        header: header.label,
        className: 'date-col num',
        align: 'right' as const,
        render: ({ mat, metric }) => {
          const period = periodById(mat, header.periodId);
          const value = metricValue(period, metric.key);
          const highlightGap = metric.key === 'shortageQty' && value > 0;
          return (
            <span className={`${metric.className} ${highlightGap ? 'has-gap' : ''}`}>
              {fmtQty(value)}
            </span>
          );
        },
      })),
    [periodHeaders],
  );

  const metricColumns: TableColumnDef<PeriodMetricRow>[] = useMemo(
    () => [
      {
        key: 'metric',
        header: '度量',
        className: 'sticky-col metric-col',
        filterable: false,
        render: ({ metric }) => (
          <span className={`metric-label ${metric.className}`}>{metric.label}</span>
        ),
      },
      ...periodColumns,
    ],
    [periodColumns],
  );

  const loadCandidates = async () => {
    if (!selectedPispId || !periodFrom || !periodTo) return;
    setActionLoading(true);
    setError(null);
    try {
      const rows = await api.ontologyMaterialPlanningRoutingCandidates(
        selectedPispId,
        periodFrom,
        periodTo,
        { masterPlanVersionId: activePlanVersionId ?? undefined },
      );
      setCandidates(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : '路径候选加载失败');
    } finally {
      setActionLoading(false);
    }
  };

  const autoCreateSupply = async () => {
    if (!selectedPispId || !periodFrom || !periodTo) return;
    setActionLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.ontologyMaterialPlanningCreateSupplyPlan(
        selectedPispId,
        { mode: 'AUTO', periodFrom, periodTo },
        activePlanVersionId ?? undefined,
      );
      const wo = result.supplyOrderIds[0];
      setSuccess(
        wo
          ? `已创建供应计划 ${wo.supplyOrderId}（${fmtQty(wo.quantity)} 件，路径 ${result.routingId}）`
          : '供应计划已创建',
      );
      setCandidates([]);
      await load(activePlanVersionId);
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建供应计划失败');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="mrp-page">
      <PageHeader
        {...DECISION_PAGE_HEADER}
        title="供需平衡"
        subtitle="PISPP 期间桶视图 · SCN-07a~d"
      />
      {error && <StatusBanner variant="error" message={error} onDismiss={() => setError(null)} />}
      {success && (
        <StatusBanner variant="success" message={success} onDismiss={() => setSuccess(null)} />
      )}
      {report && (
        <p className="mrp-horizon">
          计划 horizon：{report.horizonStart} ~ {report.horizonEnd}
          {periodHeaders.length === 0 && ' · 未配置期间序列'}
        </p>
      )}

      <div className="mrp-layout">
        <aside className="panel mrp-kpi-panel">
          <h3 className="panel-title">PISP 物料</h3>
          <input
            className="mrp-search"
            placeholder="搜索物料…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <FilterableTable
            tableId="supply-demand-materials"
            rows={filteredMaterials}
            columns={materialColumns}
            rowKey={(row) => row.pispId ?? row.productCode}
            onRowClick={(row) => setSelectedPispId(row.pispId ?? null)}
            loading={loading}
            emptyText="无物料数据"
            unifiedChrome={false}
          />
        </aside>

        <section className="panel mrp-balance-panel">
          <div className="sdb-toolbar">
            <h3 className="panel-title">
              {selectedMaterial ? `${selectedMaterial.productCode} · PISPP` : '请选择物料'}
            </h3>
            {selectedMaterial && periodHeaders.length > 0 && (
              <div className="sdb-toolbar-actions">
                <label>
                  区间起
                  <select
                    value={periodFrom ?? ''}
                    onChange={(e) => setPeriodFrom(e.target.value)}
                  >
                    {periodHeaders.map((h) => (
                      <option key={h.periodId} value={h.periodId}>
                        {h.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  区间止
                  <select value={periodTo ?? ''} onChange={(e) => setPeriodTo(e.target.value)}>
                    {periodHeaders.map((h) => (
                      <option key={h.periodId} value={h.periodId}>
                        {h.label}
                      </option>
                    ))}
                  </select>
                </label>
                <button type="button" disabled={actionLoading} onClick={() => void loadCandidates()}>
                  预览路径
                </button>
                <button
                  type="button"
                  className="btn-primary"
                  disabled={actionLoading}
                  onClick={() => void autoCreateSupply()}
                >
                  自动创建供应
                </button>
              </div>
            )}
          </div>

          {selectedMaterial ? (
            <FilterableTable
              tableId="supply-demand-pispp"
              rows={tableRows}
              columns={metricColumns}
              rowKey={(row) => `${row.mat.productCode}-${row.metric.key}`}
              loading={loading}
              emptyText="无期间数据"
              unifiedChrome={false}
              getRowClassName={(row) => `mrp-metric-row mrp-metric-${row.metric.className}`}
            />
          ) : (
            <p className="mrp-empty-hint">左侧选择物料以查看 ENT-PISPP 期间明细。</p>
          )}

          {candidates.length > 0 && (
            <div className="sdb-candidates">
              <h4>供应路径候选（API-MAT-02）</h4>
              <ul>
                {candidates.map((c) => (
                  <li key={c.routingId}>
                    {c.routingName} · 优先级 {c.pathPriority} · EAT {c.earliestAchievableTime}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
