package com.plantops.masterdata.internal;

import com.plantops.persistence.entity.MdRoutingEntity;
import com.plantops.persistence.entity.MdRoutingStepEntity;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.persistence.entity.MdRoutingStepOsrEntity;
import com.plantops.scenario.ProductRoutingSteps;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 从 md_* 读取工艺路线（TODO-13 M4 · AC-MD-04）。 */
@ApplicationScoped
public class MdRoutingReadService {

    public boolean hasRouting(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        return MdRoutingEntity.listInWorkspace().stream().anyMatch(r -> productCode.equals(r.productCode));
    }

    public List<Integer> routingPathPrioritiesForProduct(String productCode) {
        return MdRoutingEntity.listInWorkspace().stream()
                .filter(r -> productCode.equals(r.productCode))
                .map(r -> r.pathPriority)
                .distinct()
                .sorted()
                .toList();
    }

    public List<ProductRoutingSteps.Operation> operationsForProduct(String productCode, int pathPriority) {
        List<MdRoutingEntity> routings = MdRoutingEntity.listInWorkspace().stream()
                .filter(r -> productCode.equals(r.productCode) && r.pathPriority == pathPriority)
                .sorted(Comparator.comparing(r -> r.routingCode))
                .toList();
        if (routings.isEmpty()) {
            return List.of();
        }
        MdRoutingEntity routing = routings.get(0);
        List<MdRoutingStepEntity> steps = MdRoutingStepEntity.listInWorkspace().stream()
                .filter(s -> routing.routingCode.equals(s.routingCode))
                .sorted(Comparator.comparingInt(s -> s.sequenceNo))
                .toList();
        if (steps.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<MdRoutingStepOsrEntity>> osrBySeq = MdRoutingStepOsrEntity.listInWorkspace().stream()
                .filter(o -> routing.routingCode.equals(o.routingCode))
                .collect(Collectors.groupingBy(o -> o.sequenceNo));

        List<ProductRoutingSteps.Operation> operations = new ArrayList<>(steps.size());
        for (MdRoutingStepEntity step : steps) {
            List<MdRoutingStepOsrEntity> osrs =
                    osrBySeq.getOrDefault(step.sequenceNo, List.of()).stream()
                            .sorted(Comparator.comparingInt(o -> o.resourcePriority))
                            .toList();
            List<ProductRoutingSteps.ResourceOption> options = new ArrayList<>(osrs.size());
            for (MdRoutingStepOsrEntity osr : osrs) {
                options.add(new ProductRoutingSteps.ResourceOption(
                        osr.standardResourceCode,
                        osr.resourcePriority,
                        osr.processTimeSeconds != null ? osr.processTimeSeconds : BigDecimal.valueOf(60),
                        osr.setupTimeMinutes));
            }
            String opName = step.operationName != null && !step.operationName.isBlank()
                    ? step.operationName
                    : "OP-" + step.sequenceNo;
            operations.add(new ProductRoutingSteps.Operation(step.sequenceNo, opName, options));
        }
        return operations;
    }

    public List<MdRoutingStepImEntity> inputMaterialsForFirstStep(String productCode, int pathPriority) {
        List<MdRoutingEntity> routings = MdRoutingEntity.listInWorkspace().stream()
                .filter(r -> productCode.equals(r.productCode) && r.pathPriority == pathPriority)
                .toList();
        if (routings.isEmpty()) {
            return List.of();
        }
        String routingCode = routings.get(0).routingCode;
        int minSeq = MdRoutingStepEntity.listInWorkspace().stream()
                .filter(s -> routingCode.equals(s.routingCode))
                .mapToInt(s -> s.sequenceNo)
                .min()
                .orElse(1);
        return MdRoutingStepImEntity.listInWorkspace().stream()
                .filter(im -> routingCode.equals(im.routingCode) && im.sequenceNo == minSeq)
                .toList();
    }

    public String routingName(String productCode, int pathPriority) {
        return MdRoutingEntity.listInWorkspace().stream()
                .filter(r -> productCode.equals(r.productCode) && r.pathPriority == pathPriority)
                .map(r -> r.name)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(productCode + " 工艺");
    }

    public Map<String, MdRoutingEntity> routingsByProduct(String productCode) {
        Map<String, MdRoutingEntity> byPath = new LinkedHashMap<>();
        for (MdRoutingEntity routing : MdRoutingEntity.listInWorkspace()) {
            if (!productCode.equals(routing.productCode)) {
                continue;
            }
            byPath.putIfAbsent(String.valueOf(routing.pathPriority), routing);
        }
        return byPath;
    }
}
