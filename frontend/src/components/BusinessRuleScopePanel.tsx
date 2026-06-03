import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { BusinessRuleScopeMd } from '../types/masterData';

type Props = {
  ruleTypeId: string;
  scope: BusinessRuleScopeMd | null;
  onScopeUpdated: (scope: BusinessRuleScopeMd) => void;
};

function formatScopeError(e: unknown, fallback = '加载启用范围失败'): string {
  const msg = e instanceof Error ? e.message : fallback;
  if (/business_rule_scope_seq/i.test(msg) || (/sequence/i.test(msg) && /not found/i.test(msg))) {
    return '启用范围配置暂不可用，请重启后端服务以完成数据库迁移。';
  }
  try {
    const parsed = JSON.parse(msg) as { detail?: string; message?: string };
    const detail = parsed.detail ?? parsed.message;
    if (detail) return detail;
  } catch {
    /* plain text */
  }
  return msg.length > 240 ? fallback : msg;
}

export function BusinessRuleScopePanel({ ruleTypeId, scope, onScopeUpdated }: Props) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async (patch: Partial<Pick<BusinessRuleScopeMd, 'enableMasterPlan' | 'enableDetailSchedule'>>) => {
    if (!scope) return;
    setSaving(true);
    setError(null);
    try {
      const next = {
        ...scope,
        ...patch,
      };
      const saved = await api.masterData.businessRuleScopes.save(ruleTypeId, next);
      onScopeUpdated(saved);
    } catch (e) {
      setError(formatScopeError(e, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  if (!scope) {
    return null;
  }

  return (
    <div className="br-rule-scope" role="group" aria-label="规则项目启用范围">
      <span className="br-rule-scope-label">启用范围</span>
      <label className="br-rule-scope-option">
        <input
          type="checkbox"
          checked={scope.enableMasterPlan}
          disabled={saving}
          onChange={(e) => void save({ enableMasterPlan: e.target.checked })}
        />
        主计划
      </label>
      <label className="br-rule-scope-option">
        <input
          type="checkbox"
          checked={scope.enableDetailSchedule}
          disabled={saving}
          onChange={(e) => void save({ enableDetailSchedule: e.target.checked })}
        />
        排程
      </label>
      {error && <span className="br-scope-error">{error}</span>}
    </div>
  );
}

export function useBusinessRuleScopes() {
  const [scopesById, setScopesById] = useState<Record<string, BusinessRuleScopeMd>>({});
  const [scopeError, setScopeError] = useState<string | null>(null);
  const [scopeLoading, setScopeLoading] = useState(false);

  const loadScopes = useCallback(async () => {
    setScopeLoading(true);
    setScopeError(null);
    try {
      const list = await api.masterData.businessRuleScopes.list();
      setScopesById(Object.fromEntries(list.map((s) => [s.ruleTypeId, s])));
    } catch (e) {
      setScopeError(formatScopeError(e));
    } finally {
      setScopeLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadScopes();
  }, [loadScopes]);

  const updateScope = useCallback(async (ruleTypeId: string, patch: Partial<BusinessRuleScopeMd>) => {
    const current = scopesById[ruleTypeId];
    if (!current) {
      throw new Error('规则配置尚未加载');
    }
    const saved = await api.masterData.businessRuleScopes.save(ruleTypeId, { ...current, ...patch });
    setScopesById((prev) => ({ ...prev, [ruleTypeId]: saved }));
    return saved;
  }, [scopesById]);

  const replaceScope = useCallback((scope: BusinessRuleScopeMd) => {
    setScopesById((prev) => ({ ...prev, [scope.ruleTypeId]: scope }));
  }, []);

  return { scopesById, scopeError, scopeLoading, loadScopes, updateScope, replaceScope };
}
