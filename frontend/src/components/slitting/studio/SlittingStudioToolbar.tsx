type PlanOption = { planVersionId: string; name: string };

type Props = {
  title?: string;
  plans: PlanOption[];
  planVersionId: string | null;
  planName: string | null;
  saving: boolean;
  optimizing?: boolean;
  masterCount: number;
  orderCount: number;
  onPlanChange: (id: string) => void;
  onNewPlan: () => void;
  onSave: () => void;
  onOptimizeAll?: () => void;
};

/** Studio 顶栏：标题 + 方案 + 操作 + KPI（合并原 PageHeader / studio-toolbar） */
export function SlittingStudioToolbar({
  title = '分切排样工作台',
  plans,
  planVersionId,
  planName,
  saving,
  optimizing = false,
  masterCount,
  orderCount,
  onPlanChange,
  onNewPlan,
  onSave,
  onOptimizeAll,
}: Props) {
  return (
    <div
      className="slitting-toolbar slitting-toolbar--page"
      title="母卷与订单拖放、树形分切；画板固定显示母卷并高亮选中项"
    >
      <h1 className="slitting-toolbar-title">{title}</h1>
      <div className="slitting-toolbar-divider" aria-hidden />

      <div className="slitting-toolbar-group">
        <span className="slitting-toolbar-label">方案</span>
        <label className="slitting-toolbar-field">
          <span className="sr-only">选择方案</span>
          <select
            className="input slitting-select"
            value={planVersionId ?? ''}
            onChange={(e) => {
              const id = e.target.value;
              if (id) onPlanChange(id);
            }}
          >
            <option value="">— 新建或选择 —</option>
            {plans.map((p) => (
              <option key={p.planVersionId} value={p.planVersionId}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="btn" onClick={onNewPlan}>
          新建
        </button>
        <button
          type="button"
          className="btn primary slitting-btn-accent"
          disabled={saving || !planVersionId}
          onClick={onSave}
        >
          {saving ? '保存中…' : '保存'}
        </button>
        {onOptimizeAll ? (
          <button
            type="button"
            className="btn"
            disabled={saving || optimizing || !planVersionId}
            onClick={onOptimizeAll}
            title="重新优化全部未锁定分切，已锁定项保持不变"
          >
            {optimizing ? '优化中…' : '优化未锁定'}
          </button>
        ) : null}
      </div>

      <div className="slitting-toolbar-kpis">
        {planName && (
          <span className="slitting-kpi slitting-kpi--plan" title={planVersionId ?? undefined}>
            <span className="slitting-kpi-plan-name">{planName}</span>
            {planVersionId ? <code>{planVersionId}</code> : null}
          </span>
        )}
        <span className="slitting-kpi">
          待排 <strong>{masterCount}</strong> 母卷 · <strong>{orderCount}</strong> 订单
        </span>
      </div>
    </div>
  );
}
