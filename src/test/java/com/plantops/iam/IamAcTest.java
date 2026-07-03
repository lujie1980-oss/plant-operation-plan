package com.plantops.iam;

import com.plantops.iam.entity.AppUserEntity;
import com.plantops.iam.entity.IamAuditLogEntity;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.iam.entity.WorkspaceMemberModuleEntity;
import com.plantops.iam.service.JwtTokenService;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.testsupport.SpecRef;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Automated checks for AC-IAM-01 ~ AC-IAM-06 (§8 · ADR-13).
 */
@QuarkusTest
@SpecRef({"AC-IAM-01", "AC-IAM-02", "AC-IAM-03", "AC-IAM-04", "AC-IAM-05", "AC-IAM-06"})
class IamAcTest {

    private static final String JINGHUA = "jinghua";
    private static final String OUTSIDER = "iam-ac-outsider";
    private static final String NEW_USER = "iam-ac-new";

    @Inject
    JwtTokenService jwtTokenService;

    @BeforeEach
    void seedUsers() {
        QuarkusTransaction.requiringNew().run(() -> {
            upsertUser(OUTSIDER, "outsider", false);
            upsertUser(NEW_USER, "new-user", false);
            WorkspaceMemberEntity.delete("userId", OUTSIDER);
            WorkspaceMemberEntity.delete("userId", NEW_USER);
            WorkspaceMemberModuleEntity.delete("workspaceId = ?1 and userId = ?2", JINGHUA, "planner");
            ensureModuleEnabled(JINGHUA, "MOD-SLT", true);
        });
    }

    @Test
    void acIam01_manualWorkspaceCreateAddsOwnerMembership() {
        String wsId = "iam-ac-ws-" + System.currentTimeMillis();
        String token = bearer(NEW_USER, false);

        given()
            .header("Authorization", token)
            .when()
            .get("/api/v1/iam/me")
            .then()
            .statusCode(200)
            .body("hasWorkspaces", equalTo(false));

        given()
            .header("Authorization", token)
            .contentType(ContentType.JSON)
            .body("""
                {"id":"%s","name":"IAM AC Test WS","description":"ac-iam-01"}
                """.formatted(wsId))
            .when()
            .post("/api/v1/workspaces")
            .then()
            .statusCode(201);

        given()
            .header("Authorization", token)
            .when()
            .get("/api/v1/iam/me")
            .then()
            .statusCode(200)
            .body("hasWorkspaces", equalTo(true))
            .body("workspaces.workspaceId", hasItem(wsId));

        QuarkusTransaction.requiringNew().run(() -> {
            WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                    "workspaceId = ?1 and userId = ?2", wsId, NEW_USER).firstResult();
            org.junit.jupiter.api.Assertions.assertNotNull(member);
            org.junit.jupiter.api.Assertions.assertEquals("OWNER", member.role);
            WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(wsId);
            org.junit.jupiter.api.Assertions.assertNotNull(ws);
            org.junit.jupiter.api.Assertions.assertEquals(NEW_USER, ws.ownerUserId);
        });
    }

    @Test
    void acIam02_nonMemberWorkspaceHeaderReturns403() {
        given()
            .header("Authorization", bearer(OUTSIDER, false))
            .header("X-Workspace-Id", JINGHUA)
            .when()
            .get("/api/v1/slitting/master-rolls")
            .then()
            .statusCode(403)
            .body("error", equalTo("WORKSPACE_FORBIDDEN"));
    }

    @Test
    void acIam03_disabledModuleReturns403() {
        QuarkusTransaction.requiringNew().run(() -> ensureModuleEnabled(JINGHUA, "MOD-SLT", false));

        given()
            .header("Authorization", bearer("planner", false))
            .header("X-Workspace-Id", JINGHUA)
            .when()
            .get("/api/v1/slitting/master-rolls")
            .then()
            .statusCode(403)
            .body("error", equalTo("MODULE_DISABLED"));
    }

    @Test
    void acIam04_viewOnlyModuleBlocksWrite() {
        QuarkusTransaction.requiringNew().run(() -> {
            WorkspaceMemberModuleEntity row = new WorkspaceMemberModuleEntity();
            row.workspaceId = JINGHUA;
            row.userId = "planner";
            row.moduleId = "MOD-OCP";
            row.accessLevel = "VIEW";
            row.persist();
        });

        given()
            .header("Authorization", bearer("planner", false))
            .header("X-Workspace-Id", JINGHUA)
            .contentType(ContentType.JSON)
            .body("{\"scenarioId\":\"default\"}")
            .when()
            .post("/api/v1/planning/master-plan/preview")
            .then()
            .statusCode(403)
            .body("error", equalTo("MODULE_FORBIDDEN"));
    }

    @Test
    void acIam05_superAdminCreateUserWritesAudit() {
        String newId = "iam-ac-admin-" + System.currentTimeMillis();
        given()
            .header("Authorization", bearer("dev", true))
            .contentType(ContentType.JSON)
            .body("""
                {
                  "userId":"%s",
                  "loginName":"%s",
                  "displayName":"AC Test",
                  "password":"test1234",
                  "isSuperAdmin":false
                }
                """.formatted(newId, newId))
            .when()
            .post("/api/v1/admin/users")
            .then()
            .statusCode(200)
            .body("userId", equalTo(newId));

        QuarkusTransaction.requiringNew().run(() -> {
            long count = IamAuditLogEntity.count(
                    "actorUserId = ?1 and action = ?2 and targetType = ?3 and targetId = ?4",
                    "dev", "CREATE_USER", "USER", newId);
            org.junit.jupiter.api.Assertions.assertTrue(count >= 1);
        });
    }

    @Test
    void acIam06_unknownModuleIdRejected() {
        given()
            .header("Authorization", bearer("dev", true))
            .header("X-Workspace-Id", JINGHUA)
            .contentType(ContentType.JSON)
            .body("""
                {"modules":[{"moduleId":"MOD-UNREGISTERED","enabled":true}]}
                """)
            .when()
            .put("/api/v1/iam/workspaces/" + JINGHUA + "/modules")
            .then()
            .statusCode(400);
    }

    @Test
    void acIam01_meListsOnlyMemberWorkspacesNotAllSeed() {
        given()
            .header("Authorization", bearer(OUTSIDER, false))
            .when()
            .get("/api/v1/iam/me")
            .then()
            .statusCode(200)
            .body("workspaces.size()", equalTo(0));
    }

    private String bearer(String userId, boolean superAdmin) {
        return "Bearer " + jwtTokenService.issue(userId, userId, superAdmin);
    }

    private static void upsertUser(String userId, String loginName, boolean superAdmin) {
        AppUserEntity user = AppUserEntity.findById(userId);
        if (user == null) {
            user = new AppUserEntity();
            user.userId = userId;
            user.loginName = loginName;
            user.displayName = loginName;
            user.passwordHash = "unused";
            user.superAdmin = superAdmin;
            user.status = "ACTIVE";
            user.persist();
        }
    }

    private static void ensureModuleEnabled(String workspaceId, String moduleId, boolean enabled) {
        WorkspaceEnabledModuleEntity row = WorkspaceEnabledModuleEntity.find(
                "workspaceId = ?1 and moduleId = ?2", workspaceId, moduleId).firstResult();
        if (row == null) {
            row = new WorkspaceEnabledModuleEntity();
            row.workspaceId = workspaceId;
            row.moduleId = moduleId;
            row.enabled = enabled;
            row.persist();
        } else {
            row.enabled = enabled;
        }
    }
}
