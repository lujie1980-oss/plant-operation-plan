import { useState } from 'react';
import { api } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import { usePlan } from '../context/PlanContext';
import type { DispatchResult, RescheduleResult } from '../types/api';

const EVENT_TYPES = ['MES_DELAY', 'MES_SCRAP', 'NEW_ORDER', 'MATERIAL_SHORTAGE'];

export function ExecutionPage() {
  const { detailSchedule } = usePlan();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dispatch, setDispatch] = useState<DispatchResult | null>(null);
  const [reschedule, setReschedule] = useState<RescheduleResult | null>(null);
  const [eventType, setEventType] = useState(EVENT_TYPES[0]);
  const [payloadJson, setPayloadJson] = useState('{"workOrderNo":"WO-001","delayMinutes":60}');

  const doDispatch = async () => {
    const id = detailSchedule?.planVersionId;
    if (!id) {
      setError('请先完成详细排程');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setDispatch(await api.dispatch(id));
    } catch (e) {
      setError(e instanceof Error ? e.message : '下发失败');
    } finally {
      setLoading(false);
    }
  };

  const sendEvent = async () => {
    setLoading(true);
    setError(null);
    try {
      const payload = JSON.parse(payloadJson) as Record<string, unknown>;
      setReschedule(await api.handleEvent(eventType, payload));
    } catch (e) {
      setError(e instanceof Error ? e.message : '事件处理失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="S06 执行闭环"
        description="计划下发、MES 反馈事件与 R0–R3 重排"
        actions={
          <button type="button" className="btn primary" onClick={() => void doDispatch()} disabled={loading}>
            下发计划
          </button>
        }
      />
      <StatusBanner loading={loading} error={error} />
      <section className="card">
        <h3>下发结果</h3>
        {dispatch ? (
          <ul className="info-list">
            <li>版本：{dispatch.planVersionId}</li>
            <li>状态：{dispatch.status}</li>
            <li>时间：{dispatch.dispatchedTs}</li>
          </ul>
        ) : (
          <p className="empty">尚未下发</p>
        )}
      </section>
      <section className="card">
        <h3>模拟事件 / 重排</h3>
        <div className="form-row">
          <label>
            事件类型
            <select value={eventType} onChange={(e) => setEventType(e.target.value)}>
              {EVENT_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="form-row">
          <label>
            Payload (JSON)
            <textarea
              className="textarea"
              rows={4}
              value={payloadJson}
              onChange={(e) => setPayloadJson(e.target.value)}
            />
          </label>
        </div>
        <button type="button" className="btn" onClick={() => void sendEvent()} disabled={loading}>
          提交事件
        </button>
        {reschedule && (
          <ul className="info-list" style={{ marginTop: '1rem' }}>
            <li>重排级别：{reschedule.level}</li>
            <li>主计划版本：{reschedule.masterPlanVersionId ?? '—'}</li>
            <li>排程版本：{reschedule.detailScheduleVersionId ?? '—'}</li>
            <li>影响订单：{reschedule.impactedOrders.join(', ') || '—'}</li>
          </ul>
        )}
      </section>
    </>
  );
}
