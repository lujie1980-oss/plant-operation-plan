package com.plantops.ontology.master;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepDetailDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepInputMaterialDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOnStandardResourceDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOutputMaterialDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.masterdata.internal.MdRoutingReadService;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code md_*}（优先）或 legacy {@code MaterialEntity} + {@code ProductResourceEntity} + {@code BomComponentEntity}
 * 投影主计划 Routing 主数据模型。
 */
@ApplicationScoped
public class MasterPlanRoutingProjector {

    @jakarta.inject.Inject
    MdRoutingReadService mdRouting;

    public RoutingDto projectRoutingHeader(String pispId, String productCode) {
        List<Integer> priorities = routingPathPriorities(productCode);
        int pathPriority = priorities.isEmpty() ? 1 : priorities.get(0);
        return projectRoutingHeader(pispId, productCode, pathPriority);
    }

    private List<Integer> routingPathPriorities(String productCode) {
        if (mdRouting.hasRouting(productCode)) {
            return mdRouting.routingPathPrioritiesForProduct(productCode);
        }
        return ProductRoutingSteps.routingPathPrioritiesForProduct(productCode);
    }

    public RoutingDto projectRoutingHeader(String pispId, String productCode, int pathPriority) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode, pathPriority);
        String routingName = resolveRoutingName(productCode, pathPriority);
        boolean multiPath = routingPathPriorities(productCode).size() > 1;
        return new RoutingDto(
                OntologyIds.routingId(pispId, pathPriority, multiPath),
                pispId,
                productCode,
                routingName,
                operations.size(),
                pathPriority);
    }

    public List<RoutingDto> listRoutingsForPisp(String pispId, String productCode) {
        if (!hasRoutingForProduct(productCode)) {
            return List.of();
        }
        List<Integer> priorities = routingPathPriorities(productCode);
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
        List<Integer> priorities = routingPathPriorities(productCode);
        int pathPriority = priorities.isEmpty() ? 1 : priorities.get(0);
        return projectRoutingSteps(pispId, productCode, pathPriority);
    }

    public List<RoutingStepDetailDto> projectRoutingSteps(
            String pispId, String productCode, int pathPriority) {
        List<ProductRoutingSteps.Operation> operations = masterDataOperations(productCode, pathPriority);
        if (operations.isEmpty()) {
            return List.of();
        }

        boolean multiPath = routingPathPriorities(productCode).size() > 1;
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
                    ? projectInputMaterials(stepId, productCode, pathPriority)
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

    /** Legacy static entry; prefer {@link #hasRoutingForProduct(String)} when injected. */
    public static boolean hasRouting(String productCode) {
        return ProductResourceEntity.hasRouting(productCode);
    }

    public boolean hasRoutingForProduct(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        if (mdRouting.hasRouting(productCode)) {
            return true;
        }
        return ProductResourceEntity.hasRouting(productCode);
    }

    private List<ProductRoutingSteps.Operation> masterDataOperations(String productCode, int pathPriority) {
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }
        if (mdRouting.hasRouting(productCode)) {
            List<ProductRoutingSteps.Operation> mdOps = mdRouting.operationsForProduct(productCode, pathPriority);
            if (!mdOps.isEmpty()) {
                return mdOps;
            }
        }
        if (ProductResourceEntity.findByProductOrdered(productCode).isEmpty()) {
            return List.of();
        }
        return ProductRoutingSteps.operationsForProduct(productCode, pathPriority);
    }

    private List<RoutingStepInputMaterialDto> projectInputMaterials(
            String stepId, String productCode, int pathPriority) {
        if (mdRouting.hasRouting(productCode)) {
            List<MdRoutingStepImEntity> mdInputs = mdRouting.inputMaterialsForFirstStep(productCode, pathPriority);
            if (!mdInputs.isEmpty()) {
                List<RoutingStepInputMaterialDto> inputs = new ArrayList<>(mdInputs.size());
                for (MdRoutingStepImEntity im : mdInputs) {
                    double qty = im.componentQty != null ? im.componentQty.doubleValue() : 1.0;
                    inputs.add(new RoutingStepInputMaterialDto(
                            OntologyIds.routingStepInputMaterialId(stepId, im.componentProductCode),
                            stepId,
                            im.componentProductCode,
                            qty,
                            false));
                }
                return inputs;
            }
        }
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

    private String resolveRoutingName(String productCode, int pathPriority) {
        if (mdRouting.hasRouting(productCode)) {
            String base = mdRouting.routingName(productCode, pathPriority);
            if (pathPriority > 1) {
                return base + " · 路径 " + pathPriority;
            }
            return base;
        }
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
