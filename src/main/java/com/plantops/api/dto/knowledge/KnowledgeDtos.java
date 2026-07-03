package com.plantops.api.dto.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {}

    public record KnowledgeContextDto(
            String workspaceId,
            String industryId,
            String standardPackId,
            String industryPackVersion,
            List<ResolvedValueDto> parameters) {}

    public record ResolvedValueDto(String key, String value, String layer) {}

    public record KnowledgeOverlayDto(
            Long id, String overlayKey, String overlayValue, String source, String updatedBy, LocalDateTime updatedAt) {}

    public record KnowledgeOverlayUpsertRequest(String overlayKey, String overlayValue) {}

    public record KnowledgePackInfoDto(String packId, String version, String layer) {}

    public record IndustryInstallResultDto(
            String workspaceId,
            String industryId,
            String packId,
            String packVersion,
            boolean seededMaterialLeadTimeWildcard) {}
}
