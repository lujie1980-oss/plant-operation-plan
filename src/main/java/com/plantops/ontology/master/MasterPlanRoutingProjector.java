package com.plantops.ontology.master;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepDetailDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepInputMaterialDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOnStandardResourceDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.RoutingStepOutputMaterialDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.masterdata.internal.MdRoutingReadService;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/** 从 {@code md_*} 投影主计划 Routing 主数据模型（TODO-13 M5 · AC-MD-05）。 */
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
        return mdRouting.routingPathPrioritiesForProduct(productCode);
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

    /** @deprecated 使用 {@link #hasRoutingForProduct(String)}（M5 后仅读 md_*）。 */
    @Deprecated
    public static boolean hasRouting(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        return com.plantops.persistence.entity.MdRoutingEntity.listInWorkspace().stream()
                .anyMatch(r -> productCode.equals(r.productCode));
    }

    public boolean hasRoutingForProduct(String productCode) {
        return mdRouting.hasRouting(productCode);
    }

    private List<ProductRoutingSteps.Operation> masterDataOperations(String productCode, int pathPriority) {
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }
        return mdRouting.operationsForProduct(productCode, pathPriority);
    }

    private List<RoutingStepInputMaterialDto> projectInputMaterials(
            String stepId, String productCode, int pathPriority) {
        List<MdRoutingStepImEntity> mdInputs = mdRouting.inputMaterialsForFirstStep(productCode, pathPriority);
        if (mdInputs.isEmpty()) {
            return List.of();
        }
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

    private String resolveRoutingName(String productCode, int pathPriority) {
        String base = mdRouting.routingName(productCode, pathPriority);
        if (pathPriority > 1) {
            return base + " · 路径 " + pathPriority;
        }
        return base;
    }
}
