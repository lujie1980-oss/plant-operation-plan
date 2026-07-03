package com.plantops.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

/** §13.5.3 CustomizedKnowledgeOverlay（TODO-15 K2）。 */
@Entity
@Table(name = "knowledge_overlay")
public class KnowledgeOverlayEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    public String workspaceId;

    @Column(name = "overlay_key", nullable = false, length = 256)
    public String overlayKey;

    @Column(name = "overlay_value", nullable = false, length = 4000)
    public String overlayValue;

    @Column(nullable = false, length = 16)
    public String source = "CUSTOM";

    @Column(name = "updated_by", length = 64)
    public String updatedBy;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public static List<KnowledgeOverlayEntity> listInWorkspace(String workspaceId) {
        return list("workspaceId", workspaceId);
    }

    public static KnowledgeOverlayEntity findByKey(String workspaceId, String overlayKey) {
        return find("workspaceId = ?1 and overlayKey = ?2", workspaceId, overlayKey).firstResult();
    }
}
