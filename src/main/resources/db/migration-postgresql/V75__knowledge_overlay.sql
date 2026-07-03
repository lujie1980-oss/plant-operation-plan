-- TODO-15 K1/K2: Workspace industry binding + CustomizedKnowledge overlay
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS industry_id VARCHAR(64);
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS knowledge_pack_version VARCHAR(32);

CREATE TABLE IF NOT EXISTS knowledge_overlay (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    overlay_key VARCHAR(256) NOT NULL,
    overlay_value VARCHAR(4000) NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'CUSTOM',
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_overlay_ws_key UNIQUE (workspace_id, overlay_key)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_overlay_ws ON knowledge_overlay (workspace_id);
