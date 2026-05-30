import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { PlanProvider } from './context/PlanContext';
import { WorkspaceProvider } from './context/WorkspaceContext';
import { WorkspaceAdminPage } from './pages/WorkspaceAdminPage';
import { BusinessDataPage } from './pages/BusinessDataPage';
import { BusinessRulesPage } from './pages/BusinessRulesPage';
import { DashboardPage } from './pages/DashboardPage';
import { DemandTrackingPage } from './pages/DemandTrackingPage';
import { DetailSchedulePage } from './pages/DetailSchedulePage';
import { DemandPage } from './pages/DemandPage';
import { CapacityPage } from './pages/CapacityPage';
import { KittingPage } from './pages/KittingPage';
import { ProductionPlanPage } from './pages/ProductionPlanPage';
import { PlanDiagnosticsPage } from './pages/PlanDiagnosticsPage';
import { MasterDataPage } from './pages/MasterDataPage';
import { MasterPlanObjectivesPage } from './pages/MasterPlanObjectivesPage';
import { PlanParametersPage } from './pages/PlanParametersPage';
import { PlanRunPage } from './pages/PlanRunPage';
import { ScheduleKittingPage } from './pages/ScheduleKittingPage';
import { SchedulingPlanParametersPage } from './pages/SchedulingPlanParametersPage';
import { ScenarioComparisonPage } from './pages/ScenarioComparisonPage';

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

            <Route path="scheduling/parameters" element={<SchedulingPlanParametersPage />} />
            <Route path="scheduling/kitting" element={<ScheduleKittingPage />} />
            <Route path="scheduling/detail-schedule" element={<DetailSchedulePage />} />
            <Route
              path="scheduling/scenario-comparison"
              element={
                <ScenarioComparisonPage
                  title="排程场景对比"
                  description="对比不同排程版本的关键 KPI（当前与主计划场景共用数据源，后续可扩展排程版本对比）"
                  emptyHint="请先在「计划运行」生成多个主计划场景；排程版本对比接口预留中。"
                />
              }
            />

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
