package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationOutputMaterial;
import com.plantops.ontology.supply.OperationPostProcessingResolver;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.OperationTimeWindowDerivations;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.RuleScopeHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在本体 {@link OntologyGraph.Builder} 中物化合成 {@link SupplyOrder} 的 PlanUnit / Operation / Supply / BOM Demand。
 */
@ApplicationScoped
public class OntologySupplyOrderMaterializer {

    @Inject
    RuleScopeHelper ruleScopeHelper;

    public List<Demand> materialize(
            OntologyGraph.Builder builder,
            SupplyOrder supplyOrder,
            String finishedProduct,
            LocalDate planningStart,
            UpstreamFulfillmentSession session) {
        String planUnitId = OntologyIds.planUnitId(supplyOrder.getId(), 0);
        builder.planUnit(new PlanUnit(planUnitId, supplyOrder.getId(), supplyOrder.getQuantity(), 0));

        List<ProductRoutingSteps.Operation> routingOps = session.routingFor(supplyOrder.getProductCode());
        List<Operation> operations = new ArrayList<>();
        for (int i = 0; i < routingOps.size(); i++) {
            ProductRoutingSteps.Operation routingOp = routingOps.get(i);
            String operationId = OntologyIds.operationId(supplyOrder.getId(), i);
            Operation operation = new Operation(
                    operationId,
                    supplyOrder.getId(),
                    i,
                    routingOp.operationName());
            operation.setPlanUnitId(planUnitId);
            operation.setRoutingSequenceNo(routingOp.sequenceNo());
            operation.setSegmentIndex(0);
            operation.setLastSegment(i == routingOps.size() - 1);
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
                builder.operationOnStandardResource(primaryOosr);
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
            operations.add(operation);
        }

        if (operations.isEmpty()) {
            return List.of();
        }

        OperationTimeWindowDerivations.recalculateFallback(
                operations,
                planningStart,
                supplyOrder.getNeedDate());

        Operation lastOp = operations.get(operations.size() - 1);
        String supplyId = OntologyIds.supplyId(supplyOrder.getId(), 0);
        builder.supply(new Supply(
                supplyId,
                supplyOrder.getProductCode(),
                supplyOrder.getPispId(),
                supplyOrder.getQuantity(),
                supplyOrder.getId()));
        builder.operationOutputMaterial(new OperationOutputMaterial(
                OntologyIds.operationOutputMaterialId(lastOp.getId(), supplyId),
                lastOp.getId(),
                supplyId,
                supplyOrder.getQuantity()));

        List<Demand> bomDemands = new ArrayList<>();
        Operation inputOp = operations.get(0);
        String finished = finishedProduct != null && !finishedProduct.isBlank()
                ? finishedProduct
                : supplyOrder.getProductCode();
        OntologyLoader.ensureProduct(builder, supplyOrder.getProductCode());
        for (BomComponentEntity bom : session.bomChildren(supplyOrder.getProductCode())) {
            if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                continue;
            }
            double componentQty = bom.componentQty != null
                    ? bom.componentQty.doubleValue() * supplyOrder.getQuantity()
                    : supplyOrder.getQuantity();
            String demandId = OntologyIds.demandFromBomId(supplyOrder.getId(), bom.componentProductCode);
            OntologyLoader.ensureProduct(builder, bom.componentProductCode);
            Demand bomDemand = new Demand(
                    demandId,
                    bom.componentProductCode,
                    OntologyIds.pispId(bom.componentProductCode),
                    componentQty,
                    supplyOrder.getNeedDate(),
                    5,
                    DemandSourceType.BOM_COMPONENT,
                    supplyOrder.getId());
            builder.demand(bomDemand);
            builder.operationInputMaterial(new OperationInputMaterial(
                    OntologyIds.operationInputMaterialId(inputOp.getId(), demandId),
                    inputOp.getId(),
                    demandId,
                    componentQty));
            bomDemands.add(bomDemand);
        }
        return bomDemands;
    }

    public static boolean hasManufacturingRouting(String productCode) {
        return ProductResourceEntity.hasRouting(productCode);
    }
}
