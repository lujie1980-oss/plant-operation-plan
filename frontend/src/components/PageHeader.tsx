import type { ReactNode } from 'react';
import { ScenarioSelector } from './ScenarioSelector';
import { ScheduleVersionSelector } from './ScheduleVersionSelector';

export type PageHeaderVariant = 'default' | 'compact';

/** 决策 / 排程类页面统一使用紧凑页头 */
export const DECISION_PAGE_HEADER: PageHeaderVariant = 'compact';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** 在页面标题上方展示场景选择器（仅计划结果类页面开启） */
  showScenarioSelector?: boolean;
  /** 排程模块：展示排程版本（默认当前版本，可切换历史） */
  showScheduleVersionSelector?: boolean;
  /** compact：单行页头，描述合并到 title 提示，场景与操作同排 */
  variant?: PageHeaderVariant;
}

export function PageHeader({
  title,
  description,
  actions,
  showScenarioSelector = false,
  showScheduleVersionSelector = false,
  variant = 'default',
}: PageHeaderProps) {
  const titleTip = description ? `${title} — ${description}` : title;
  const contextSelector = showScheduleVersionSelector ? (
    <ScheduleVersionSelector />
  ) : showScenarioSelector ? (
    <ScenarioSelector />
  ) : null;

  if (variant === 'compact') {
    return (
      <header className="page-header page-header--compact">
        <div className="page-header-compact-row">
          {contextSelector ? <div className="page-header-context">{contextSelector}</div> : null}
          <h1 className="page-header-title" title={titleTip}>
            {title}
          </h1>
          {actions ? <div className="page-actions">{actions}</div> : null}
        </div>
      </header>
    );
  }

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
