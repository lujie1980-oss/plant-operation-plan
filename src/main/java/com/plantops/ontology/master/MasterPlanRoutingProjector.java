package com.plantops.ontology.master;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepDetailDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepInputMaterialDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOnStandardResourceDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOutputMaterialDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code MaterialEntity} + {@code ProductResourceEntity} + {@code BomComponentEntity}
 * 投影主计划 Routing 主数据模型。
 */
@ApplicationScoped
public class MasterPlanRoutingProjector {

    public RoutingDto projectRoutingHeader(String pispId, String productCode) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode);
        String routingName = resolveRoutingName(productCode);
        return new RoutingDto(
                OntologyIds.routingId(pispId),
                pispId,
                productCode,
                routingName,
                operations.size(),
                1);
    }

    public List<RoutingDto> listRoutingsForPisp(String pispId, String productCode) {
        if (!hasRouting(productCode)) {
            return List.of();
        }
        return List.of(projectRoutingHeader(pispId, productCode));
    }

    public List<RoutingStepDetailDto> projectRoutingSteps(String pispId, String productCode) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode);
        if (operations.isEmpty()) {
            return List.of();
        }

        String routingId = OntologyIds.routingId(pispId);
        List<RoutingStepDetailDto> steps = new ArrayList<>(operations.size());
        for (int i = 0; i < operations.size(); i++) {
            ProductRoutingSteps.Operation operation = operations.get(i);
            String stepId = OntologyIds.routingStepId(pispId, operation.sequenceNo());
            List<RoutingStepOnStandardResourceDto> resources = operation.resourceOptions().stream()
                    .map(option -> new RoutingStepOnStandardResourceDto(
                            OntologyIds.routingStepOnStandardResourceId(stepId, option.resourceId()),
                            stepId,
                            option.resourceId(),
                            option.resourcePriority(),
                            option.setupTimeMinutes(),
                            option.processTimeSeconds()))
                    .toList();

            List<RoutingStepInputMaterialDto> inputs = i == 0
                    ? projectInputMaterials(stepId, productCode)
                    : List.of();
            List<RoutingStepOutputMaterialDto> outputs = i == operations.size() - 1
                    ? List.of(new RoutingStepOutputMaterialDto(
                            OntologyIds.routingStepOutputMaterialId(stepId, productCode),
                            stepId,
                            productCode,
                            1.0))
                    : List.of();

            steps.add(new RoutingStepDetailDto(
                    stepId,
                    routingId,
                    operation.sequenceNo(),
                    operation.operationName(),
                    resources,
                    inputs,
                    outputs));
        }
        return steps;
    }

    public static boolean hasRouting(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        return !masterDataOperations(productCode).isEmpty();
    }

    /**
     * 主计划数据模型仅投影已维护的 {@code product_resource}，不回退演示用 {@code ProductRoutingCatalog}。
     */
    static List<ProductRoutingSteps.Operation> masterDataOperations(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }
        if (!ProductResourceEntity.findByProductOrdered(productCode).isEmpty()) {
            return ProductRoutingSteps.operationsForProduct(productCode);
        }
        return List.of();
    }

    private static List<RoutingStepInputMaterialDto> projectInputMaterials(String stepId, String productCode) {
        List<BomComponentEntity> components = BomComponentEntity.findChildren(productCode, productCode);
        if (components.isEmpty()) {
            return List.of();
        }
        List<RoutingStepInputMaterialDto> inputs = new ArrayList<>(components.size());
        for (BomComponentEntity bom : components) {
            double qty = bom.componentQty != null ? bom.componentQty.doubleValue() : 1.0;
            inputs.add(new RoutingStepInputMaterialDto(
                    OntologyIds.routingStepInputMaterialId(stepId, bom.componentProductCode),
                    stepId,
                    bom.componentProductCode,
                    qty,
                    bom.isCriticalComponent));
        }
        return inputs;
    }

    private static String resolveRoutingName(String productCode) {
        MaterialEntity material = MaterialEntity.findByCode(productCode);
        if (material != null && material.materialName != null && !material.materialName.isBlank()) {
            return material.materialName + " 工艺";
        }
        return productCode + " 工艺";
    }
}
