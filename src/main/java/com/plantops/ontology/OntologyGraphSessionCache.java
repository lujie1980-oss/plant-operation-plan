package com.plantops.ontology;

import jakarta.enterprise.context.RequestScoped;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 单次 HTTP 请求内复用已装载的本体图，避免 list + summary 重复 buildGraph。
 */
@RequestScoped
public class OntologyGraphSessionCache {

    private String cachedVersionId;
    private OntologyGraph cachedGraph;

    public OntologyGraph getOrLoad(String masterPlanVersionId, Supplier<OntologyGraph> loader) {
        String key = masterPlanVersionId == null || masterPlanVersionId.isBlank() ? "" : masterPlanVersionId;
        if (cachedGraph != null && Objects.equals(key, cachedVersionId)) {
            return cachedGraph;
        }
        cachedVersionId = key;
        cachedGraph = loader.get();
        return cachedGraph;
    }

    public void invalidate() {
        cachedGraph = null;
        cachedVersionId = null;
    }
}
