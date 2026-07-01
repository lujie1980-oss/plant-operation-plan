package com.plantops.ontology;

import com.plantops.config.OntologyRestorerReadFeature;
import com.plantops.ontology.master.Product;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.period.StandardResourcePeriodLoader;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.OntologyUpstreamFulfillmentBuilder;
import com.plantops.ontology.fulfillment.SupplyChainLoader;
import com.plantops.ontology.fulfillment.UpstreamFulfillmentSession;
import com.plantops.ontology.scheduling.SchedulingSlot;
import com.plantops.ontology.scheduling.SchedulingSlotExpander;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationPostProcessingResolver;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import com.plantops.ontology.supply.OperationParallelBindingService;
import com.plantops.ontology.supply.OperationTimingBridgeService;
import com.plantops.rol.PispRolling;
import com.plantops.scenario.OntologyUpstreamChainWorkOrderPersister;
import com.plantops.scenario.planning.PlanVersionAllocationHydrator;
import com.plantops.scenario.planning.PlanVersionEntRcaOccupancy;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.RuleScopeHelper;
import com.plantops.scenario.WorkOrderService;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OntologyLoader {

    @Inject
    SchedulingSlotExpander schedulingSlotExpander;

    @Inject
    SupplyChainLoader supplyChainLoader;

    @Inject
    OperationTimingBridgeService operationTimingBridgeService;

    @Inject
    OperationParallelBindingService operationParallelBindingService;

    @Inject
    PlanVersionAllocationHydrator planVersionAllocationHydrator;

    @Inject
    OntologyRestorerReadFeature restorerReadFeature;

    @Inject
    OntologyUpstreamFulfillmentBuilder upstreamFulfillmentBuilder;

    @Inject
    OntologyUpstreamChainWorkOrderPersister upstreamChainWorkOrderPersister;

    @Inject
    RuleScopeHelper ruleScopeHelper;

    /**
     * @deprecated P4 迁移期：规范读路径为 {@link WorkspaceAuthoritativeOntologyGraphService}
     *             + {@link com.plantops.ontology.persistence.OntologyRestorer}；本方法仅作 legacy 装载边界。
     */
    @Deprecated
    public OntologyGraph loadForWorkspace(LocalDate planningStart) {
        LocalDate effectiveStart = planningStart != null ? planningStart : LocalDate.now();
        return buildGraph(effectiveStart);
    }

    /**
     * 上游满足链：本体求解不在长事务中执行；仅 prepare/persist 使用独立的短事务（REQUIRES_NEW），
     * 避免长时间占锁导致 H2 超时与 503。
     */
    /**
     * 单交付行只读快照（过渡）。<strong>禁止</strong>作为 simulate / optimize / confirm 真相源（ADR-07 / RULE-SES-04）。
     * 满足链请使用 {@link WorkspaceAuthoritativeOntologyGraphService} + {@link OntologyFulfillmentChainProjector}。
     *
     * @deprecated 规范路径为权威全厂图 + DTO 投影；保留仅供遗留只读调用排查。
     */
    @Deprecated
    public OntologyGraph buildDeliveryFulfillmentProjectionGraph(String deliveryId, LocalDate planningStart) {
        LocalDate effectiveStart = planningStart != null ? planningStart : LocalDate.now();
        OntologyIds.CustomerOrderLineDeliveryKey deliveryKey =
                OntologyIds.parseCustomerOrderLineDeliveryId(deliveryId);
        UpstreamFulfillmentSession session =
                UpstreamFulfillmentSession.create(deliveryKey, Set.of(), ruleScopeHelper);

        OntologyGraph.Builder builder = createUpstreamScopedBuilder(effectiveStart, deliveryKey, session);
        List<SupplyOrder> supplyOrders = new ArrayList<>(builder.supplyOrdersById().values());
        loadOperations(builder, supplyOrders);
        supplyChainLoader.expandDemandsAndStructureOnly(builder, supplyOrders);
        supplyChainLoader.runFulfillmentPegging(builder, supplyOrders);

        OntologyGraph graph = builder.build();
        operationTimingBridgeService.applyToGraph(graph, effectiveStart);
        return graph;
    }

    @Deprecated
    public OntologyGraph buildDeliveryFulfillmentProjectionGraph(String deliveryId, String planVersionId) {
        LocalDate planningStart = LocalDate.now();
        if (planVersionId != null && !planVersionId.isBlank()) {
            PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(planVersionId);
            if (planVersion != null) {
                planningStart = resolvePlanningStart(planVersion);
            }
        }
        OntologyGraph graph = buildDeliveryFulfillmentProjectionGraph(deliveryId, planningStart);
        if (planVersionId != null && !planVersionId.isBlank()) {
            planVersionAllocationHydrator.hydrate(graph, planVersionId);
        }
        return graph;
    }

    public OntologyGraph buildUpstreamFulfillmentGraph(String deliveryId, LocalDate planningStart) {
        LocalDate effectiveStart = planningStart != null ? planningStart : LocalDate.now();
        OntologyIds.CustomerOrderLineDeliveryKey deliveryKey =
                OntologyIds.parseCustomerOrderLineDeliveryId(deliveryId);
        if (deliveryKey != null) {
            upstreamChainWorkOrderPersister.prepareOrderLineRebuild(deliveryKey);
        }

        Set<String> workOrderNosBeforeBuild = snapshotWorkOrderNos();
        UpstreamFulfillmentSession session =
                UpstreamFulfillmentSession.create(deliveryKey, workOrderNosBeforeBuild, ruleScopeHelper);

        OntologyGraph.Builder builder = createUpstreamScopedBuilder(effectiveStart, deliveryKey, session);
        upstreamFulfillmentBuilder.buildForDelivery(builder, deliveryId, effectiveStart, session);
        upstreamChainWorkOrderPersister.persistNewSupplyOrders(builder, deliveryKey, workOrderNosBeforeBuild, session);
        return builder.build();
    }

    /**
     * 上游建链专用：只装本交付行需求 + 可挂接库存/工单供应，不对全场景工单做工艺/BOM 展开。
     */
    private OntologyGraph.Builder createUpstreamScopedBuilder(
            LocalDate planningStart,
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            UpstreamFulfillmentSession session) {
        OntologyGraph.Builder builder = OntologyGraph.builder()
                .defaultStockingPoint(StockingPoint.defaultFg())
                .periodsOrdered(buildPeriods(planningStart));

        supplyChainLoader.loadSingleCustomerDelivery(builder, deliveryKey);
        loadOpenSupplyOrdersForPegging(builder, deliveryKey, session);
        return builder;
    }

    private static void loadOpenSupplyOrdersForPegging(
            OntologyGraph.Builder builder,
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            UpstreamFulfillmentSession session) {
        Set<String> linePeggedWorkOrderNos = Set.of();
        if (deliveryKey != null) {
            linePeggedWorkOrderNos = WorkOrderPeggingEntity
                    .findByOrderLine(deliveryKey.salesOrderNo(), deliveryKey.salesOrderLineNo())
                    .stream()
                    .map(peg -> peg.workOrderNo)
                    .collect(Collectors.toUnmodifiableSet());
        }
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (!isOpenWorkOrder(wo)) {
                continue;
            }
            if (!session.isRelevantOpenWorkOrder(wo, deliveryKey, linePeggedWorkOrderNos)) {
                continue;
            }
            SupplyOrder supplyOrder = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);
            if (supplyOrder == null) {
                continue;
            }
            ensureProduct(builder, supplyOrder.getProductCode());
            builder.supplyOrder(supplyOrder);
            String supplyId = OntologyIds.supplyId(supplyOrder.getId(), 0);
            if (!builder.suppliesById().containsKey(supplyId)) {
                builder.supply(new Supply(
                        supplyId,
                        supplyOrder.getProductCode(),
                        supplyOrder.getPispId(),
                        supplyOrder.getQuantity(),
                        supplyOrder.getId()));
            }
        }
    }

    public static void ensureProduct(OntologyGraph.Builder builder, String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return;
        }
        builder.product(new Product(productCode, productCode));
        builder.pisp(new ProductInStockingPoint(
                OntologyIds.pispId(productCode),
                productCode,
                StockingPoint.DEFAULT_FG,
                productCode));
    }

    private static Set<String> snapshotWorkOrderNos() {
        Set<String> workOrderNos = new LinkedHashSet<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            workOrderNos.add(wo.workOrderNo);
        }
        return workOrderNos;
    }

    /**
     * @deprecated P4 迁移期：规范读路径为 {@link WorkspaceAuthoritativeOntologyGraphService}
     *             + {@link com.plantops.ontology.persistence.OntologyRestorer}。
     */
    @Deprecated
    public OntologyGraph loadForPlanVersion(String planVersionId) {
        PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(planVersionId);
        if (planVersion == null) {
            throw new NotFoundException("Plan version not found: " + planVersionId);
        }
        LocalDate planningStart = resolvePlanningStart(planVersion);
        OntologyGraph graph = buildGraph(planningStart);
        hydrateLegacyAllocationsIfNeeded(graph, planVersionId);
        return graph;
    }

    private void hydrateLegacyAllocationsIfNeeded(OntologyGraph graph, String planVersionId) {
        if (shouldSkipLegacyAllocationHydration(planVersionId)) {
            return;
        }
        planVersionAllocationHydrator.hydrateFromLegacyAllocations(graph, planVersionId);
    }

    private boolean shouldSkipLegacyAllocationHydration(String planVersionId) {
        if (!restorerReadFeature.enabled()) {
            return false;
        }
        return PlanVersionEntRcaOccupancy.hasCommittedEntRca(
                WorkspaceResolver.currentWorkspaceId(), planVersionId);
    }

    /**
     * 加载交付相关图；若指定 {@code planVersionId} 则反灌已发布 allocation → Operation planned + SRP。
     */
    public OntologyGraph loadForDelivery(String deliveryId, String planVersionId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new NotFoundException("deliveryId required");
        }
        if (planVersionId != null && !planVersionId.isBlank()) {
            return loadForPlanVersion(planVersionId);
        }
        return loadForWorkspace(LocalDate.now());
    }

    /**
     * 轻量加载：仅 Period + SRP + 主计划 allocation 反灌，供产能甘特读取本体 reservedCapacity。
     *
     * @deprecated P4 迁移期：规范读路径为 {@link WorkspaceAuthoritativeOntologyGraphService#getSrpCapacityOrLoad}
     */
    @Deprecated
    public OntologyGraph loadSrpCapacityForPlanVersion(String planVersionId) {
        LocalDate planningStart = LocalDate.now();
        if (planVersionId != null && !planVersionId.isBlank()) {
            PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(planVersionId);
            if (planVersion != null) {
                planningStart = resolvePlanningStart(planVersion);
            }
        }
        List<Period> periods = buildPeriods(planningStart);
        PeriodIndex periodIndex = PeriodIndex.of(periods);
        OntologyGraph.Builder builder = OntologyGraph.builder().periodsOrdered(periods);
        loadStandardResourcePeriods(builder, periods, periodIndex);
        OntologyGraph graph = builder.build();
        if (planVersionId != null && !planVersionId.isBlank()) {
            hydrateLegacyAllocationsIfNeeded(graph, planVersionId);
        }
        return graph;
    }

    private static LocalDate resolvePlanningStart(PlanVersionEntity planVersion) {
        // PlanVersionEntity has no planningStart field in M1; default to today.
        return LocalDate.now();
    }

    private OntologyGraph.Builder createFulfillmentChainBuilder(LocalDate planningStart) {
        Set<String> productCodes = collectProductCodes();
        OntologyGraph.Builder builder = OntologyGraph.builder()
                .defaultStockingPoint(StockingPoint.defaultFg())
                .periodsOrdered(buildPeriods(planningStart));

        for (String productCode : productCodes) {
            builder.product(new Product(productCode, productCode));
            builder.pisp(new ProductInStockingPoint(
                    OntologyIds.pispId(productCode),
                    productCode,
                    StockingPoint.DEFAULT_FG,
                    productCode));
        }

        List<SupplyOrder> supplyOrders = loadOpenSupplyOrders(builder);
        loadOperations(builder, supplyOrders);
        supplyChainLoader.expandDemandsAndStructureOnly(builder, supplyOrders);
        return builder;
    }

    private static List<SupplyOrder> loadOpenSupplyOrders(OntologyGraph.Builder builder) {
        List<SupplyOrder> supplyOrders = new ArrayList<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (!isOpenWorkOrder(wo)) {
                continue;
            }
            SupplyOrder supplyOrder = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);
            if (supplyOrder != null) {
                supplyOrders.add(supplyOrder);
                builder.supplyOrder(supplyOrder);
            }
        }
        return supplyOrders;
    }

    private OntologyGraph buildGraph(LocalDate planningStart) {
        Set<String> productCodes = collectProductCodes();
        List<Period> periods = buildPeriods(planningStart);
        PeriodIndex periodIndex = PeriodIndex.of(periods);
        OntologyGraph.Builder builder = createFulfillmentChainBuilder(planningStart);
        builder.periodsOrdered(periods);

        List<SupplyOrder> supplyOrders = new ArrayList<>(builder.supplyOrdersById().values());
        supplyChainLoader.runFulfillmentPegging(builder, supplyOrders);

        Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId = new LinkedHashMap<>();
        for (String productCode : productCodes) {
            String pispId = OntologyIds.pispId(productCode);
            double openingOnHand = sumInventoryOnHand(productCode);
            List<ProductInStockingPointPeriod> chain = new ArrayList<>(periods.size());
            for (int i = 0; i < periods.size(); i++) {
                Period period = periods.get(i);
                ProductInStockingPointPeriod pispp = new ProductInStockingPointPeriod(
                        OntologyIds.pisppId(pispId, period.getSequenceNr()),
                        pispId,
                        period.getId());
                if (i == 0) {
                    pispp.setOnHand(openingOnHand);
                    pispp.recalculatePlanningFields();
                }
                chain.add(pispp);
                builder.pispPeriod(pispp);
            }
            pisppChainByPispId.put(pispId, chain);
        }

        aggregateSupplyIntoPispp(supplyOrders, periodIndex, pisppChainByPispId);
        aggregateDemandsIntoPispp(builder.demandsById().values(), periodIndex, pisppChainByPispId);
        for (List<ProductInStockingPointPeriod> chain : pisppChainByPispId.values()) {
            PispRolling.rollChain(chain);
        }

        loadStandardResourcePeriods(builder, periods, periodIndex);

        List<SchedulingSlot> schedulingSlots = schedulingSlotExpander.expand(
                planningStart, ProductionResourceEntity.routingResourceIds());
        builder.schedulingSlotsOrdered(schedulingSlots);

        OntologyGraph graph = builder.build();
        operationTimingBridgeService.applyToGraph(graph, planningStart);
        operationParallelBindingService.applyToGraph(graph);
        return graph;
    }

    private static void loadOperations(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        for (SupplyOrder supplyOrder : supplyOrders) {
            List<ProductRoutingSteps.Operation> routingOps =
                    ProductRoutingSteps.operationsForProduct(supplyOrder.getProductCode());
            BigDecimal quantity = BigDecimal.valueOf(supplyOrder.getQuantity());
            for (int i = 0; i < routingOps.size(); i++) {
                ProductRoutingSteps.Operation routingOp = routingOps.get(i);
                String operationId = OntologyIds.operationId(supplyOrder.getId(), i);
                Operation operation = new Operation(
                        operationId,
                        supplyOrder.getId(),
                        i,
                        routingOp.operationName());
                operation.setRoutingSequenceNo(routingOp.sequenceNo());
                operation.setSegmentIndex(0);
                operation.setLastSegment(false);
                operation.setLocked(false);
                ProductRoutingSteps.ResourceOption primaryOption = routingOp.resourceOptions().isEmpty()
                        ? null
                        : routingOp.resourceOptions().get(0);
                if (primaryOption != null) {
                    OperationOnStandardResource primaryOosr = new OperationOnStandardResource(
                            OntologyIds.operationOnStandardResourceId(operationId, primaryOption.resourceId()),
                            operationId,
                            primaryOption.resourceId(),
                            OperationResourceBinding.defaultPriority(primaryOption.resourcePriority()),
                            primaryOption.setupTimeMinutes(),
                            OperationResourceBinding.processTimeSeconds(primaryOption.processTimeSeconds()));
                    OperationResourceBinding.applyPrimaryTiming(
                            operation, primaryOosr, supplyOrder.getQuantity());
                }
                if (i == routingOps.size() - 1) {
                    operation.setPostprocessingTime(OperationPostProcessingResolver.postprocessingSeconds(
                            supplyOrder.getProductCode(), routingOp.operationName()));
                }
                builder.operation(operation);
                for (ProductRoutingSteps.ResourceOption option : routingOp.resourceOptions()) {
                    if (option.resourceId() == null || option.resourceId().isBlank()) {
                        continue;
                    }
                    builder.operationOnStandardResource(new OperationOnStandardResource(
                            OntologyIds.operationOnStandardResourceId(operationId, option.resourceId()),
                            operationId,
                            option.resourceId(),
                            OperationResourceBinding.defaultPriority(option.resourcePriority()),
                            option.setupTimeMinutes(),
                            OperationResourceBinding.processTimeSeconds(option.processTimeSeconds())));
                }
            }
        }
    }

    private static void loadStandardResourcePeriods(
            OntologyGraph.Builder builder, List<Period> periods, PeriodIndex periodIndex) {
        StandardResourcePeriodLoader.load(builder, periods, periodIndex);
    }

    private static Set<String> collectProductCodes() {
        Set<String> productCodes = new LinkedHashSet<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (wo.productCode != null && !wo.productCode.isBlank()) {
                productCodes.add(wo.productCode);
            }
        }
        for (MaterialEntity material : MaterialEntity.listInWorkspace()) {
            if (material.materialCode != null && !material.materialCode.isBlank()) {
                productCodes.add(material.materialCode);
            }
        }
        for (InventoryEntity inventory : InventoryEntity.listInWorkspace()) {
            if (inventory.productCode != null && !inventory.productCode.isBlank()) {
                productCodes.add(inventory.productCode);
            }
        }
        for (SalesOrderLineEntity line : SalesOrderLineEntity.listInWorkspace()) {
            if (line.productCode != null && !line.productCode.isBlank()) {
                productCodes.add(line.productCode);
            }
        }
        for (com.plantops.persistence.entity.ForecastDemandEntity fc :
                com.plantops.persistence.entity.ForecastDemandEntity.listInWorkspace()) {
            if (fc.productCode != null && !fc.productCode.isBlank()) {
                productCodes.add(fc.productCode);
            }
        }
        for (BomComponentEntity bom : BomComponentEntity.listInWorkspace()) {
            if (bom.parentProductCode != null && !bom.parentProductCode.isBlank()) {
                productCodes.add(bom.parentProductCode);
            }
            if (bom.componentProductCode != null && !bom.componentProductCode.isBlank()) {
                productCodes.add(bom.componentProductCode);
            }
            if (bom.finishedProductCode != null && !bom.finishedProductCode.isBlank()) {
                productCodes.add(bom.finishedProductCode);
            }
        }
        return productCodes;
    }

    private static List<Period> buildPeriods(LocalDate planningStart) {
        SystemParameterEntity specRow = SystemParameterEntity.findByParamId("ontology_period_sequence");
        String specText = specRow != null ? specRow.paramValue : null;
        return PeriodSequenceSpec.parseOrDefault(specText).expand(planningStart);
    }

    private static double sumInventoryOnHand(String productCode) {
        double total = 0.0;
        for (InventoryEntity row : InventoryEntity.findByProduct(productCode)) {
            if (row.onhandQty != null) {
                total += row.onhandQty.doubleValue();
            }
        }
        return total;
    }

    private static boolean isOpenWorkOrder(WorkOrderEntity wo) {
        return wo.dispatchStatus == null
                || !WorkOrderService.DISPATCH_DISPATCHED.equals(wo.dispatchStatus);
    }

    private static void aggregateSupplyIntoPispp(
            List<SupplyOrder> supplyOrders,
            PeriodIndex periodIndex,
            Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId) {
        for (SupplyOrder supplyOrder : supplyOrders) {
            List<ProductInStockingPointPeriod> chain = pisppChainByPispId.get(supplyOrder.getPispId());
            if (chain == null) {
                continue;
            }
            ProductInStockingPointPeriod pispp = chain.get(periodIndex.sequenceFor(supplyOrder.getNeedDate()));
            double added = supplyOrder.getQuantity();
            pispp.setPlannedSupplyTotalMrp(pispp.getPlannedSupplyTotalMrp() + added);
            pispp.setPlannedSupplyTotal(pispp.getPlannedSupplyTotal() + added);
        }
    }

    private static void aggregateDemandsIntoPispp(
            Iterable<Demand> demands,
            PeriodIndex periodIndex,
            Map<String, List<ProductInStockingPointPeriod>> pisppChainByPispId) {
        for (Demand demand : demands) {
            if (demand.getNeedDate() == null || demand.getPispId() == null) {
                continue;
            }
            List<ProductInStockingPointPeriod> chain = pisppChainByPispId.get(demand.getPispId());
            if (chain == null) {
                continue;
            }
            ProductInStockingPointPeriod pispp = chain.get(periodIndex.sequenceFor(demand.getNeedDate()));
            pispp.setPlannedDemandQuantityTotal(
                    pispp.getPlannedDemandQuantityTotal() + demand.getQuantity());
        }
    }
}
