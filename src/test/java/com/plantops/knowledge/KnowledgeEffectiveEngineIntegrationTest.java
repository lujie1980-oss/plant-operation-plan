package com.plantops.knowledge;

import com.plantops.testsupport.SpecRef;
import com.plantops.persistence.entity.MaterialLeadTimeRuleEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@SpecRef("AC-KN-01")
class KnowledgeEffectiveEngineIntegrationTest {

    @Inject
    MaterialLeadTimeKnowledgeService materialLeadTimeKnowledge;

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    ReservationAutoPolicyService reservationAutoPolicy;

    @Test
    @TestTransaction
    void materialLeadTimeUsesIndustryEffectiveDefault() {
        WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(workspaceContext.getWorkspaceId());
        ws.industryId = "DISCRETE_ASSEMBLY";
        ws.persist();
        knowledgeContext.invalidate(ws.workspaceId);

        MaterialLeadTimeRuleEntity.delete("workspaceId = ?1", ws.workspaceId);
        assertEquals(10, materialLeadTimeKnowledge.leadTimeDaysForProduct("RM-UNKNOWN"));
    }

    @Test
    @TestTransaction
    void reservationPolicyReadsEffectiveOverlay() {
        WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(workspaceContext.getWorkspaceId());
        knowledgeContext.invalidate(ws.workspaceId);
        assertEquals(ReservationAutoPolicyService.PolicyMode.DEFAULT, reservationAutoPolicy.mode());

        var overlay = new com.plantops.persistence.entity.KnowledgeOverlayEntity();
        overlay.workspaceId = ws.workspaceId;
        overlay.overlayKey = "reservation_auto_policy";
        overlay.overlayValue = "DATE_FIRST";
        overlay.source = "CUSTOM";
        overlay.persist();
        knowledgeContext.invalidate(ws.workspaceId);

        assertEquals(ReservationAutoPolicyService.PolicyMode.DATE_FIRST, reservationAutoPolicy.mode());
    }

    @Test
    @TestTransaction
    void industryInstallApiBindsPackAndSeedsWildcardLeadTime() {
        MaterialLeadTimeRuleEntity.delete("workspaceId = ?1", workspaceContext.getWorkspaceId());

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/v1/knowledge/industry/DISCRETE_ASSEMBLY/install")
                .then()
                .statusCode(200)
                .body("industryId", org.hamcrest.Matchers.equalTo("DISCRETE_ASSEMBLY"))
                .body("seededMaterialLeadTimeWildcard", org.hamcrest.Matchers.equalTo(true));

        MaterialLeadTimeRuleEntity wildcard = MaterialLeadTimeRuleEntity.findByProduct("*");
        assertTrue(wildcard != null);
        assertEquals(10, wildcard.leadTimeDays);
        assertEquals(10, materialLeadTimeKnowledge.leadTimeDaysForProduct("ANY-MAT"));
    }
}
