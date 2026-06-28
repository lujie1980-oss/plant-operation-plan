import type { ReactNode } from 'react';
import { Typography } from 'antd';
import './AntShellPage.css';

type AntShellPageProps = {
  title: string;
  description?: string;
  extra?: ReactNode;
  children: ReactNode;
};

/** L1 Shell page wrapper — Ant Design typography + domain content below. */
export function AntShellPage({ title, description, extra, children }: AntShellPageProps) {
  return (
    <div className="ant-shell-page">
      <div className="ant-shell-page__header">
        <div>
          <Typography.Title level={3} className="ant-shell-page__title">
            {title}
          </Typography.Title>
          {description ? (
            <Typography.Paragraph type="secondary" className="ant-shell-page__desc">
              {description}
            </Typography.Paragraph>
          ) : null}
        </div>
        {extra ? <div className="ant-shell-page__extra">{extra}</div> : null}
      </div>
      {children}
    </div>
  );
}
