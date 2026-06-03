import type { ReactNode } from 'react';
import { ScenarioSelector } from './ScenarioSelector';
import { ScheduleVersionSelector } from './ScheduleVersionSelector';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** 在页面标题上方展示场景选择器（仅计划结果类页面开启） */
  showScenarioSelector?: boolean;
  /** 排程模块：展示排程版本（默认当前版本，可切换历史） */
  showScheduleVersionSelector?: boolean;
}

export function PageHeader({
  title,
  description,
  actions,
  showScenarioSelector = false,
  showScheduleVersionSelector = false,
}: PageHeaderProps) {
  return (
    <header className="page-header">
      {(showScenarioSelector || showScheduleVersionSelector) && (
        <div className="page-header-scenario">
          {showScheduleVersionSelector ? <ScheduleVersionSelector /> : <ScenarioSelector />}
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
