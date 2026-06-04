import { useEffect, useState } from 'react';
import { slittingClient } from '../../api/slittingClient';
import { PageHeader } from '../../components/PageHeader';
import { StatusBanner } from '../../components/StatusBanner';
import type { ChildSlittingOrder, IntermediateRollCatalog, MasterRoll } from '../../types/slitting';

export function SlittingMasterDataPage() {
  const [masters, setMasters] = useState<MasterRoll[]>([]);
  const [orders, setOrders] = useState<ChildSlittingOrder[]>([]);
  const [catalog, setCatalog] = useState<IntermediateRollCatalog[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    void Promise.all([
      slittingClient.listMasterRolls(),
      slittingClient.listChildOrders(),
      slittingClient.listCatalog(),
    ])
      .then(([m, o, c]) => {
        setMasters(m);
        setOrders(o);
        setCatalog(c);
      })
      .catch((e: unknown) => setErr(e instanceof Error ? e.message : String(e)));
  }, []);

  return (
    <div className="page">
      <PageHeader title="分切主数据" description="母卷、中间卷规格、子订单（含 APS 外键预留字段）" />
      <StatusBanner error={err} />
      <section className="card">
        <h2>母卷</h2>
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>宽×长</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {masters.map((m) => (
              <tr key={m.rollCode}>
                <td>{m.rollCode}</td>
                <td>
                  {m.widthMm}×{m.lengthMm}
                </td>
                <td>{m.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="card">
        <h2>标准中间卷</h2>
        <table>
          <thead>
            <tr>
              <th>规格</th>
              <th>宽×长</th>
              <th>切型</th>
            </tr>
          </thead>
          <tbody>
            {catalog.map((c) => (
              <tr key={c.specCode}>
                <td>{c.specCode}</td>
                <td>
                  {c.widthMm}×{c.lengthMm}
                </td>
                <td>{c.cuttingMethod}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="card">
        <h2>子订单</h2>
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>宽×长</th>
              <th>数量</th>
              <th>销售订单</th>
              <th>工单</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.orderCode}>
                <td>{o.orderCode}</td>
                <td>
                  {o.widthMm}×{o.lengthMm}
                </td>
                <td>{o.quantity}</td>
                <td>
                  {o.salesOrderNo
                    ? `${o.salesOrderNo}-${o.salesOrderLineNo ?? ''}`
                    : '—'}
                </td>
                <td>{o.workOrderNo ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
