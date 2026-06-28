-- TODO-18 IAM M1: app_user + workspace 扩展 + 成员/模块/权限/审计

CREATE TABLE app_user (
    user_id         VARCHAR(50)  NOT NULL PRIMARY KEY,
    login_name      VARCHAR(100) NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    password_hash   VARCHAR(200),
    is_super_admin  BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_login UNIQUE (login_name)
);

INSERT INTO app_user (user_id, login_name, display_name, is_super_admin, status, created_at)
VALUES ('dev', 'dev', 'Dev', TRUE, 'ACTIVE', CURRENT_TIMESTAMP);

ALTER TABLE workspace ADD COLUMN owner_user_id VARCHAR(50) DEFAULT 'dev';
ALTER TABLE workspace ADD COLUMN workspace_type VARCHAR(20) DEFAULT 'SHARED';
UPDATE workspace SET owner_user_id = 'dev' WHERE owner_user_id IS NULL;
UPDATE workspace SET workspace_type = 'SHARED' WHERE workspace_type IS NULL;

CREATE TABLE workspace_member (
    workspace_id VARCHAR(64) NOT NULL,
    user_id      VARCHAR(50) NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    CONSTRAINT pk_workspace_member PRIMARY KEY (workspace_id, user_id)
);

CREATE TABLE workspace_enabled_module (
    workspace_id VARCHAR(64) NOT NULL,
    module_id    VARCHAR(20) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_wks_enabled_module PRIMARY KEY (workspace_id, module_id)
);

CREATE TABLE workspace_enabled_adapter (
    workspace_id VARCHAR(64) NOT NULL,
    adapter_id   VARCHAR(30) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_wks_enabled_adapter PRIMARY KEY (workspace_id, adapter_id)
);

CREATE TABLE workspace_member_module (
    workspace_id VARCHAR(64) NOT NULL,
    user_id      VARCHAR(50) NOT NULL,
    module_id    VARCHAR(20) NOT NULL,
    access_level VARCHAR(10) NOT NULL DEFAULT 'NONE',
    CONSTRAINT pk_wks_member_module PRIMARY KEY (workspace_id, user_id, module_id)
);

CREATE TABLE iam_audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id VARCHAR(50)  NOT NULL,
    action        VARCHAR(100) NOT NULL,
    target_type   VARCHAR(50),
    target_id     VARCHAR(200),
    payload_json  TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
