import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { WorkspaceSelector } from './WorkspaceSelector';
import './Layout.css';

type NavLinkItem = { to: string; label: string; end?: boolean };

type NavSubGroup = {
  id: string;
  label: string;
  items: NavLinkItem[];
};

type NavGroup = {
  id: string;
  label: string;
  items: NavLinkItem[];
  subGroups?: NavSubGroup[];
};

const TOP_NAV: NavLinkItem[] = [{ to: '/', label: '首页', end: true }];

const DATA_GROUP: NavGroup = {
  id: 'data',
  label: '数据管理',
  items: [
    { to: '/master-data', label: '主数据', end: true },
    { to: '/business-data', label: '业务数据', end: true },
    { to: '/factory-calendar', label: '工厂日历', end: true },
  ],
};

const BUSINESS_RULES_GROUP: NavGroup = {
  id: 'business-rules',
  label: '业务规则',
  items: [
    { to: '/business-rules/production', label: '生产规则' },
    { to: '/business-rules/capacity', label: '产能规则' },
    { to: '/business-rules/material', label: '物料规则' },
    { to: '/business-rules/labor', label: '人力规则' },
    { to: '/business-rules/demand', label: '需求规则' },
  ],
};

const MASTER_PLAN_GROUP: NavGroup = {
  id: 'master-plan',
  label: '主计划',
  items: [
    { to: '/master-plan/parameters', label: '计划参数' },
    { to: '/master-plan/objectives', label: '优化目标' },
    { to: '/master-plan/plan-run', label: '计划运行' },
    { to: '/master-plan/scenario-comparison', label: '场景对比' },
  ],
  subGroups: [
    {
      id: 'plan-analysis',
      label: '计划分析',
      items: [
        { to: '/master-plan/analysis/demand', label: '需求满足' },
        { to: '/master-plan/analysis/capacity', label: '产能平衡' },
        { to: '/master-plan/analysis/material', label: '物料需求' },
        { to: '/master-plan/analysis/work-orders', label: '生产工单' },
        { to: '/master-plan/analysis/diagnostics', label: '推演诊断' },
        { to: '/master-plan/analysis/order-chain', label: '订单推演' },
      ],
    },
  ],
};

const SCHEDULING_GROUP: NavGroup = {
  id: 'scheduling',
  label: '生产排程',
  items: [
    { to: '/scheduling/parameters', label: '计划参数' },
    { to: '/scheduling/pending-work-orders', label: '待排工单' },
    { to: '/scheduling/batch-plan', label: '批次计划' },
    { to: '/scheduling/kitting', label: '物料齐套' },
    { to: '/scheduling/detail-schedule', label: '生产排程' },
    { to: '/scheduling/version-comparison', label: '版本对比' },
  ],
};

const SLITTING_GROUP: NavGroup = {
  id: 'slitting',
  label: '分切排样',
  items: [
    { to: '/slitting/master-data', label: '主数据' },
    { to: '/slitting/plans', label: '分切方案' },
    { to: '/slitting/workbench', label: '画板工作台' },
  ],
};

const ALL_GROUPS = [DATA_GROUP, BUSINESS_RULES_GROUP, MASTER_PLAN_GROUP, SCHEDULING_GROUP, SLITTING_GROUP];

function pathMatchesItem(pathname: string, item: NavLinkItem) {
  return pathname === item.to || pathname.startsWith(`${item.to}/`);
}

function subGroupActive(sub: NavSubGroup, pathname: string) {
  return sub.items.some((item) => pathMatchesItem(pathname, item));
}

function groupActive(group: NavGroup, pathname: string) {
  if (group.subGroups?.some((sub) => subGroupActive(sub, pathname))) {
    return true;
  }
  return group.items.some((item) => pathMatchesItem(pathname, item));
}

function expandForPath(pathname: string, prev: Record<string, boolean>) {
  const next = { ...prev };
  for (const g of ALL_GROUPS) {
    if (groupActive(g, pathname)) {
      next[g.id] = true;
    }
    for (const sub of g.subGroups ?? []) {
      if (subGroupActive(sub, pathname)) {
        next[g.id] = true;
        next[sub.id] = true;
      }
    }
  }
  return next;
}

export function Layout() {
  const { pathname } = useLocation();
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => expandForPath(pathname, {}));

  useEffect(() => {
    setExpanded((prev) => expandForPath(pathname, prev));
  }, [pathname]);

  const toggleGroup = (id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !(prev[id] ?? false) }));
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-title">工厂运营计划</span>
          <span className="brand-sub">APS · Timefold</span>
        </div>
        <nav className="nav">
          {TOP_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}

          {ALL_GROUPS.map((group) => (
            <div key={group.id} className="nav-group">
              <button
                type="button"
                className={`nav-group-head ${groupActive(group, pathname) ? 'is-active-group' : ''}`}
                onClick={() => toggleGroup(group.id)}
                aria-expanded={expanded[group.id] ?? false}
              >
                <span>{group.label}</span>
                <span className="nav-group-chevron">{expanded[group.id] ? '▾' : '▸'}</span>
              </button>
              {expanded[group.id] && (
                <div className="nav-group-items">
                  {group.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      className={({ isActive }) =>
                        isActive ? 'nav-link nav-link-sub active' : 'nav-link nav-link-sub'
                      }
                    >
                      {item.label}
                    </NavLink>
                  ))}
                  {(group.subGroups ?? []).map((sub) => (
                    <div key={sub.id} className="nav-subgroup">
                      <button
                        type="button"
                        className={`nav-subgroup-head ${subGroupActive(sub, pathname) ? 'is-active-subgroup' : ''}`}
                        onClick={() => toggleGroup(sub.id)}
                        aria-expanded={expanded[sub.id] ?? false}
                      >
                        <span>{sub.label}</span>
                        <span className="nav-group-chevron">{expanded[sub.id] ? '▾' : '▸'}</span>
                      </button>
                      {expanded[sub.id] && (
                        <div className="nav-subgroup-items">
                          {sub.items.map((item) => (
                            <NavLink
                              key={item.to}
                              to={item.to}
                              end={item.end}
                              className={({ isActive }) =>
                                isActive ? 'nav-link nav-link-sub2 active' : 'nav-link nav-link-sub2'
                              }
                            >
                              {item.label}
                            </NavLink>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </nav>
      </aside>
      <div className="content-column">
        <header className="content-topbar">
          <WorkspaceSelector />
          <Link to="/workspaces" className="content-topbar-link">
            管理数据集
          </Link>
        </header>
        <main className="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
