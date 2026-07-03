import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { MaterialLeadTimeMd } from '../types/masterData';
import './MaterialLeadTimeRuleCallout.css';

type Props = {
  /** Bump when table saves/deletes to refresh default row display */
  dataRevision?: number;
};

const DEFAULT_WILDCARD = '*';

export function MaterialLeadTimeRuleCallout({ dataRevision = 0 }: Props) {
  const [rows, setRows] = useState<MaterialLeadTimeMd[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await api.masterData.materialLeadTime.list());
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, dataRevision]);

  const defaultRow = rows.find((r) => r.productCode.trim() === DEFAULT_WILDCARD);
  const hasDefault = defaultRow != null;

  return (
    <aside className="br-mlt-callout" aria-label="默认最长采购周期说明">
      <div className="br-mlt-callout-head">
        <span className="br-mlt-callout-badge">RULE-MRP-04</span>
        <strong className="br-mlt-callout-title">默认最长采购周期</strong>
      </div>
      <p className="br-mlt-callout-text">
        物料短缺时，系统用<strong>最长采购周期</strong>推算组件 Supply 的<strong>最晚可用日</strong>（
        <code>needDate − 采购周期</code>），用于 RULE-PLAN-01 物料短缺豁免与 MRP 展示。取值顺序：精确物料行 →
        下方「默认」行（物料 <code>*</code>）→ 系统参数 <code>default_procurement_lead_time_days</code>。
      </p>
      <dl className="br-mlt-callout-meta">
        <div>
          <dt>默认行标识</dt>
          <dd>
            物料编码填 <code className="br-mlt-wildcard">{DEFAULT_WILDCARD}</code>（仅一行）
          </dd>
        </div>
        <div>
          <dt>字段</dt>
          <dd>最长采购周期(天)</dd>
        </div>
      </dl>
      {!loading && (
        <div className={`br-mlt-callout-status ${hasDefault ? 'is-ok' : 'is-warn'}`}>
          {hasDefault ? (
            <>
              已配置默认最长采购周期：<strong>{defaultRow.leadTimeDays} 天</strong>
            </>
          ) : (
              <>
                尚未配置物料为 <code>{DEFAULT_WILDCARD}</code> 的默认行；当前将回退到系统参数默认提前期。请在下表新增一行并填写{' '}
                <code>{DEFAULT_WILDCARD}</code>。
              </>
          )}
        </div>
      )}
    </aside>
  );
}
