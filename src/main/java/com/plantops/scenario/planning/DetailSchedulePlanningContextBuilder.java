package com.plantops.scenario.planning;

import com.plantops.config.ParameterRegistry;
import com.plantops.config.ScheduleContractConfigService;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.persistence.entity.LineOpeningDecisionEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ContinuousProductionBindingService;
import com.plantops.scenario.KittingService;
import com.plantops.scenario.ParallelOperationBindingService;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.scenario.planning.diagnostics.DetailSchedulePlanningDiagnosticsCollector;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleContractSettings;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 详细排程推演层入口（P0–P4）：
 * <ul>
 *   <li>P0 事实装载：排程锚点、契约权重、主计划工序契约、开线决策</li>
 *   <li>P1 产线域：ScheduleLine（开/关 + 产能）</li>
 *   <li>P2 齐套推演：KittingService 消耗池 → OperationAssignment.kittingEligible</li>
 *   <li>P3 工序展开：{@link DetailScheduleAssignmentBuilder} + 主计划软契约字段</li>
 *   <li>P4 绑定规则：并行工序 / 连续生产（硬约束预处理）</li>
 * </ul>
 */
@ApplicationScoped
public class DetailSchedulePlanningContextBuilder {

    @Inject
    ParameterRegistry parameters;

    @Inject
    ScheduleContractConfigService scheduleContractConfig;

    @Inject
    KittingService kittingService;

    @Inject
    MasterPlanContractLoader masterPlanContractLoader;

    @Inject
    ParallelOperationBindingService parallelOperationBindingService;

    @Inject
    ContinuousProductionBindingService continuousProductionBindingService;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    public DetailSchedulePlanningContext build(String masterPlanVersionId) {
        return build(masterPlanVersionId, null);
    }

    public DetailSchedulePlanningContext build(String masterPlanVersionId, MaterialPlanningContext materialPlanning) {
        DetailSchedulePlanningDiagnosticsCollector diag = new DetailSchedulePlanningDiagnosticsCollector();
        LocalDate planningAnchor = LocalDate.now();
        int shiftCapacity = parameters.getInt("shift_capacity_minutes", 480);
        ScheduleContractSettings contract = scheduleContractConfig.load();
        MasterPlanContractLoader.ContractSnapshot mpContracts = masterPlanContractLoader.load(masterPlanVersionId);
        diag.set(PlanningDiagnosticCodes.DS_MP_CONTRACTS_LOADED, mpContracts.operationContracts().size());

        List<ScheduleLine> lines = loadLines(masterPlanVersionId, diag);
        MaterialPlanningContext effectiveMaterial = materialPlanning != null
                ? materialPlanning
                : materialPlanningContextBuilder.build();
        List<OperationAssignment> operations = expandOperations(
                planningAnchor,
                mpContracts.workOrderEndByWorkOrder(),
                mpContracts.operationContracts(),
                effectiveMaterial,
                diag);

        parallelOperationBindingService.applyBindings(operations);
        continuousProductionBindingService.applyBindings(operations);
        diag.scanBindingFlags(operations);

        return new DetailSchedulePlanningContext(
                planningAnchor,
                shiftCapacity,
                contract,
                lines,
                operations,
                diag.toDto(masterPlanVersionId, effectiveMaterial.inventorySnapshotId()),
                effectiveMaterial);
    }

    private List<ScheduleLine> loadLines(String masterPlanVersionId, DetailSchedulePlanningDiagnosticsCollector diag) {
        Set<String> openedLineIds = loadOpenedLines(masterPlanVersionId);
        List<ScheduleLine> lines = new ArrayList<>();
        int opened = 0;
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            boolean isOpened = openedLineIds.isEmpty() || openedLineIds.contains(line.lineId);
            if (isOpened) {
                opened++;
            }
            lines.add(new ScheduleLine(
                    line.lineId,
                    line.resourceId,
                    line.areaId,
                    isOpened,
                    line.lineCapacityPerShift));
        }
        diag.set(PlanningDiagnosticCodes.DS_SCHEDULE_LINES_TOTAL, lines.size());
        diag.set(PlanningDiagnosticCodes.DS_SCHEDULE_LINES_OPENED, opened);
        return lines;
    }

    private List<OperationAssignment> expandOperations(
            LocalDate planningAnchor,
            Map<String, LocalDate> masterPlanEndByWorkOrder,
            Map<String, MasterPlanContractLoader.OperationContract> operationContracts,
            MaterialPlanningContext materialPlanning,
            DetailSchedulePlanningDiagnosticsCollector diag) {
        List<OperationAssignment> operations = new ArrayList<>();
        int seqHint = 0;
        Map<String, BigDecimal> kittingPool = kittingService.newAvailableInventoryPool(materialPlanning.inventory());
        diag.set(PlanningDiagnosticCodes.DS_INVENTORY_PRODUCT_COUNT, materialPlanning.inventory().productCount());
        for (WorkOrderEntity wo : WorkOrderEntity.listAllOrdered()) {
            diag.increment(PlanningDiagnosticCodes.DS_WORK_ORDERS_SCANNED);
            WorkOrderScheduleContext scheduleCtx = WorkOrderScheduleContext.resolve(wo);
            if (!scheduleCtx.schedulable) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE,
                        wo.workOrderNo,
                        "工单不可排程");
                continue;
            }
            List<ProductRoutingSteps.Operation> routingOperations =
                    ProductRoutingSteps.operationsForProduct(wo.productCode);
            if (routingOperations.isEmpty()) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_NO_ROUTING,
                        wo.workOrderNo,
                        "产品 " + wo.productCode + " 无工艺");
                continue;
            }
            boolean kittingOk = kittingService.checkAndConsumeWorkOrderKitting(wo, kittingPool);
            if (!kittingOk) {
                diag.recordWarn(
                        PlanningDiagnosticCodes.WO_KITTING_SHORT,
                        wo.workOrderNo,
                        null,
                        "齐套不足，工序 kittingEligible=false");
            }
            boolean pinned = DetailScheduleAssignmentBuilder.resolvePinned(wo, businessRuleScopeService);
            LocalDate dueDate = DetailScheduleAssignmentBuilder.resolveDueDate(wo);
            LocalDate woMpEnd = masterPlanEndByWorkOrder.get(wo.workOrderNo);
            List<OperationAssignment> woOps = DetailScheduleAssignmentBuilder.buildForWorkOrder(
                    wo,
                    routingOperations,
                    kittingOk,
                    pinned,
                    dueDate,
                    woMpEnd,
                    planningAnchor,
                    operationContracts,
                    seqHint,
                    businessRuleScopeService);
            seqHint += woOps.size();
            diag.increment(PlanningDiagnosticCodes.DS_WORK_ORDERS_INCLUDED);
            for (OperationAssignment op : woOps) {
                if (!op.isKittingEligible()) {
                    diag.increment(PlanningDiagnosticCodes.DS_OPERATIONS_KITTING_INELIGIBLE);
                }
                if (op.getMpContractStartDate() != null) {
                    diag.increment(PlanningDiagnosticCodes.DS_OPERATIONS_WITH_MP_CONTRACT);
                } else if (op.getMpTargetEndDate() != null) {
                    diag.increment(PlanningDiagnosticCodes.DS_OPERATIONS_MP_TARGET_FALLBACK);
                    diag.recordWarn(
                            PlanningDiagnosticCodes.OP_MP_TARGET_FALLBACK,
                            wo.workOrderNo,
                            op.getOperationId(),
                            "无工序级主计划契约，使用工单末槽回退目标日 " + op.getMpTargetEndDate());
                }
            }
            operations.addAll(woOps);
        }
        diag.set(PlanningDiagnosticCodes.DS_OPERATIONS_TOTAL, operations.size());
        return operations;
    }

    private Set<String> loadOpenedLines(String masterPlanVersionId) {
        if (masterPlanVersionId == null || masterPlanVersionId.isBlank()) {
            return Set.of();
        }
        return LineOpeningDecisionEntity
                .find("workspaceId = ?1 and planVersionId = ?2 and opened = true",
                        LineOpeningDecisionEntity.ws(), masterPlanVersionId)
                .<LineOpeningDecisionEntity>list().stream()
                .map(o -> o.lineId)
                .collect(Collectors.toSet());
    }
}
