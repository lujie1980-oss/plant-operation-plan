import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DashboardKpiCard, type DashboardKpiId } from '../components/DashboardKpiCard';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { FilterableTable } from '../components/table/FilterableTable';
import type { DashboardSummary, DemandPoolEntry } from '../types/api';
import './DashboardPage.css';

type DashboardTab = 'fulfillment' | 'capacity' | 'material';

function orderKey(o: DemandPoolEntry) {
  return `${o.salesOrderNo}-${o.salesOrderLineNo}`;
}

function statusBadge(status: string) {
  if (status === 'SHORTAGE' || status === 'AT_RISK') return 'badge danger';
  if (status === 'ON_TRACK' || status === 'KITTING_OK' || status === 'PLANNED') return 'badge ok';
  return 'badge muted';
}

export function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [tab, setTab] = useState<DashboardTab>('fulfillment');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await api.dashboardSummary());
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selectKpi = (id: DashboardKpiId) => {
    if (id === 'fulfillment') setTab('fulfillment');
    else if (id === 'capacity') setTab('capacity');
    else if (id === 'shortage') setTab('material');
  };

  const capacityBuckets = summary?.highUtilizationBuckets ?? [];

  const tabTitle = useMemo(() => {
    switch (tab) {
      case 'fulfillment':
        return '需求满足预警';
      case 'capacity':
        return '产能预警';
      case 'material':
        return '物料预警';
    }
  }, [tab]);

  return (
    <div className="dashboard-page">
      <PageHeader
        title="首页"
        description="核心 KPI 一览 · 点击 KPI 卡片或 Tab 查看对应预警明细"
        actions={
          <button type="button" className="btn" onClick={() => void load()} disabled={loading}>
            刷新
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} />

      {summary && (
        <>
          <div className="dash-kpi-row">
            <DashboardKpiCard
              id="fulfillment"
              label="需求满足率"
              valuePct={summary.demandFulfillmentRatePct}
              subLabel={`${summary.fulfilledCount}/${summary.totalDemandLines} 行已满足`}
              active={tab === 'fulfillment'}
              color="#2563eb"
              onSelect={selectKpi}
            />
            <DashboardKpiCard
              id="capacity"
              label="产能利用率"
              valuePct={summary.capacityUtilizationPct}
              subLabel={`${summary.overloadedBucketCount} 个区间超负荷`}
              active={tab === 'capacity'}
              color="#7c3aed"
              onSelect={selectKpi}
            />
            <DashboardKpiCard
              id="shortage"
              label="缺料率"
              valuePct={summary.materialShortageRatePct}
              subLabel={`${summary.shortageCount} 行缺料风险`}
              active={tab === 'material'}
              color="#dc2626"
              onSelect={selectKpi}
            />
          </div>

          <section className="card dash-detail-panel">
            <div className="dash-tab-head">
              <h3>{tabTitle}</h3>
              <div className="dash-tabs" role="tablist">
                <button
                  type="button"
                  role="tab"
                  className={`dash-tab ${tab === 'fulfillment' ? 'is-active' : ''}`}
                  onClick={() => setTab('fulfillment')}
                >
                  需求满足预警
                </button>
                <button
                  type="button"
                  role="tab"
                  className={`dash-tab ${tab === 'capacity' ? 'is-active' : ''}`}
                  onClick={() => setTab('capacity')}
                >
                  产能预警
                </button>
                <button
                  type="button"
                  role="tab"
                  className={`dash-tab ${tab === 'material' ? 'is-active' : ''}`}
                  onClick={() => setTab('material')}
                >
                  物料预警
                </button>
              </div>
            </div>

            {tab === 'fulfillment' && (
              <div className="dash-tab-body">
                <p className="dash-detail-meta">
                  共 {summary.unfulfilledCount} 条需求未满足（状态非 ON_TRACK / PLANNED）
                </p>
                <FilterableTable
                  tableId="dashboard-unfulfilled"
                  wrapClassName="dash-detail-table-wrap ft-table-wrap"
                  rows={summary.unfulfilledDemands}
                  rowKey={(row) => orderKey(row)}
                  emptyText="当前全部需求均已满足"
                  columns={[
                    {
                      key: 'order',
                      header: '销售订单',
                      className: 'mono',
                      render: (row) => `${row.salesOrderNo}-${row.salesOrderLineNo}`,
                    },
                    { key: 'product', header: '产品', className: 'mono', render: (row) => row.productCode },
                    { key: 'qty', header: '数量', render: (row) => row.orderQty },
                    { key: 'dueDate', header: '交期', render: (row) => row.dueDate },
                    {
                      key: 'fulfillment',
                      header: '满足状态',
                      render: (row) => (
                        <span className={statusBadge(row.fulfillmentStatus)}>{row.fulfillmentStatus}</span>
                      ),
                    },
                    {
                      key: 'kitting',
                      header: '齐套',
                      render: (row) => (
                        <span className={statusBadge(row.kittingStatus)}>{row.kittingStatus}</span>
                      ),
                    },
                  ]}
                />
              </div>
            )}

            {tab === 'capacity' && (
              <div className="dash-tab-body">
                <p className="dash-detail-meta">
                  平均利用率 {summary.capacityUtilizationPct.toFixed(1)}%，以下为利用率 ≥80% 或超负荷的区间
                </p>
                <FilterableTable
                  tableId="dashboard-capacity"
                  wrapClassName="dash-detail-table-wrap ft-table-wrap"
                  rows={capacityBuckets}
                  rowKey={(row) => row.bucketId}
                  emptyText="暂无高负荷区间"
                  columns={[
                    {
                      key: 'resource',
                      header: '资源',
                      render: (b) => b.resourceLabel ?? b.resourceId,
                    },
                    { key: 'date', header: '日期', render: (b) => b.date },
                    { key: 'shift', header: '班次', render: (b) => b.shiftId },
                    { key: 'demand', header: '需求(分钟)', render: (b) => b.demandMinutes },
                    { key: 'available', header: '可用(分钟)', render: (b) => b.availableMinutes },
                    {
                      key: 'utilization',
                      header: '利用率',
                      render: (b) => `${b.utilizationPct.toFixed(1)}%`,
                    },
                    { key: 'overloaded', header: '超负荷', render: (b) => (b.overloaded ? '是' : '否') },
                  ]}
                />
              </div>
            )}

            {tab === 'material' && (
              <div className="dash-tab-body">
                <p className="dash-detail-meta">共 {summary.shortageCount} 条订单存在缺料风险（齐套 SHORTAGE）</p>
                <FilterableTable
                  tableId="dashboard-shortage"
                  wrapClassName="dash-detail-table-wrap ft-table-wrap"
                  rows={summary.shortageAffectedOrders}
                  rowKey={(row) => orderKey(row)}
                  emptyText="暂无缺料订单"
                  columns={[
                    {
                      key: 'order',
                      header: '销售订单',
                      className: 'mono',
                      render: (row) => `${row.salesOrderNo}-${row.salesOrderLineNo}`,
                    },
                    { key: 'product', header: '产品', className: 'mono', render: (row) => row.productCode },
                    { key: 'qty', header: '数量', render: (row) => row.orderQty },
                    { key: 'dueDate', header: '交期', render: (row) => row.dueDate },
                    {
                      key: 'kitting',
                      header: '齐套状态',
                      render: (row) => <span className="badge danger">{row.kittingStatus}</span>,
                    },
                    {
                      key: 'fulfillment',
                      header: '满足状态',
                      render: (row) => (
                        <span className={statusBadge(row.fulfillmentStatus)}>{row.fulfillmentStatus}</span>
                      ),
                    },
                  ]}
                />
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
