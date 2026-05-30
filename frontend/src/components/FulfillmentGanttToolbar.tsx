import { ViewMode } from 'gantt-task-react';

interface FulfillmentGanttToolbarProps {
  title?: string;
  showArrows: boolean;
  onShowArrowsChange?: (show: boolean) => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  compact?: boolean;
}

export function FulfillmentGanttToolbar({
  title,
  showArrows,
  onShowArrowsChange,
  viewMode,
  onViewModeChange,
  compact = false,
}: FulfillmentGanttToolbarProps) {
  return (
    <div className={`gantt-toolbar ${compact ? 'gantt-toolbar-compact' : ''}`.trim()}>
      {title ? <h3>{title}</h3> : <span />}
      <div className="gantt-toolbar-actions">
        {onShowArrowsChange && (
          <button
            type="button"
            className={`btn gantt-arrow-btn ${showArrows ? 'active' : ''}`}
            onClick={() => onShowArrowsChange(!showArrows)}
          >
            {showArrows ? '隐藏箭头' : '显示箭头'}
          </button>
        )}
        <div className="gantt-view-modes">
          <button
            type="button"
            className={viewMode === ViewMode.Hour ? 'active' : ''}
            onClick={() => onViewModeChange(ViewMode.Hour)}
          >
            小时
          </button>
          <button
            type="button"
            className={viewMode === ViewMode.Day ? 'active' : ''}
            onClick={() => onViewModeChange(ViewMode.Day)}
          >
            日
          </button>
          <button
            type="button"
            className={viewMode === ViewMode.Week ? 'active' : ''}
            onClick={() => onViewModeChange(ViewMode.Week)}
          >
            周
          </button>
        </div>
      </div>
    </div>
  );
}
