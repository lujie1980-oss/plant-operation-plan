package com.plantops.knowledge;

import com.plantops.api.dto.knowledge.KnowledgeDtos.IndustryInstallResultDto;
import com.plantops.persistence.entity.MaterialLeadTimeRuleEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import com.plantops.workspace.WorkspaceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/** K4：为 Workspace 绑定 Industry pack 并 seed 默认 MRP-04 行。 */
@ApplicationScoped
public class KnowledgeIndustryInstallService {

    @Inject
    KnowledgeRegistry registry;

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    WorkspaceContext workspaceContext;

    @Transactional
    public IndustryInstallResultDto install(String industryId) {
        if (industryId == null || industryId.isBlank()) {
            throw new BadRequestException("industryId required");
        }
        KnowledgePack pack = registry.industryPack(industryId);
        if (pack == null) {
            throw new NotFoundException("Unknown industry pack: " + industryId);
        }
        String workspaceId = workspaceContext.getWorkspaceId();
        WorkspaceEntity workspace = WorkspaceEntity.findByWorkspaceId(workspaceId);
        if (workspace == null) {
            throw new NotFoundException("Workspace not found: " + workspaceId);
        }
        workspace.industryId = industryId;
        workspace.persist();

        boolean seededLeadTime = seedDefaultLeadTimeRow(workspaceId, pack);
        knowledgeContext.invalidate(workspaceId);

        return new IndustryInstallResultDto(
                workspaceId, industryId, pack.packId(), pack.version(), seededLeadTime);
    }

    private static boolean seedDefaultLeadTimeRow(String workspaceId, KnowledgePack pack) {
        if (MaterialLeadTimeRuleEntity.findByProduct("*") != null) {
            return false;
        }
        String days = pack.flatParameters().get("business_rules_tabs.material-lead-time.default_lead_time_days");
        if (days == null || days.isBlank()) {
            days = pack.flatParameters().get("default_procurement_lead_time_days");
        }
        if (days == null || days.isBlank()) {
            return false;
        }
        int leadTime;
        try {
            leadTime = Math.max(0, Integer.parseInt(days.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
        MaterialLeadTimeRuleEntity row = new MaterialLeadTimeRuleEntity();
        row.productCode = "*";
        row.leadTimeDays = leadTime;
        row.ensureWorkspace();
        row.persist();
        return true;
    }
}
