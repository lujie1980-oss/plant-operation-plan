import { Outlet } from 'react-router-dom';
import { ScheduleVersionProvider } from '../context/ScheduleVersionContext';

/** 排程模块布局：注入排程版本上下文，供各子页面共享。 */
export function SchedulingModuleLayout() {
  return (
    <ScheduleVersionProvider>
      <Outlet />
    </ScheduleVersionProvider>
  );
}
