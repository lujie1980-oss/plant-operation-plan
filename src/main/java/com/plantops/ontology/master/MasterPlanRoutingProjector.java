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
        List<Integer> priorities = ProductRoutingSteps.routingPathPrioritiesForProduct(productCode);
        int pathPriority = priorities.isEmpty() ? 1 : priorities.get(0);
        return projectRoutingHeader(pispId, productCode, pathPriority);
    }

    public RoutingDto projectRoutingHeader(String pispId, String productCode, int pathPriority) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode, pathPriority);
        String routingName = resolveRoutingName(productCode, pathPriority);
        boolean multiPath = ProductRoutingSteps.routingPathPrioritiesForProduct(productCode).size() > 1;
        return new RoutingDto(
                OntologyIds.routingId(pispId, pathPriority, multiPath),
                pispId,
                productCode,
                routingName,
                operations.size(),
                pathPriority);
    }

    public List<RoutingDto> listRoutingsForPisp(String pispId, String productCode) {
        if (!hasRouting(productCode)) {
            return List.of();
        }
        List<Integer> priorities = ProductRoutingSteps.routingPathPrioritiesForProduct(productCode);
        boolean multiPath = priorities.size() > 1;
        List<RoutingDto> routings = new ArrayList<>(priorities.size());
        for (int pathPriority : priorities) {
            List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode, pathPriority);
            if (operations.isEmpty()) {
                continue;
            }
            routings.add(projectRoutingHeader(pispId, productCode, pathPriority));
        }
        if (routings.isEmpty()) {
            return List.of();
        }
        return routings;
    }

    public List<RoutingStepDetailDto> projectRoutingSteps(String pispId, String productCode) {
        List<Integer> priorities = ProductRoutingSteps.routingPathPrioritiesForProduct(productCode);
        int pathPriority = priorities.isEmpty() ? 1 : priorities.get(0);
        return projectRoutingSteps(pispId, productCode, pathPriority);
    }

    public List<RoutingStepDetailDto> projectRoutingSteps(
            String pispId, String productCode, int pathPriority) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode, pathPriority);
        if (operations.isEmpty()) {
            return List.of();
        }

        boolean multiPath = ProductRoutingSteps.routingPathPrioritiesForProduct(productCode).size() > 1;
        String routingId = OntologyIds.routingId(pispId, pathPriority, multiPath);
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
        return !ProductResourceEntity.findByProductOrdered(productCode).isEmpty();
    }

    static List<ProductRoutingSteps.Operation> masterDataOperations(String productCode) {
        return masterDataOperations(productCode, 1);
    }

    static List<ProductRoutingSteps.Operation> masterDataOperations(String productCode, int pathPriority) {
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }
        if (ProductResourceEntity.findByProductOrdered(productCode).isEmpty()) {
            return List.of();
        }
        return ProductRoutingSteps.operationsForProduct(productCode, pathPriority);
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

    private static String resolveRoutingName(String productCode, int pathPriority) {
        MaterialEntity material = MaterialEntity.findByCode(productCode);
        String base;
        if (material != null && material.materialName != null && !material.materialName.isBlank()) {
            base = material.materialName + " 工艺";
        } else {
            base = productCode + " 工艺";
        }
        if (pathPriority > 1) {
            return base + " · 路径 " + pathPriority;
        }
        return base;
    }
}
