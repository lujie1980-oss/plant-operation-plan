-- TODO-12 P5: Partial persistence policy (§5.16 · RULE-PERS-05)

CREATE TABLE ont_entity_policy (
    workspace_id   VARCHAR(64)  NOT NULL,
    entity_kind    VARCHAR(64)  NOT NULL,
    storage        VARCHAR(16)  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ont_entity_policy PRIMARY KEY (workspace_id, entity_kind),
    CONSTRAINT ck_ont_entity_policy_storage CHECK (storage IN ('STORE', 'DERIVE'))
);

CREATE INDEX idx_ont_entity_policy_ws ON ont_entity_policy (workspace_id);
