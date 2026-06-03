package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "master_field_definition", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "entity_type", "field_key"
}))
public class MasterFieldDefinitionEntity extends WorkspaceScopedEntity {

    @Column(name = "entity_type", nullable = false, length = 64)
    public String entityType;

    @Column(name = "field_key", nullable = false, length = 128)
    public String fieldKey;

    @Column(name = "field_category", nullable = false, length = 16)
    public String fieldCategory;

    @Column(name = "data_type", nullable = false, length = 16)
    public String dataType;

    @Column(name = "label_zh", nullable = false, length = 256)
    public String labelZh;

    @Column(nullable = false)
    public boolean required;

    @Column(name = "visible_in_grid", nullable = false)
    public boolean visibleInGrid = true;

    @Column(name = "used_in_rules", nullable = false)
    public boolean usedInRules;

    @Column(name = "display_order", nullable = false)
    public int displayOrder;

    @Column(nullable = false, length = 16)
    public String source = "PLATFORM";

    public static List<MasterFieldDefinitionEntity> listByEntityType(String entityType) {
        return list("workspaceId = ?1 and entityType = ?2", ws(), entityType).stream()
                .map(MasterFieldDefinitionEntity.class::cast)
                .sorted(Comparator
                        .comparing((MasterFieldDefinitionEntity e) -> e.fieldCategory)
                        .thenComparing(e -> e.displayOrder)
                        .thenComparing(e -> e.fieldKey))
                .toList();
    }

    public static boolean existsForEntity(String entityType) {
        return count("workspaceId = ?1 and entityType = ?2", ws(), entityType) > 0;
    }

    public static MasterFieldDefinitionEntity findByEntityAndKey(String entityType, String fieldKey) {
        return find("workspaceId = ?1 and entityType = ?2 and fieldKey = ?3", ws(), entityType, fieldKey)
                .firstResult();
    }
}
