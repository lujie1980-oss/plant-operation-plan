package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.persistence.entity.ProductResourceEntity;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** 从 {@link OperationOnStandardResource} 派生求解资源域与时间分量（D32）。 */
public final class OperationResourceBinding {

    private OperationResourceBinding() {
    }

    public static List<OperationOnStandardResource> bindingsFor(OntologyGraph graph, String operationId) {
        return graph.operationsOnStandardResourceFor(operationId);
    }

    public static String primaryResourceId(OntologyGraph graph, String operationId) {
        List<OperationOnStandardResource> bindings = bindingsFor(graph, operationId);
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.get(0).getStandardResourceId();
    }

    public static List<String> allowedResourceIds(OntologyGraph graph, String operationId) {
        return bindingsFor(graph, operationId).stream()
                .map(OperationOnStandardResource::getStandardResourceId)
                .toList();
    }

    public static OperationOnStandardResource primaryBinding(OntologyGraph graph, String operationId) {
        List<OperationOnStandardResource> bindings = bindingsFor(graph, operationId);
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    /** 纯加工秒数：quantity × processTimeSeconds（主 OOSR）。 */
    public static long productionDurationSeconds(OperationOnStandardResource primary, double quantity) {
        if (primary == null || quantity <= 0) {
            return 0;
        }
        return Math.max(0, Math.round(primary.getProcessTimeSeconds() * quantity));
    }

    /** 换型/setup（秒），计入 preprocessingTime，不占槽位产能。 */
    public static long preprocessingDurationSeconds(OperationOnStandardResource primary) {
        if (primary == null) {
            return 0;
        }
        return Math.max(0, (long) primary.getSetupTimeMinutes() * 60);
    }

    public static void applyPrimaryTiming(Operation operation, OperationOnStandardResource primary, double quantity) {
        operation.setProductionDuration(productionDurationSeconds(primary, quantity));
        operation.setPreprocessingTime(preprocessingDurationSeconds(primary));
    }

    public static Comparator<OperationOnStandardResource> byPriority() {
        return Comparator
                .comparingInt(OperationOnStandardResource::getResourcePriority)
                .thenComparing(OperationOnStandardResource::getId);
    }

    public static int defaultPriority(Integer resourcePriority) {
        return resourcePriority != null ? resourcePriority : ProductResourceEntity.DEFAULT_RESOURCE_PRIORITY;
    }

    public static double processTimeSeconds(BigDecimal processTimeSeconds) {
        return processTimeSeconds != null ? processTimeSeconds.doubleValue() : 0.0;
    }
}
