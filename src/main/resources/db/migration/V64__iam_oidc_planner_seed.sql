-- IAM OIDC 联调：Keycloak 用户 planner（login_name 与 preferred_username 一致）
INSERT INTO app_user (user_id, login_name, display_name, is_super_admin, status, created_at)
SELECT 'planner', 'planner', '计划员', FALSE, 'ACTIVE', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = 'planner');

INSERT INTO workspace_member (workspace_id, user_id, role)
SELECT 'jinghua', 'planner', 'MEMBER'
WHERE NOT EXISTS (
    SELECT 1 FROM workspace_member WHERE workspace_id = 'jinghua' AND user_id = 'planner'
);
