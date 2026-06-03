import {
  DEFAULT_SCHEDULE_CONTRACT,
  type ScheduleContractConfig,
  type ScheduleContractPenaltyMode,
} from '../types/scheduleContract';

export const SCHEDULE_CONTRACT_PARAM_ID = 'detail_schedule_contract';

function parseMode(raw: unknown, fallback: ScheduleContractPenaltyMode): ScheduleContractPenaltyMode {
  if (typeof raw !== 'string' || raw.trim() === '') {
    return fallback;
  }
  const upper = raw.trim().toUpperCase();
  if (upper === 'LINEAR' || upper === 'QUADRATIC' || upper === 'CAPPED') {
    return upper;
  }
  return fallback;
}

function parseBoolField(raw: unknown, fallback: boolean): boolean {
  if (typeof raw === 'boolean') {
    return raw;
  }
  if (typeof raw === 'string') {
    const lower = raw.trim().toLowerCase();
    if (lower === 'true' || lower === '1') {
      return true;
    }
    if (lower === 'false' || lower === '0') {
      return false;
    }
  }
  return fallback;
}

function parseIntField(raw: unknown, fallback: number): number {
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return Math.max(0, Math.trunc(raw));
  }
  if (typeof raw === 'string' && raw.trim() !== '') {
    const n = Number.parseInt(raw, 10);
    if (Number.isFinite(n)) {
      return Math.max(0, n);
    }
  }
  return fallback;
}

export function parseScheduleContractJson(raw: string | null | undefined): ScheduleContractConfig {
  if (!raw || raw.trim() === '') {
    return { ...DEFAULT_SCHEDULE_CONTRACT };
  }
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    return {
      weightDue: parseIntField(obj.weight_due, DEFAULT_SCHEDULE_CONTRACT.weightDue),
      enableMpTarget: parseBoolField(obj.enable_mp_target, DEFAULT_SCHEDULE_CONTRACT.enableMpTarget),
      enableMpContractStartWait: parseBoolField(
        obj.enable_mp_contract_start_wait,
        DEFAULT_SCHEDULE_CONTRACT.enableMpContractStartWait,
      ),
      weightMpLate: parseIntField(obj.weight_mp_late, DEFAULT_SCHEDULE_CONTRACT.weightMpLate),
      weightMpEarly: parseIntField(obj.weight_mp_early, DEFAULT_SCHEDULE_CONTRACT.weightMpEarly),
      mpLateMode: parseMode(obj.mp_late_mode, DEFAULT_SCHEDULE_CONTRACT.mpLateMode),
      mpEarlyMode: parseMode(obj.mp_early_mode, DEFAULT_SCHEDULE_CONTRACT.mpEarlyMode),
      mpEarlyCapDays: parseIntField(obj.mp_early_cap_days, DEFAULT_SCHEDULE_CONTRACT.mpEarlyCapDays),
    };
  } catch {
    return { ...DEFAULT_SCHEDULE_CONTRACT };
  }
}

export function serializeScheduleContractJson(config: ScheduleContractConfig): string {
  return JSON.stringify({
    weight_due: Math.max(0, config.weightDue),
    enable_mp_target: config.enableMpTarget,
    enable_mp_contract_start_wait: config.enableMpContractStartWait,
    weight_mp_late: Math.max(0, config.weightMpLate),
    weight_mp_early: Math.max(0, config.weightMpEarly),
    mp_late_mode: config.mpLateMode,
    mp_early_mode: config.mpEarlyMode,
    mp_early_cap_days: Math.max(0, config.mpEarlyCapDays),
  });
}
