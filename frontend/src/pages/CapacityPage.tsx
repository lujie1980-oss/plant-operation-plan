import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { usePlan } from '../context/PlanContext';
import { CapacityUtilizationGantt } from '../components/CapacityUtilizationGantt';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { VerticalResizeSplit } from '../components/VerticalResizeSplit';
import { FilterableTable } from '../components/table/FilterableTable';
import type { CapacityAnalysis, DemandPoolKpi, LoadBucket } from '../types/api';
import { formatBucketColumnLabel } from '../utils/capacityUtilization';
import './CapacityPage.css';

export function CapacityPage({ embedded = false }: { embedded?: boolean }) {
  const { activePlanVersionId, selectedScenarioId, scenarios } = usePlan();
  const [data, setData] = useState<CapacityAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedBucket, setSelectedBucket] = useState<LoadBucket | null>(null);

  const currentScenario = scenarios.find((s) => s.scenarioId === selectedScenarioId);

  const analyze = useCallback(async (versionId: string | null | undefined) => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.analyzeCapacity(versionId ?? undefined);
      setData(result);
      if (result.loadBuckets.length > 0) {
        setSelectedBucket((prev) => {
          if (prev) {
            const match = result.loadBuckets.find((b) => b.bucketId === prev.bucketId);
            return match ?? result.loadBuckets[0];
          }
          return result.loadBuckets[0];
        });
      } else {
        setSelectedBucket(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '分析失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void analyze(activePlanVersionId);
  }, [activePlanVersionId, analyze]);

  const kpis: DemandPoolKpi[] = data?.kpis ?? [];
  const buckets = data?.loadBuckets ?? [];
  const workOrders = selectedBucket?.workOrders ?? [];

  const selectionTitle = useMemo(() => {
    if (!selectedBucket) return '区间排产工单';
    const locked = selectedBucket.feedbackLockedMinutes ?? 0;
    const lockedHint = locked > 0 ? ` · 反馈锁定 ${locked} 分` : '';
    return `${selectedBucket.resourceLabel} · ${formatBucketColumnLabel(selectedBucket.date, selectedBucket.shiftId)} · 利用率 ${selectedBucket.utilizationPct}%${lockedHint}`;
  }, [selectedBucket]);

  const scenarioHint = currentScenario
    ? `场景「${currentScenario.name}」${activePlanVersionId ? ` · 生效版本 ${activePlanVersionId}` : ' · 尚未运行'}`
    : '请先在顶部选择计划场景';

  return (
    <div className={`capacity-page ${embedded ? 'capacity-page--embedded' : ''}`.trim()}>
      {!embedded && (
        <PageHeader
          title="产能平衡"
          showScenarioSelector
          description={`机台×班次产能利用率热力甘特；紫色斜纹为排程反馈已锁定产能。${scenarioHint}`}
        />
      )}
      {!embedded && <StatusBanner loading={loading} error={error} />}

      <div className="capacity-layout">
        <aside className="capacity-kpi-panel card">
          <h3 className="panel-title">关键 KPI</h3>
          <div className="panel-scroll kpi-scroll">
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
            {kpis.length === 0 && !loading && (
              <p className="empty">{activePlanVersionId ? '暂无 KPI' : '请先选择场景并运行计划'}</p>
            )}
          </div>
        </aside>

        <div className="capacity-main">
          <VerticalResizeSplit
            className="capacity-right-split"
            storageKey="capacity-s03-split-ratio"
            minTopRatio={0.35}
            maxTopRatio={0.8}
            top={
              <section className="capacity-gantt-panel card">
                <h3 className="panel-title">产能利用率甘特</h3>
                <div className="capacity-gantt-body">
                  <CapacityUtilizationGantt
                    buckets={buckets}
                    selectedBucketId={selectedBucket?.bucketId ?? null}
                    onSelectBucket={setSelectedBucket}
                  />
                </div>
              </section>
            }
            bottom={
              <section className="capacity-wo-panel card">
                <h3 className="panel-title">{selectionTitle}</h3>
                <div className="panel-scroll">
                  {selectedBucket ? (
                    <FilterableTable
                      tableId="capacity-bucket-work-orders"
                      tableClassName="capacity-wo-table"
                      wrapClassName="ft-table-wrap"
                      rows={workOrders}
                      rowKey={(wo) => wo.workOrderNo}
                      emptyText="该区间暂无排产工单"
                      columns={[
                        { key: 'workOrderNo', header: '工单', className: 'mono', render: (wo) => wo.workOrderNo },
                        {
                          key: 'salesOrder',
                          header: '销售订单',
                          render: (wo) => `${wo.salesOrderNo}-${wo.salesOrderLineNo}`,
                        },
                        { key: 'productCode', header: '产品', render: (wo) => wo.productCode },
                        { key: 'quantity', header: '数量', render: (wo) => wo.quantity },
                        { key: 'loadMinutes', header: '负荷(分)', render: (wo) => wo.loadMinutes },
                        {
                          key: 'feedbackLocked',
                          header: '锁定',
                          render: (wo) =>
                            wo.feedbackLocked ? <span className="cap-lock-tag">反馈锁定</span> : '—',
                        },
                        {
                          key: 'scheduleSource',
                          header: '来源',
                          render: (wo) => <span className="cap-source-tag">{wo.scheduleSource}</span>,
                        },
                      ]}
                    />
                  ) : (
                    <p className="empty">
                      {activePlanVersionId ? '请在上方甘特图中选择一个机台区间' : '请先在顶部选择计划场景并运行'}
                    </p>
                  )}
                </div>
              </section>
            }
          />
        </div>
      </div>
    </div>
  );
}
