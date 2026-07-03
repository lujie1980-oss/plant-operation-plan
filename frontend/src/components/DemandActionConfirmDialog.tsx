import { useEffect, useState } from 'react';

import { api } from '../api/client';

import type { DemandPoolEntry } from '../types/api';

import type { OrderDemandActionId } from '../types/demandActions';

import type { OrderFulfillmentChain } from '../types/api';

import {

  demandActionConfirmCopy,

  orderSummaryLine,

  suggestPromiseDate,

} from '../utils/demandActionConfirm';

import './DemandActionConfirmDialog.css';



export function DemandActionConfirmDialog({

  row,

  deliveryId,

  action,

  masterPlanVersionId,

  onConfirm,

  onCancel,

  busy,

}: {

  row: DemandPoolEntry;

  deliveryId: string;

  action: OrderDemandActionId;

  masterPlanVersionId?: string | null;

  onConfirm: () => void;

  onCancel: () => void;

  busy?: boolean;

}) {

  const copy = demandActionConfirmCopy(action);

  const [previewLoading, setPreviewLoading] = useState(action === 'CONFIRM_PROMISE_DATE');

  const [previewError, setPreviewError] = useState<string | null>(null);

  const [chain, setChain] = useState<OrderFulfillmentChain | null>(null);



  useEffect(() => {

    if (action !== 'CONFIRM_PROMISE_DATE') return;

    let cancelled = false;

    setPreviewLoading(true);

    setPreviewError(null);

    setChain(null);

    void api

      .ontologyPromiseDatePreview(deliveryId, masterPlanVersionId ?? undefined)

      .then((preview) => {

        if (cancelled) return;

        setChain(preview.fulfillmentChain);

      })

      .catch((e) => {

        if (cancelled) return;

        setPreviewError(e instanceof Error ? e.message : '有限能力预览加载失败');

      })

      .finally(() => {

        if (!cancelled) setPreviewLoading(false);

      });

    return () => {

      cancelled = true;

    };

  }, [action, deliveryId, masterPlanVersionId]);



  const suggestedDate = action === 'CONFIRM_PROMISE_DATE' ? suggestPromiseDate(chain) : null;

  const overallStatus = chain?.overallStatus ?? null;

  const isBlocked = overallStatus === 'BLOCKED';

  const lateVsDue =

    suggestedDate && row.dueDate ? suggestedDate > row.dueDate.slice(0, 10) : false;

  const confirmDisabled =

    busy ||

    previewLoading ||

    (action === 'CONFIRM_PROMISE_DATE' &&

      (Boolean(previewError) || isBlocked || !suggestedDate));



  return (

    <div className="demand-confirm-backdrop" role="presentation" onClick={onCancel}>

      <div

        className="demand-confirm-modal"

        role="dialog"

        aria-modal="true"

        aria-labelledby="demand-confirm-title"

        onClick={(e) => e.stopPropagation()}

      >

        <h4 id="demand-confirm-title">{copy.title}</h4>

        <p className="demand-confirm-order">{orderSummaryLine(row)}</p>

        <p className="demand-confirm-desc">{copy.description}</p>



        {action === 'CONFIRM_PROMISE_DATE' && (

          <div className="demand-confirm-preview">

            {previewLoading && <p className="demand-confirm-muted">正在加载有限能力满足链…</p>}

            {!previewLoading && previewError && (

              <p className="demand-confirm-error">{previewError}</p>

            )}

            {!previewLoading && !previewError && chain && (

              <>

                <dl className="demand-confirm-facts">

                  <div>

                    <dt>客户交期</dt>

                    <dd>{row.dueDate ?? '—'}</dd>

                  </div>

                  <div>

                    <dt>建议承诺交期</dt>

                    <dd>{suggestedDate ?? '—'}</dd>

                  </div>

                  <div>

                    <dt>满足链状态</dt>

                    <dd>{overallStatus ?? '—'}</dd>

                  </div>

                  {row.promiseDate && (

                    <div>

                      <dt>当前承诺交期</dt>

                      <dd>{row.promiseDate}</dd>

                    </div>

                  )}

                </dl>

                {isBlocked && (

                  <p className="demand-confirm-error">

                    满足链状态为 BLOCKED，无法自动确认承诺交期。请先处理阻塞项后再试。

                  </p>

                )}

                {!isBlocked && !suggestedDate && (

                  <p className="demand-confirm-error">

                    无法从满足链推算承诺交期，请先执行有限能力计划或检查工单与工艺数据。

                  </p>

                )}

                {!isBlocked && suggestedDate && lateVsDue && (

                  <p className="demand-confirm-warn">

                    建议承诺交期晚于客户交期，确认后将标记为延期承诺。

                  </p>

                )}

              </>

            )}

          </div>

        )}



        <div className="demand-confirm-actions">

          <button type="button" className="btn" onClick={onCancel} disabled={busy}>

            取消

          </button>

          <button

            type="button"

            className={`btn ${copy.destructive ? 'danger' : 'primary'}`.trim()}

            onClick={onConfirm}

            disabled={confirmDisabled}

          >

            {busy ? '执行中…' : copy.confirmLabel}

          </button>

        </div>

      </div>

    </div>

  );

}

