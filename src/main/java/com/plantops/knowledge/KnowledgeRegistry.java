package com.plantops.knowledge;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 注册 Standard + Industry packs（TODO-15 K0/K1）。 */
@ApplicationScoped
public class KnowledgeRegistry {

    @Inject
    KnowledgePackLoader loader;

    private volatile KnowledgePack standardPack;
    private final ConcurrentHashMap<String, KnowledgePack> industryPacks = new ConcurrentHashMap<>();

    void onStart(@Observes StartupEvent event) {
        standardPack = loader.loadStandardPack();
        industryPacks.put("DISCRETE_ASSEMBLY", loader.loadIndustryPack("DISCRETE_ASSEMBLY"));
    }

    public KnowledgePack standardPack() {
        KnowledgePack pack = standardPack;
        if (pack == null) {
            pack = loader.loadStandardPack();
            standardPack = pack;
        }
        return pack;
    }

    public KnowledgePack industryPack(String industryId) {
        if (industryId == null || industryId.isBlank()) {
            return null;
        }
        return industryPacks.computeIfAbsent(industryId, id -> {
            try {
                return loader.loadIndustryPack(id);
            } catch (IllegalStateException e) {
                return null;
            }
        });
    }

    public Map<String, KnowledgePack> industryPacks() {
        return Map.copyOf(industryPacks);
    }
}
