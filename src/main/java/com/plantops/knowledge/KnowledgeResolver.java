package com.plantops.knowledge;

import com.plantops.persistence.entity.KnowledgeOverlayEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** merge Standard + Industry + Custom overlay（Custom > Industry > Standard）。 */
@ApplicationScoped
public class KnowledgeResolver {

    @Inject
    KnowledgeRegistry registry;

    @Inject
    KnowledgeValidator validator;

    public EffectiveKnowledge resolve(
            String workspaceId,
            String industryId,
            List<KnowledgeOverlayEntity> overlays,
            Map<String, String> workspaceParameters) {
        Map<String, ResolvedKnowledgeValue> merged = new LinkedHashMap<>();

        KnowledgePack standard = registry.standardPack();
        applyLayer(merged, standard.flatParameters(), KnowledgeLayer.STANDARD);

        KnowledgePack industry = registry.industryPack(industryId);
        if (industry != null) {
            applyLayer(merged, industry.flatParameters(), KnowledgeLayer.INDUSTRY);
        }

        if (overlays != null) {
            for (KnowledgeOverlayEntity overlay : overlays) {
                validator.validateOverlayKey(overlay.overlayKey);
                merged.put(
                        overlay.overlayKey,
                        new ResolvedKnowledgeValue(overlay.overlayKey, overlay.overlayValue, KnowledgeLayer.CUSTOM));
            }
        }

        if (workspaceParameters != null) {
            for (Map.Entry<String, String> entry : workspaceParameters.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                merged.put(
                        entry.getKey(),
                        new ResolvedKnowledgeValue(entry.getKey(), entry.getValue(), KnowledgeLayer.CUSTOM));
            }
        }

        return new EffectiveKnowledge(
                workspaceId,
                industryId,
                standard.packId(),
                industry != null ? industry.version() : null,
                Map.copyOf(merged));
    }

    private static void applyLayer(
            Map<String, ResolvedKnowledgeValue> merged, Map<String, String> layerValues, KnowledgeLayer layer) {
        for (Map.Entry<String, String> entry : layerValues.entrySet()) {
            merged.put(entry.getKey(), new ResolvedKnowledgeValue(entry.getKey(), entry.getValue(), layer));
        }
    }
}
