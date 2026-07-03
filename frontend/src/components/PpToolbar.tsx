import type { ReactNode } from 'react';

/** 计划/排程页紧凑工具栏容器（无 card 阴影） */
export function PpToolbar({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`pp-toolbar ${className}`.trim()}>{children}</div>;
}

export function PpToolbarRow({ children }: { children: ReactNode }) {
  return <div className="pp-toolbar-row">{children}</div>;
}

/** 默认折叠的页面说明与上下文信息 */
export function PpToolbarHint({
  summary = '说明',
  children,
}: {
  summary?: string;
  children: ReactNode;
}) {
  return (
    <details className="pp-hint-details">
      <summary className="pp-hint-summary">{summary}</summary>
      <div className="pp-hint-body">{children}</div>
    </details>
  );
}
