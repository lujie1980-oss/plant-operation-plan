import type { SystemParameterMd } from '../types/masterData';
import {
  BATCH_SPLIT_PARAM_IDS,
  DEFAULT_BATCH_SPLIT_CONFIG,
  type BatchRemainderMode,
  type BatchSplitConfig,
  type BatchSplitMode,
} from '../types/batchSplitConfig';
import { api } from '../api/client';

function parseMode(raw: string | undefined): BatchSplitMode {
  const upper = (raw ?? 'NONE').trim().toUpperCase();
  if (upper === 'FIXED_QTY' || upper === 'KITTING' || upper === 'AUTO') {
    return upper;
  }
  return 'NONE';
}

function parseRemainder(raw: string | undefined): BatchRemainderMode {
  const upper = (raw ?? 'SEPARATE_TAIL').trim().toUpperCase();
  if (upper === 'FLOOR' || upper === 'CEIL' || upper === 'MERGE_TAIL') {
    return upper;
  }
  return 'SEPARATE_TAIL';
}

function parseIntField(raw: string | undefined, fallback: number): number {
  if (raw == null || raw.trim() === '') {
    return fallback;
  }
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) ? Math.max(0, n) : fallback;
}

function parseBool(raw: string | undefined, fallback: boolean): boolean {
  if (raw == null || raw.trim() === '') {
    return fallback;
  }
  return raw.trim().toLowerCase() === 'true';
}

export function parseBatchSplitConfig(rows: SystemParameterMd[]): BatchSplitConfig {
  const byId = new Map(rows.map((r) => [r.paramId, r.paramValue ?? '']));
  const d = DEFAULT_BATCH_SPLIT_CONFIG;
  const minQty = Math.max(1, parseIntField(byId.get('batch_min_qty'), d.minQty));
  const maxQty = Math.max(minQty, parseIntField(byId.get('batch_max_qty'), d.maxQty));
  return {
    mode: parseMode(byId.get('batch_split_mode')),
    fixedQty: Math.max(1, parseIntField(byId.get('batch_fixed_qty'), d.fixedQty)),
    minQty,
    maxQty,
    remainderMode: parseRemainder(byId.get('batch_remainder_mode')),
    kittingCreateShortBatch: parseBool(byId.get('batch_kitting_create_short_batch'), d.kittingCreateShortBatch),
    autoOnDispatch: parseBool(byId.get('batch_auto_on_dispatch'), d.autoOnDispatch),
  };
}

const PARAM_DESCRIPTIONS: Record<(typeof BATCH_SPLIT_PARAM_IDS)[number], string> = {
  batch_split_mode: 'Batch split strategy for dispatched work orders',
  batch_fixed_qty: 'Fixed batch quantity for FIXED_QTY / AUTO baseline',
  batch_min_qty: 'Minimum batch quantity for AUTO split',
  batch_max_qty: 'Maximum batch quantity for AUTO split',
  batch_remainder_mode: 'Remainder handling for FIXED_QTY split',
  batch_kitting_create_short_batch: 'Create SHORT batch for unkitted remainder',
  batch_auto_on_dispatch: 'Auto split when work order is dispatched',
};

function serializeConfig(config: BatchSplitConfig): Record<(typeof BATCH_SPLIT_PARAM_IDS)[number], string> {
  return {
    batch_split_mode: config.mode,
    batch_fixed_qty: String(Math.max(1, config.fixedQty)),
    batch_min_qty: String(Math.max(1, config.minQty)),
    batch_max_qty: String(Math.max(config.minQty, config.maxQty)),
    batch_remainder_mode: config.remainderMode,
    batch_kitting_create_short_batch: config.kittingCreateShortBatch ? 'true' : 'false',
    batch_auto_on_dispatch: config.autoOnDispatch ? 'true' : 'false',
  };
}

export async function loadBatchSplitParameterRows(): Promise<SystemParameterMd[]> {
  const rows = await api.masterData.parameters.list();
  return rows.filter((r) =>
    (BATCH_SPLIT_PARAM_IDS as readonly string[]).includes(r.paramId),
  );
}

export async function saveBatchSplitConfig(
  config: BatchSplitConfig,
  existingRows: SystemParameterMd[],
): Promise<void> {
  const values = serializeConfig(config);
  const byId = new Map(existingRows.map((r) => [r.paramId, r]));
  for (const paramId of BATCH_SPLIT_PARAM_IDS) {
    const existing = byId.get(paramId);
    const payload: SystemParameterMd = {
      id: existing?.id ?? null,
      paramId,
      paramValue: values[paramId],
      description: existing?.description ?? PARAM_DESCRIPTIONS[paramId],
    };
    await api.masterData.parameters.save(payload);
  }
}
