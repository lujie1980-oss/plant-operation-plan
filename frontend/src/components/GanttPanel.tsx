import { useMemo, useState } from 'react';
import { Gantt, ViewMode, type Task } from 'gantt-task-react';
import 'gantt-task-react/dist/index.css';
import './GanttPanel.css';

interface GanttPanelProps {
  tasks: Task[];
  title?: string;
  className?: string;
  /** 左侧列表标题，如「机台」 */
  listTitle?: string;
  listCellWidth?: string;
  rowHeight?: number;
}

function MachineTaskListHeader({
  headerHeight,
  rowWidth,
  fontFamily,
  fontSize,
  title,
}: {
  headerHeight: number;
  rowWidth: string;
  fontFamily: string;
  fontSize: string;
  title: string;
}) {
  return (
    <div
      className="gantt-list-header-custom"
      style={{ height: headerHeight, width: rowWidth, fontFamily, fontSize }}
    >
      <span>{title}</span>
    </div>
  );
}

export function GanttPanel({
  tasks,
  title = '甘特图',
  className = '',
  listTitle,
  listCellWidth = '176px',
  rowHeight = 31,
}: GanttPanelProps) {
  const [viewMode, setViewMode] = useState<ViewMode>(ViewMode.Hour);

  const validTasks = useMemo(() => {
    return tasks.filter((t) => t.start && t.end && !Number.isNaN(t.start.getTime()));
  }, [tasks]);

  const TaskListHeader = listTitle
    ? (props: { headerHeight: number; rowWidth: string; fontFamily: string; fontSize: string }) => (
        <MachineTaskListHeader {...props} title={listTitle} />
      )
    : undefined;

  return (
    <section className={`card gantt-card ${className}`.trim()}>
      <div className="gantt-toolbar">
        <h3>{title}</h3>
        <div className="gantt-view-modes">
          <button type="button" className={viewMode === ViewMode.Hour ? 'active' : ''} onClick={() => setViewMode(ViewMode.Hour)}>
            小时
          </button>
          <button type="button" className={viewMode === ViewMode.Day ? 'active' : ''} onClick={() => setViewMode(ViewMode.Day)}>
            日
          </button>
          <button type="button" className={viewMode === ViewMode.Week ? 'active' : ''} onClick={() => setViewMode(ViewMode.Week)}>
            周
          </button>
        </div>
      </div>
      <div className="gantt-container">
        <Gantt
          tasks={validTasks}
          viewMode={viewMode}
          locale="zh-CN"
          listCellWidth={listCellWidth}
          rowHeight={rowHeight}
          columnWidth={viewMode === ViewMode.Hour ? 48 : 64}
          barFill={50}
          todayColor="rgba(59, 130, 246, 0.15)"
          TaskListHeader={TaskListHeader}
        />
      </div>
    </section>
  );
}
