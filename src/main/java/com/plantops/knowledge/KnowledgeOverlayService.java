package com.plantops.knowledge;

import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgeOverlayDto;
import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgeOverlayUpsertRequest;
import com.plantops.iam.context.SecurityContext;
import com.plantops.persistence.entity.KnowledgeOverlayEntity;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class KnowledgeOverlayService {

    @Inject
    KnowledgeValidator validator;

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    SecurityContext securityContext;

    public List<KnowledgeOverlayDto> listOverlays() {
        return KnowledgeOverlayEntity.listInWorkspace(workspaceContext.getWorkspaceId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public KnowledgeOverlayDto upsert(KnowledgeOverlayUpsertRequest request) {
        validator.validateOverlayKey(request.overlayKey());
        String workspaceId = workspaceContext.getWorkspaceId();
        KnowledgeOverlayEntity entity = KnowledgeOverlayEntity.findByKey(workspaceId, request.overlayKey().trim());
        if (entity == null) {
            entity = new KnowledgeOverlayEntity();
            entity.workspaceId = workspaceId;
            entity.overlayKey = request.overlayKey().trim();
        }
        entity.overlayValue = request.overlayValue();
        entity.source = "CUSTOM";
        entity.updatedBy = securityContext.getCurrentUserId();
        entity.updatedAt = LocalDateTime.now();
        entity.persist();
        knowledgeContext.invalidate(workspaceId);
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeOverlayEntity entity = KnowledgeOverlayEntity.findById(id);
        if (entity == null || !workspaceContext.getWorkspaceId().equals(entity.workspaceId)) {
            throw new NotFoundException("overlay not found: " + id);
        }
        entity.delete();
        knowledgeContext.invalidate(entity.workspaceId);
    }

    private KnowledgeOverlayDto toDto(KnowledgeOverlayEntity entity) {
        return new KnowledgeOverlayDto(
                entity.id,
                entity.overlayKey,
                entity.overlayValue,
                entity.source,
                entity.updatedBy,
                entity.updatedAt);
    }
}
