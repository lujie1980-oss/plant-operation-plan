import type { ReactNode } from 'react';
import { ScenarioSelector } from './ScenarioSelector';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** 在页面标题上方展示场景选择器（仅计划结果类页面开启） */
  showScenarioSelector?: boolean;
}

export function PageHeader({
  title,
  description,
  actions,
  showScenarioSelector = false,
}: PageHeaderProps) {
  return (
    <header className="page-header">
      {showScenarioSelector && (
        <div className="page-header-scenario">
          <ScenarioSelector />
        </div>
      )}
      <div className="page-header-body">
        <div>
          <h1>{title}</h1>
          {description && <p className="page-desc">{description}</p>}
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </div>
    </header>
  );
}
