import { Link } from 'react-router-dom';
import type { OrderFulfillmentChain } from '../types/api';
import { extractFulfillmentRootCauses } from '../utils/fulfillmentRootCauses';
import {
  capacityAnalysisLink,
  materialPlanningLink,
} from '../utils/masterPlanDeepLink';
import './FulfillmentRootCausePanel.css';

export interface FulfillmentRootCausePanelProps {
  chain: OrderFulfillmentChain | null;
}

export function FulfillmentRootCausePanel({ chain }: FulfillmentRootCausePanelProps) {
  const causes = extractFulfillmentRootCauses(chain);
  const capacityCauses = causes.filter((c) => c.kind === 'capacity');
  const materialCauses = causes.filter((c) => c.kind === 'material');

  if (!chain || causes.length === 0) {
    return null;
  }

  return (
    <section className="fulfillment-root-cause-panel" aria-label="不满足根因">
      <h4 className="fulfillment-root-cause-title">根因与跳转</h4>
      <p className="fulfillment-root-cause-hint muted-text">
        根据满足链产能负载与缺料信号识别；点击可深链到对应分析页并自动筛选。
      </p>
      {capacityCauses.length > 0 && (
        <div className="fulfillment-root-cause-group">
          <span className="fulfillment-root-cause-label">关键机台产能</span>
          <ul className="fulfillment-root-cause-list">
            {capacityCauses.map((cause) => (
              <li key={cause.id}>
                <Link
                  to={capacityAnalysisLink(cause.resourceId!)}
                  className="fulfillment-root-cause-link"
                >
                  查看产能计划 · {cause.label}
                  {cause.utilizationPct != null && (
                    <small>（{cause.utilizationPct.toFixed(0)}%）</small>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
      {materialCauses.length > 0 && (
        <div className="fulfillment-root-cause-group">
          <span className="fulfillment-root-cause-label">关键物料短缺</span>
          <ul className="fulfillment-root-cause-list">
            {materialCauses.map((cause) => (
              <li key={cause.id}>
                <Link
                  to={materialPlanningLink(cause.pispId ?? cause.productCode!)}
                  className="fulfillment-root-cause-link"
                >
                  查看物料计划 · {cause.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
