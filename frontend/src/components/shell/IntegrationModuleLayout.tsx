import { Tabs } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import '../shell/AntShellPage.css';

const TAB_ITEMS = [
  { key: '/integration', label: '概览' },
  { key: '/integration/external/master', label: 'External 主数据' },
  { key: '/integration/external/transactional', label: 'External 交易' },
  { key: '/integration/adapters', label: '适配器' },
  { key: '/integration/quality', label: '质检报告' },
];

function activeTabKey(pathname: string): string {
  if (pathname === '/integration' || pathname === '/integration/') {
    return '/integration';
  }
  if (pathname.startsWith('/integration/adapters/')) {
    return '/integration/adapters';
  }
  if (pathname.startsWith('/integration/external/master')) {
    return '/integration/external/master';
  }
  if (pathname.startsWith('/integration/external/transactional')) {
    return '/integration/external/transactional';
  }
  if (pathname.startsWith('/integration/quality')) {
    return '/integration/quality';
  }
  if (pathname.startsWith('/integration/adapters')) {
    return '/integration/adapters';
  }
  return '/integration';
}

export function IntegrationModuleLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const activeKey = activeTabKey(pathname);

  return (
    <div className="integration-module">
      <Tabs
        className="integration-subnav"
        activeKey={activeKey}
        items={TAB_ITEMS}
        onChange={(key) => navigate(key)}
        size="small"
      />
      <div className="integration-module__body">
        <Outlet />
      </div>
    </div>
  );
}
