package com.plantops.knowledge;

import com.plantops.persistence.entity.KnowledgeOverlayEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import com.plantops.testsupport.SpecRef;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@SpecRef("AC-KN-02")
class KnowledgeResolverIntegrationTest {

    @Inject
    KnowledgeRegistry registry;

    @Inject
    KnowledgeResolver resolver;

    @Inject
    KnowledgeValidator validator;

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    WorkspaceContext workspaceContext;

    @Test
    void standardPackLoadsDefaultParameters() {
        KnowledgePack standard = registry.standardPack();
        assertEquals("plantops-standard-v1", standard.packId());
        assertEquals("7", standard.flatParameters().get("default_procurement_lead_time_days"));
        assertEquals("ortools", standard.flatParameters().get("planning_optimizer_engine"));
    }

    @Test
    void industryPackOverridesStandardDefaults() {
        KnowledgePack industry = registry.industryPack("DISCRETE_ASSEMBLY");
        assertTrue(industry != null);
        assertEquals("10", industry.flatParameters().get("default_procurement_lead_time_days"));
        assertEquals("true", industry.flatParameters().get("master_plan_material_constraint_enabled"));
    }

    @Test
    @TestTransaction
    void effectiveKnowledgeMergesCustomOverlayOverIndustry() {
        WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(workspaceContext.getWorkspaceId());
        ws.industryId = "DISCRETE_ASSEMBLY";
        ws.persist();

        KnowledgeOverlayEntity overlay = new KnowledgeOverlayEntity();
        overlay.workspaceId = ws.workspaceId;
        overlay.overlayKey = "default_procurement_lead_time_days";
        overlay.overlayValue = "14";
        overlay.source = "CUSTOM";
        overlay.persist();

        EffectiveKnowledge effective = resolver.resolve(ws.workspaceId, ws.industryId, List.of(overlay), Map.of());
        assertEquals("14", effective.getString("default_procurement_lead_time_days"));
    }

    @Test
    void validatorRejectsHardRuleOverlay() {
        assertThrows(BadRequestException.class, () -> validator.validateOverlayKey("RULE-WS-01"));
    }

    @Test
    @TestTransaction
    void parameterRegistryReadsEffectiveKnowledge() {
        WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(workspaceContext.getWorkspaceId());
        ws.industryId = "DISCRETE_ASSEMBLY";
        ws.persist();
        knowledgeContext.invalidate(ws.workspaceId);

        String leadTime = knowledgeContext.getParameter("default_procurement_lead_time_days");
        assertEquals("10", leadTime);
    }
}
