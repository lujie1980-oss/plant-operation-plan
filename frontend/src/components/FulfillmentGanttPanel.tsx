import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Gantt, ViewMode, type Task } from 'gantt-task-react';
import 'gantt-task-react/dist/index.css';
import type { FulfillmentPegEdge } from '../types/api';
import { FulfillmentPegArrows } from './FulfillmentPegArrows';
import { FulfillmentGanttToolbar } from './FulfillmentGanttToolbar';
import './FulfillmentGanttPanel.css';
import './FulfillmentPegArrows.css';

const LIST_CELL_WIDTH = '176px';
const DEFAULT_ROW_HEIGHT = 35;
const DEFAULT_HEADER_HEIGHT = 35;
const BAR_FILL = 50;

interface FulfillmentGanttPanelProps {
  tasks: Task[];
  pegEdges: FulfillmentPegEdge[];
  title?: string;
  className?: string;
  onTasksChange?: (tasks: Task[]) => void;
  /** 隐藏甘特内置左侧任务列表（由外部满足链树展示） */
  showTaskList?: boolean;
  /** 是否绘制满足链箭头 */
  showArrows?: boolean;
  onShowArrowsChange?: (show: boolean) => void;
  rowHeight?: number;
  headerHeight?: number;
  /** 图表区高度（与可见行数 × rowHeight 一致时可取消内部纵滚） */
  ganttHeight?: number;
  onGanttBodyScroll?: () => void;
  hideToolbar?: boolean;
  viewMode?: ViewMode;
  onViewModeChange?: (mode: ViewMode) => void;
}

export function FulfillmentGanttPanel({
  tasks,
  pegEdges,
  title = '满足链甘特图',
  className = '',
  onTasksChange,
  showTaskList = true,
  showArrows = true,
  onShowArrowsChange,
  rowHeight = DEFAULT_ROW_HEIGHT,
  headerHeight = DEFAULT_HEADER_HEIGHT,
  ganttHeight,
  onGanttBodyScroll,
  hideToolbar = false,
  viewMode: viewModeProp,
  onViewModeChange,
}: FulfillmentGanttPanelProps) {
  const [viewModeInternal, setViewModeInternal] = useState(ViewMode.Day);
  const viewMode = viewModeProp ?? viewModeInternal;
  const setViewMode = onViewModeChange ?? setViewModeInternal;
  const [localTasks, setLocalTasks] = useState<Task[]>(tasks);
  const ganttWrapRef = useRef<HTMLDivElement>(null);

  const displayTasks = onTasksChange ? tasks : localTasks;
  const columnWidth = viewMode === ViewMode.Hour ? 48 : 64;

  const validTasks = useMemo(
    () => displayTasks.filter((t) => t.start && t.end && !Number.isNaN(t.start.getTime())),
    [displayTasks],
  );

  const syncTasks = useCallback(
    (next: Task[]) => {
      if (onTasksChange) {
        onTasksChange(next);
      } else {
        setLocalTasks(next);
      }
    },
    [onTasksChange],
  );

  const handleExpanderClick = useCallback(
    (task: Task) => {
      syncTasks(
        displayTasks.map((t) =>
          t.id === task.id ? { ...t, hideChildren: !t.hideChildren } : t,
        ),
      );
    },
    [displayTasks, syncTasks],
  );

  useEffect(() => {
    if (!onTasksChange) {
      setLocalTasks(tasks);
    }
  }, [tasks, onTasksChange]);

  useEffect(() => {
    if (!onGanttBodyScroll) return;
    const el = ganttWrapRef.current?.querySelector<HTMLElement>(
      'div[class*="horizontalContainer"]',
    );
    if (!el) return;
    const handler = () => onGanttBodyScroll();
    el.addEventListener('scroll', handler, { passive: true });
    return () => el.removeEventListener('scroll', handler);
  }, [onGanttBodyScroll, validTasks.length, viewMode, ganttHeight]);

  return (
    <section className={`card gantt-card fulfillment-gantt ${className}`.trim()}>
      {!hideToolbar && (
        <FulfillmentGanttToolbar
          title={title}
          showArrows={showArrows}
          onShowArrowsChange={onShowArrowsChange}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
        />
      )}

      {showTaskList && <p className="gantt-hint">点击工单行左侧箭头可展开工序</p>}

      <div className="gantt-container" ref={ganttWrapRef}>
        <Gantt
          tasks={validTasks}
          viewMode={viewMode}
          locale="zh-CN"
          listCellWidth={showTaskList ? LIST_CELL_WIDTH : ''}
          columnWidth={columnWidth}
          rowHeight={rowHeight}
          headerHeight={headerHeight}
          ganttHeight={ganttHeight}
          barFill={BAR_FILL}
          preStepsCount={1}
          todayColor="rgba(59, 130, 246, 0.15)"
          onExpanderClick={handleExpanderClick}
        />
        <FulfillmentPegArrows
          tasks={validTasks}
          edges={pegEdges}
          viewMode={viewMode}
          columnWidth={columnWidth}
          rowHeight={rowHeight}
          headerHeight={headerHeight}
          enabled={showArrows}
        />
      </div>
    </section>
  );
}
