package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "rule_set_version", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "rule_set_version_id"
}))
public class RuleSetVersionEntity extends WorkspaceScopedEntity {

    public String ruleSetVersionId;
    public String name;
    public boolean isDefault;
    @Lob
    public String snapshotJson;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static RuleSetVersionEntity findById(String ruleSetVersionId) {
        return find("workspaceId = ?1 and ruleSetVersionId = ?2", ws(), ruleSetVersionId).firstResult();
    }

    public static List<RuleSetVersionEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by isDefault desc, createdAt asc", ws());
    }

    public static RuleSetVersionEntity findDefault() {
        RuleSetVersionEntity d = find("workspaceId = ?1 and isDefault = true", ws()).firstResult();
        if (d != null) {
            return d;
        }
        return listInWorkspace().stream().findFirst().orElse(null);
    }
}
