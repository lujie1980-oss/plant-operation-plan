import { useEffect, useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../providers/AuthContext';
import { useWorkspace } from '../context/WorkspaceContext';
import {
  expandForPath,
  filterNavGroups,
  groupActive,
  subGroupActive,
  TOP_NAV,
  type NavGroup,
  type NavSubGroup,
} from '../config/workspaceNav';
import { useEnabledModules } from '../hooks/useEnabledModules';
import { WorkspaceSelector } from './WorkspaceSelector';
import './Layout.css';

export function Layout() {
  const { pathname } = useLocation();
  const { workspaceId } = useWorkspace();
  const { currentUser, workspaces, logout, devMode } = useAuth();
  const membership = workspaces.find((w) => w.workspaceId === workspaceId);
  const canManageWorkspace =
    currentUser?.isSuperAdmin ||
    membership?.role === 'OWNER' ||
    membership?.role === 'WS_ADMIN';
  const enabledModules = useEnabledModules();
  const navGroups = useMemo(() => filterNavGroups(enabledModules), [enabledModules]);
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() =>
    expandForPath(pathname, navGroups, {}),
  );

  useEffect(() => {
    setExpanded((prev) => expandForPath(pathname, navGroups, prev));
  }, [pathname, navGroups]);

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

          {navGroups.map((group) => (
            <NavGroupBlock
              key={group.id}
              group={group}
              pathname={pathname}
              expanded={expanded}
              onToggle={toggleGroup}
            />
          ))}
        </nav>
      </aside>
      <div className="content-column">
        <header className="content-topbar">
          <WorkspaceSelector />
          {canManageWorkspace && workspaceId && (
            <Link to={`/workspaces/${workspaceId}/settings`} className="content-topbar-link">
              模块设置
            </Link>
          )}
          <Link to="/workspaces" className="content-topbar-link">
            管理数据集
          </Link>
          {currentUser?.isSuperAdmin && (
            <Link to="/admin/users" className="content-topbar-link">
              平台管理
            </Link>
          )}
          {!devMode && (
            <button type="button" className="content-topbar-link content-topbar-btn" onClick={logout}>
              退出
            </button>
          )}
        </header>
        <main className="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function NavGroupBlock({
  group,
  pathname,
  expanded,
  onToggle,
}: {
  group: NavGroup;
  pathname: string;
  expanded: Record<string, boolean>;
  onToggle: (id: string) => void;
}) {
  return (
    <div className="nav-group">
      <button
        type="button"
        className={`nav-group-head ${groupActive(group, pathname) ? 'is-active-group' : ''}`}
        onClick={() => onToggle(group.id)}
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
            <NavSubGroupBlock
              key={sub.id}
              sub={sub}
              pathname={pathname}
              expanded={expanded}
              onToggle={onToggle}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function NavSubGroupBlock({
  sub,
  pathname,
  expanded,
  onToggle,
}: {
  sub: NavSubGroup;
  pathname: string;
  expanded: Record<string, boolean>;
  onToggle: (id: string) => void;
}) {
  return (
    <div className="nav-subgroup">
      <button
        type="button"
        className={`nav-subgroup-head ${subGroupActive(sub, pathname) ? 'is-active-subgroup' : ''}`}
        onClick={() => onToggle(sub.id)}
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
  );
}
