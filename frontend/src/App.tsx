import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { IntegrationModuleLayout } from './components/shell/IntegrationModuleLayout';
import { SchedulingModuleLayout } from './components/SchedulingModuleLayout';
import { PlanProvider } from './context/PlanContext';
import { WorkspaceProvider } from './context/WorkspaceContext';
import { TableRowHoverTipDelegate } from './components/table/TableRowHoverTipDelegate';
import { TableCellContextMenuDelegate } from './components/table/TableCellContextMenuDelegate';
import { WorkspaceAdminPage } from './pages/WorkspaceAdminPage';
import { BusinessDataPage } from './pages/BusinessDataPage';
import { BusinessRulesPage } from './pages/BusinessRulesPage';
import { BusinessRulesLegacyRedirect } from './pages/BusinessRulesLegacyRedirect';
import { DashboardPage } from './pages/DashboardPage';
import { DemandTrackingPage } from './pages/DemandTrackingPage';
import { FactoryCalendarPage } from './pages/FactoryCalendarPage';
import { DetailSchedulePage } from './pages/DetailSchedulePage';
import { DemandPage } from './pages/DemandPage';
import { CapacityPage } from './pages/CapacityPage';
import { MaterialPlanningPage } from './pages/MaterialPlanningPage';
import { ProductionPlanPage } from './pages/ProductionPlanPage';
import { MasterDataPage } from './pages/MasterDataPage';
import { MasterPlanObjectivesPage } from './pages/MasterPlanObjectivesPage';
import { PlanParametersPage } from './pages/PlanParametersPage';
import { PlanRunPage } from './pages/PlanRunPage';
import { ScheduleKittingPage } from './pages/ScheduleKittingPage';
import { PendingScheduleWorkOrdersPage } from './pages/PendingScheduleWorkOrdersPage';
import { BatchPlanPage } from './pages/BatchPlanPage';
import { MasterPlanOntologyPage } from './pages/MasterPlanOntologyPage';
import { MasterPlanDataModelPage } from './pages/MasterPlanDataModelPage';
import { SchedulingPlanParametersPage } from './pages/SchedulingPlanParametersPage';
import { ScenarioComparisonPage } from './pages/ScenarioComparisonPage';
import { ScheduleVersionComparisonPage } from './pages/ScheduleVersionComparisonPage';
import { SlittingMasterDataPage } from './pages/slitting/SlittingMasterDataPage';
import { SlittingParametersPage } from './pages/slitting/SlittingParametersPage';
import { SlittingOptimizeRunPage } from './pages/slitting/SlittingOptimizeRunPage';
import { SlittingStudioPage } from './pages/slitting/SlittingStudioPage';
import { IntegrationOverviewPage } from './pages/integration/IntegrationOverviewPage';
import { IntegrationExternalMasterPage } from './pages/integration/IntegrationExternalMasterPage';
import { IntegrationExternalTransactionalPage } from './pages/integration/IntegrationExternalTransactionalPage';
import { IntegrationAdaptersPage } from './pages/integration/IntegrationAdaptersPage';
import { IntegrationAdapterDetailPage } from './pages/integration/IntegrationAdapterDetailPage';
import { IntegrationQualityPage } from './pages/integration/IntegrationQualityPage';
import { AppProviders } from './providers/AppProviders';
import { AuthProvider, useAuth } from './providers/AuthContext';
import { CreateWorkspacePage } from './pages/CreateWorkspacePage';

function AppContent() {
  const { isLoading, hasWorkspaces } = useAuth();

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
        <span style={{ color: '#64748b', fontSize: 14 }}>加载中…</span>
      </div>
    );
  }

  if (!hasWorkspaces) {
    return (
      <HashRouter>
        <Routes>
          <Route path="*" element={<CreateWorkspacePage />} />
        </Routes>
      </HashRouter>
    );
  }

  return (
    <WorkspaceProvider>
      <PlanProvider>
        <TableRowHoverTipDelegate />
        <TableCellContextMenuDelegate />
        <HashRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DashboardPage />} />

            <Route path="integration" element={<IntegrationModuleLayout />}>
              <Route index element={<IntegrationOverviewPage />} />
              <Route path="external/master" element={<IntegrationExternalMasterPage />} />
              <Route path="external/transactional" element={<IntegrationExternalTransactionalPage />} />
              <Route path="adapters" element={<IntegrationAdaptersPage />} />
              <Route path="adapters/:adapterSlug" element={<IntegrationAdapterDetailPage />} />
              <Route path="quality" element={<IntegrationQualityPage />} />
            </Route>

            <Route path="master-data" element={<MasterDataPage />} />
            <Route path="business-data" element={<BusinessDataPage />} />
            <Route path="workspaces" element={<WorkspaceAdminPage />} />

            <Route path="master-plan/rules" element={<Navigate to="/master-plan/rules/demand" replace />} />
            <Route path="master-plan/rules/:categoryId" element={<BusinessRulesPage moduleId="MOD-OCP" />} />
            <Route path="master-plan/parameters" element={<PlanParametersPage />} />
            <Route path="master-plan/objectives" element={<MasterPlanObjectivesPage />} />
            <Route path="business-rules" element={<Navigate to="/master-plan/rules/demand" replace />} />
            <Route path="business-rules/:categoryId" element={<BusinessRulesLegacyRedirect />} />
            <Route path="master-plan/plan-run" element={<PlanRunPage />} />
            <Route path="master-plan/ontology" element={<MasterPlanOntologyPage />} />
            <Route path="master-plan/data-model" element={<MasterPlanDataModelPage />} />
            <Route path="master-plan/analysis" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="master-plan/analysis/demand" element={<DemandPage />} />
            <Route path="master-plan/analysis/capacity" element={<CapacityPage />} />
            <Route path="master-plan/analysis/material-planning" element={<MaterialPlanningPage />} />
            <Route path="master-plan/analysis/material" element={<Navigate to="/master-plan/analysis/material-planning" replace />} />
            <Route path="master-plan/analysis/work-orders" element={<ProductionPlanPage />} />
            <Route path="master-plan/analysis/order-chain" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="master-plan/demand" element={<Navigate to="/master-plan/analysis/demand" replace />} />
            <Route path="master-plan/capacity" element={<Navigate to="/master-plan/analysis/capacity" replace />} />
            <Route path="master-plan/material" element={<Navigate to="/master-plan/analysis/material-planning" replace />} />
            <Route path="master-plan/work-orders" element={<Navigate to="/master-plan/analysis/work-orders" replace />} />
            <Route
              path="master-plan/scenario-comparison"
              element={
                <ScenarioComparisonPage
                  title="订单协同计划场景对比"
                  description="勾选多个订单协同计划场景，对比 Score、产能与排产关键 KPI"
                />
              }
            />

            <Route path="scheduling" element={<SchedulingModuleLayout />}>
              <Route path="rules" element={<Navigate to="/scheduling/rules/production" replace />} />
              <Route path="rules/:categoryId" element={<BusinessRulesPage moduleId="MOD-SCH" />} />
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
            <Route path="slitting/parameters" element={<SlittingParametersPage />} />
            <Route path="slitting/runs" element={<SlittingOptimizeRunPage />} />
            <Route path="slitting/studio" element={<SlittingStudioPage />} />
            <Route path="slitting/bom-studio" element={<Navigate to="/slitting/studio" replace />} />
            <Route path="slitting/plans" element={<Navigate to="/slitting/studio" replace />} />
            <Route path="slitting/workbench" element={<Navigate to="/slitting/studio" replace />} />

            <Route path="factory-calendar" element={<FactoryCalendarPage />} />
            <Route path="demand-tracking" element={<DemandTrackingPage />} />

            {/* 旧路径重定向 */}
            <Route path="business-rules/other" element={<Navigate to="/master-plan/rules/demand" replace />} />
            <Route path="master-plan/business-data" element={<Navigate to="/business-data" replace />} />
            <Route path="master-plan/business-rules" element={<Navigate to="/master-plan/rules/demand" replace />} />
            <Route path="demand" element={<Navigate to="/master-plan/demand" replace />} />
            <Route path="capacity" element={<Navigate to="/master-plan/capacity" replace />} />
            <Route path="scenario-comparison" element={<Navigate to="/master-plan/scenario-comparison" replace />} />
            <Route path="material-plan" element={<Navigate to="/master-plan/analysis/material-planning" replace />} />
            <Route path="production-plan" element={<Navigate to="/master-plan/work-orders" replace />} />
            <Route path="schedule-kitting" element={<Navigate to="/scheduling/kitting" replace />} />
            <Route path="detail-schedule" element={<Navigate to="/scheduling/detail-schedule" replace />} />
            <Route path="kitting" element={<Navigate to="/master-plan/analysis/material-planning" replace />} />
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

export default function App() {
  return (
    <AppProviders>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </AppProviders>
  );
}
