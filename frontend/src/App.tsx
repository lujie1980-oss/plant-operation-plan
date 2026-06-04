import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { SchedulingModuleLayout } from './components/SchedulingModuleLayout';
import { PlanProvider } from './context/PlanContext';
import { WorkspaceProvider } from './context/WorkspaceContext';
import { WorkspaceAdminPage } from './pages/WorkspaceAdminPage';
import { BusinessDataPage } from './pages/BusinessDataPage';
import { BusinessRulesPage } from './pages/BusinessRulesPage';
import { DashboardPage } from './pages/DashboardPage';
import { DemandTrackingPage } from './pages/DemandTrackingPage';
import { FactoryCalendarPage } from './pages/FactoryCalendarPage';
import { DetailSchedulePage } from './pages/DetailSchedulePage';
import { DemandPage } from './pages/DemandPage';
import { CapacityPage } from './pages/CapacityPage';
import { KittingPage } from './pages/KittingPage';
import { ProductionPlanPage } from './pages/ProductionPlanPage';
import { PlanDiagnosticsPage } from './pages/PlanDiagnosticsPage';
import { OrderPlanningChainPage } from './pages/OrderPlanningChainPage';
import { MasterDataPage } from './pages/MasterDataPage';
import { MasterPlanObjectivesPage } from './pages/MasterPlanObjectivesPage';
import { PlanParametersPage } from './pages/PlanParametersPage';
import { PlanRunPage } from './pages/PlanRunPage';
import { ScheduleKittingPage } from './pages/ScheduleKittingPage';
import { PendingScheduleWorkOrdersPage } from './pages/PendingScheduleWorkOrdersPage';
import { BatchPlanPage } from './pages/BatchPlanPage';
import { SchedulingPlanParametersPage } from './pages/SchedulingPlanParametersPage';
import { ScenarioComparisonPage } from './pages/ScenarioComparisonPage';
import { ScheduleVersionComparisonPage } from './pages/ScheduleVersionComparisonPage';
import { SlittingMasterDataPage } from './pages/slitting/SlittingMasterDataPage';
import { SlittingPlansPage } from './pages/slitting/SlittingPlansPage';
import { SlittingWorkbenchPage } from './pages/slitting/SlittingWorkbenchPage';

export default function App() {
  return (
    <WorkspaceProvider>
      <PlanProvider>
        <HashRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DashboardPage />} />
            <Route path="master-data" element={<MasterDataPage />} />
            <Route path="business-data" element={<BusinessDataPage />} />
            <Route path="workspaces" element={<WorkspaceAdminPage />} />

            <Route path="master-plan/parameters" element={<PlanParametersPage />} />
            <Route path="master-plan/objectives" element={<MasterPlanObjectivesPage />} />
            <Route path="business-rules" element={<Navigate to="/business-rules/production" replace />} />
            <Route path="business-rules/:categoryId" element={<BusinessRulesPage />} />
            <Route path="master-plan/plan-run" element={<PlanRunPage />} />
            <Route path="master-plan/analysis" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="master-plan/analysis/demand" element={<DemandPage />} />
            <Route path="master-plan/analysis/capacity" element={<CapacityPage />} />
            <Route path="master-plan/analysis/material" element={<KittingPage />} />
            <Route path="master-plan/analysis/work-orders" element={<ProductionPlanPage />} />
            <Route path="master-plan/analysis/diagnostics" element={<PlanDiagnosticsPage />} />
            <Route path="master-plan/analysis/order-chain" element={<OrderPlanningChainPage />} />
            <Route path="master-plan/demand" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="master-plan/capacity" element={<Navigate to="/master-plan/analysis/capacity" replace />} />
            <Route path="master-plan/material" element={<Navigate to="/master-plan/analysis/material" replace />} />
            <Route path="master-plan/work-orders" element={<Navigate to="/master-plan/analysis/work-orders" replace />} />
            <Route
              path="master-plan/scenario-comparison"
              element={
                <ScenarioComparisonPage
                  title="主计划场景对比"
                  description="勾选多个主计划场景，对比 Score、产能与排产关键 KPI"
                />
              }
            />

            <Route path="scheduling" element={<SchedulingModuleLayout />}>
              <Route path="parameters" element={<SchedulingPlanParametersPage />} />
              <Route path="batch-plan" element={<BatchPlanPage />} />
              <Route path="pending-work-orders" element={<PendingScheduleWorkOrdersPage />} />
              <Route path="kitting" element={<ScheduleKittingPage />} />
              <Route path="detail-schedule" element={<DetailSchedulePage />} />
              <Route path="version-comparison" element={<ScheduleVersionComparisonPage />} />
              <Route
                path="scenario-comparison"
                element={<Navigate to="/scheduling/version-comparison" replace />}
              />
            </Route>

            <Route path="slitting/master-data" element={<SlittingMasterDataPage />} />
            <Route path="slitting/plans" element={<SlittingPlansPage />} />
            <Route path="slitting/workbench" element={<SlittingWorkbenchPage />} />

            <Route path="factory-calendar" element={<FactoryCalendarPage />} />
            <Route path="demand-tracking" element={<DemandTrackingPage />} />

            {/* 旧路径重定向 */}
            <Route path="business-rules/other" element={<Navigate to="/business-rules/demand" replace />} />
            <Route path="master-plan/business-data" element={<Navigate to="/business-data" replace />} />
            <Route path="master-plan/business-rules" element={<Navigate to="/business-rules/production" replace />} />
            <Route path="demand" element={<Navigate to="/master-plan/demand" replace />} />
            <Route path="capacity" element={<Navigate to="/master-plan/capacity" replace />} />
            <Route path="scenario-comparison" element={<Navigate to="/master-plan/scenario-comparison" replace />} />
            <Route path="material-plan" element={<Navigate to="/master-plan/material" replace />} />
            <Route path="production-plan" element={<Navigate to="/master-plan/work-orders" replace />} />
            <Route path="schedule-kitting" element={<Navigate to="/scheduling/kitting" replace />} />
            <Route path="detail-schedule" element={<Navigate to="/scheduling/detail-schedule" replace />} />
            <Route path="kitting" element={<Navigate to="/master-plan/material" replace />} />
            <Route path="master-plan" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="execution" element={<Navigate to="/master-plan/work-orders" replace />} />
            <Route path="kpi" element={<Navigate to="/demand-tracking" replace />} />
            <Route path="pipeline" element={<Navigate to="/master-plan/plan-run" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
        </HashRouter>
      </PlanProvider>
    </WorkspaceProvider>
  );
}
