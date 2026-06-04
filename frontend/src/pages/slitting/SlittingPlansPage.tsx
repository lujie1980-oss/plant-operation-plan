import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { slittingClient } from '../../api/slittingClient';
import { PageHeader } from '../../components/PageHeader';
import { StatusBanner } from '../../components/StatusBanner';
import type { ChildSlittingOrder, MasterRoll, SlittingPlanSummary } from '../../types/slitting';

export function SlittingPlansPage() {
  const [plans, setPlans] = useState<SlittingPlanSummary[]>([]);
  const [masterRolls, setMasterRolls] = useState<MasterRoll[]>([]);
  const [childOrders, setChildOrders] = useState<ChildSlittingOrder[]>([]);
  const [selectedMasters, setSelectedMasters] = useState<string[]>([]);
  const [selectedOrders, setSelectedOrders] = useState<string[]>([]);
  const [name, setName] = useState('新方案');
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const [p, m, c] = await Promise.all([
      slittingClient.listPlans(),
      slittingClient.listMasterRolls(),
      slittingClient.listChildOrders(),
    ]);
    setPlans(p);
    setMasterRolls(m);
    setChildOrders(c);
  }, []);

  useEffect(() => {
    void refresh().catch((e: unknown) => setErr(e instanceof Error ? e.message : String(e)));
  }, [refresh]);

  const createPlan = async () => {
    setLoading(true);
    setErr(null);
    try {
      await slittingClient.createPlan({
        name,
        masterRollCodes: selectedMasters,
        childOrderCodes: selectedOrders,
      });
      await refresh();
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  const toggle = (list: string[], code: string, setter: (v: string[]) => void) => {
    setter(list.includes(code) ? list.filter((x) => x !== code) : [...list, code]);
  };

  return (
    <div className="page">
      <PageHeader title="分切方案" description="创建方案并进入工作台求解" />
      <StatusBanner error={err} />
      <section className="card">
        <h2>新建方案</h2>
        <label>
          名称
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <div>
          <strong>母卷</strong>
          {masterRolls.map((m) => (
            <label key={m.rollCode} style={{ display: 'block' }}>
              <input
                type="checkbox"
                checked={selectedMasters.includes(m.rollCode)}
                onChange={() => toggle(selectedMasters, m.rollCode, setSelectedMasters)}
              />
              {m.rollCode} ({m.widthMm}×{m.lengthMm})
            </label>
          ))}
        </div>
        <div>
          <strong>子订单</strong>
          {childOrders.map((o) => (
            <label key={o.orderCode} style={{ display: 'block' }}>
              <input
                type="checkbox"
                checked={selectedOrders.includes(o.orderCode)}
                onChange={() => toggle(selectedOrders, o.orderCode, setSelectedOrders)}
              />
              {o.orderCode} ({o.widthMm}×{o.lengthMm}) ×{o.quantity}
            </label>
          ))}
        </div>
        <button type="button" disabled={loading} onClick={() => void createPlan()}>
          创建
        </button>
      </section>
      <section className="card">
        <h2>已有方案</h2>
        <ul>
          {plans.map((p) => (
            <li key={p.planVersionId}>
              {p.name} — {p.status}
              {p.utilizationPct != null ? ` · ${p.utilizationPct}%` : ''}
              {' · '}
              <Link to="/slitting/workbench">工作台</Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
