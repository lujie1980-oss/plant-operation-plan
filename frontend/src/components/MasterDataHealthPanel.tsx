import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { FilterableTable } from '../components/table/FilterableTable';
import type { MasterDataValidationReportMd } from '../types/masterData';
import type { MasterDataTableFocus } from '../utils/masterDataFocus';
import {
  HEALTH_CATEGORIES,
  blockedToHealthItems,
  buildCategoryCounts,
  ruleLabel,
  issueReason,
  toHealthListItem,
  type HealthCategoryId,
} from '../utils/masterDataHealthNav';
import './MasterDataHealthPanel.css';

interface MasterDataHealthPanelProps {
  dataRevision?: number;
  onNavigateToRecord: (focus: MasterDataTableFocus) => void;
}

export function MasterDataHealthPanel({ dataRevision = 0, onNavigateToRecord }: MasterDataHealthPanelProps) {
  const [report, setReport] = useState<MasterDataValidationReportMd | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedCategoryId, setSelectedCategoryId] = useState<HealthCategoryId>('boms');

  const load = useCallback(() => {
    setLoading(true);
    setErr(null);
    return api.masterData
      .validation()
      .then(setReport)
      .catch((e: unknown) => {
        setErr(e instanceof Error ? e.message : String(e));
        setReport(null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    void load();
  }, [load, dataRevision]);

  const allItems = useMemo(() => {
    if (!report) return [];
    const issues = [
      ...report.errors.map(toHealthListItem),
      ...report.warnings.map(toHealthListItem),
      ...blockedToHealthItems(report.blockedSalesOrderLines),
    ];
    return issues;
  }, [report]);

  const countsByCategory = useMemo(() => buildCategoryCounts(allItems), [allItems]);

  const categoryItems = useMemo(() => {
    const cat = HEALTH_CATEGORIES.find((c) => c.id === selectedCategoryId);
    if (!cat) return [];
    const types = new Set(cat.entityTypes);
    return allItems.filter((item) => types.has(item.entityType));
  }, [allItems, selectedCategoryId]);

  const totals = useMemo(() => {
    let errors = 0;
    let warnings = 0;
    for (const item of allItems) {
      if (item.severity === 'ERROR') errors += 1;
      else warnings += 1;
    }
    return { errors, warnings };
  }, [allItems]);

  useEffect(() => {
    if (!report) return;
    const firstWithIssues = HEALTH_CATEGORIES.find((cat) => {
      const c = countsByCategory.get(cat.id);
      return c && (c.errors > 0 || c.warnings > 0);
    });
    setSelectedCategoryId(firstWithIssues?.id ?? 'materials');
  }, [report]);

  const level =
    totals.errors > 0 ? 'mdh-danger' : totals.warnings > 0 ? 'mdh-warn' : 'mdh-ok';

  const selectedLabel = HEALTH_CATEGORIES.find((c) => c.id === selectedCategoryId)?.label ?? '';

  return (
    <div className="mdh-panel">
      <div className="mdh-toolbar">
        <p className="mdh-desc">
          按数据类别查看错误与预警；点击条目可跳转到对应页签并定位数据行。
        </p>
        <button type="button" className="btn btn-secondary mdh-refresh" disabled={loading} onClick={() => void load()}>
          {loading ? '校验中…' : '重新校验'}
        </button>
      </div>

      {err && <div className="mdh-alert mdh-alert-error">主数据校验获取失败：{err}</div>}

      {!err && report && (
        <>
          <div className={`mdh-summary ${level}`}>
            <div className="mdh-stat">
              <span className="mdh-stat-value">{totals.errors}</span>
              <span className="mdh-stat-label">错误</span>
            </div>
            <div className="mdh-stat">
              <span className="mdh-stat-value">{totals.warnings}</span>
              <span className="mdh-stat-label">预警</span>
            </div>
          </div>

          <div className="mdh-split">
            <aside className="mdh-categories" aria-label="数据类别">
              <h3 className="mdh-categories-title">数据类别</h3>
              <ul className="mdh-category-list">
                {HEALTH_CATEGORIES.map((cat) => {
                  const counts = countsByCategory.get(cat.id) ?? { errors: 0, warnings: 0 };
                  const active = cat.id === selectedCategoryId;
                  const hasIssue = counts.errors > 0 || counts.warnings > 0;
                  return (
                    <li key={cat.id}>
                      <button
                        type="button"
                        className={`mdh-category-btn ${active ? 'is-active' : ''} ${hasIssue ? 'has-issues' : ''}`}
                        onClick={() => setSelectedCategoryId(cat.id)}
                      >
                        <span className="mdh-category-label">{cat.label}</span>
                        <span className="mdh-category-badges">
                          {counts.errors > 0 && (
                            <span className="mdh-badge mdh-badge-error" title="错误">
                              {counts.errors}
                            </span>
                          )}
                          {counts.warnings > 0 && (
                            <span className="mdh-badge mdh-badge-warn" title="预警">
                              {counts.warnings}
                            </span>
                          )}
                          {!hasIssue && <span className="mdh-badge mdh-badge-ok">0</span>}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </aside>

            <section className="mdh-detail" aria-label="异常清单">
              <header className="mdh-detail-head">
                <h3>{selectedLabel}</h3>
                <span className="mdh-detail-count">{categoryItems.length} 条</span>
              </header>
              {categoryItems.length === 0 ? (
                <p className="mdh-empty">该类别暂无错误或预警</p>
              ) : (
                <div className="mdh-detail-table-wrap">
                  <FilterableTable
                    tableId="master-data-health-issues"
                    tableClassName="mdh-table mdh-issue-table"
                    wrapClassName="mdh-table-wrap ft-table-wrap"
                    rows={categoryItems}
                    rowKey={(item) => item.id}
                    columns={[
                      {
                        key: 'severity',
                        header: '级别',
                        render: (item) => (
                          <span className={`mdh-pill mdh-pill-${item.severity === 'ERROR' ? 'error' : 'warn'}`}>
                            {item.severity === 'ERROR' ? '错误' : '预警'}
                          </span>
                        ),
                      },
                      {
                        key: 'ruleId',
                        header: '类型',
                        className: 'mdh-rule',
                        render: (item) => ruleLabel(item.ruleId),
                      },
                      {
                        key: 'entityKey',
                        header: '数据标识',
                        className: 'mdh-entity',
                        render: (item) => item.entityKey,
                      },
                      { key: 'reason', header: '说明', render: (item) => issueReason(item) },
                      {
                        key: 'action',
                        header: '',
                        filterable: false,
                        className: 'mdh-action-cell',
                        render: (item) =>
                          item.navigable ? (
                            <button
                              type="button"
                              className="btn md-btn mdh-goto-btn"
                              onClick={() => item.focus && onNavigateToRecord(item.focus)}
                            >
                              定位
                            </button>
                          ) : (
                            <span className="mdh-muted">—</span>
                          ),
                      },
                    ]}
                    getRowClassName={(item) => (item.navigable ? 'mdh-issue-clickable' : '')}
                  />
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
