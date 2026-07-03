import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { slittingClient } from '../../api/slittingClient';
import { PageHeader } from '../../components/PageHeader';
import { StatusBanner } from '../../components/StatusBanner';
import { slittingPlanStatusClass } from '../../utils/slitting/display';
import type { ChildSlittingOrder, MasterRoll, SlittingPlanSummary } from '../../types/slitting';
import '../../components/slitting/slitting.css';

export function SlittingPlansPage() {
  const navigate = useNavigate();
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
      const created = await slittingClient.createPlan({
        name,
        masterRollCodes: selectedMasters,
        childOrderCodes: selectedOrders,
      });
      await refresh();
      setName('新方案');
      setSelectedMasters([]);
      setSelectedOrders([]);
      navigate(`/slitting/workbench?plan=${encodeURIComponent(created.planVersionId)}`);
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
    <div className="page slitting-module">
      <PageHeader title="分切方案" description="创建方案并进入工作台求解" />
      <StatusBanner error={err} />
      <section className="card">
        <h2>新建方案</h2>
        <div className="slitting-plans-create">
          <label>
            名称
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <div>
            <strong>母卷</strong>
            <div className="slitting-check-grid">
              {masterRolls.map((m) => (
                <label key={m.rollCode} className="slitting-check-item">
                  <input
                    type="checkbox"
                    checked={selectedMasters.includes(m.rollCode)}
                    onChange={() => toggle(selectedMasters, m.rollCode, setSelectedMasters)}
                  />
                  <span>
                    {m.rollCode}
                    <br />
                    <small>
                      {m.widthMm}×{m.lengthMm} mm
                    </small>
                  </span>
                </label>
              ))}
            </div>
          </div>
          <div>
            <strong>子订单</strong>
            <div className="slitting-check-grid">
              {childOrders.map((o) => (
                <label key={o.orderCode} className="slitting-check-item">
                  <input
                    type="checkbox"
                    checked={selectedOrders.includes(o.orderCode)}
                    onChange={() => toggle(selectedOrders, o.orderCode, setSelectedOrders)}
                  />
                  <span>
                    {o.orderCode} ×{o.quantity}
                    <br />
                    <small>
                      {o.widthMm}×{o.lengthMm} mm
                    </small>
                  </span>
                </label>
              ))}
            </div>
          </div>
          <button type="button" className="btn primary slitting-btn-accent" disabled={loading} onClick={() => void createPlan()}>
            创建并打开工作台
          </button>
        </div>
      </section>
      <section className="card">
        <h2>已有方案</h2>
        {plans.length === 0 ? (
          <p className="slitting-props-empty">暂无方案，请先创建。</p>
        ) : (
          <table className="slitting-plans-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>状态</th>
                <th>利用率</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {plans.map((p) => {
                const util = p.utilizationPct ?? 0;
                return (
                  <tr key={p.planVersionId}>
                    <td>{p.name}</td>
                    <td>
                      <span className={slittingPlanStatusClass(p.status)}>{p.status}</span>
                    </td>
                    <td>
                      {p.utilizationPct != null ? (
                        <>
                          <span className="slitting-util-bar" title={`${util}%`}>
                            <span style={{ width: `${Math.min(100, util)}%` }} />
                          </span>
                          {util.toFixed(1)}%
                        </>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <Link to={`/slitting/workbench?plan=${encodeURIComponent(p.planVersionId)}`}>工作台</Link>
                      {' · '}
                      <Link
                        to={`/slitting/workbench?plan=${encodeURIComponent(p.planVersionId)}&solve=1`}
                      >
                        求解并打开
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
