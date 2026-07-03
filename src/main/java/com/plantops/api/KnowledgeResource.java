package com.plantops.api;

import com.plantops.api.dto.knowledge.KnowledgeDtos.IndustryInstallResultDto;
import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgeContextDto;
import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgeOverlayDto;
import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgeOverlayUpsertRequest;
import com.plantops.api.dto.knowledge.KnowledgeDtos.KnowledgePackInfoDto;
import com.plantops.api.dto.knowledge.KnowledgeDtos.ResolvedValueDto;
import com.plantops.knowledge.EffectiveKnowledge;
import com.plantops.knowledge.KnowledgeContext;
import com.plantops.knowledge.KnowledgeIndustryInstallService;
import com.plantops.knowledge.KnowledgeOverlayService;
import com.plantops.knowledge.KnowledgePack;
import com.plantops.knowledge.KnowledgeRegistry;
import com.plantops.knowledge.ResolvedKnowledgeValue;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** §13 Knowledge API（TODO-15）。 */
@Path("/api/v1/knowledge")
@Produces(MediaType.APPLICATION_JSON)
public class KnowledgeResource {

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    KnowledgeRegistry knowledgeRegistry;

    @Inject
    KnowledgeOverlayService overlayService;

    @Inject
    KnowledgeIndustryInstallService industryInstallService;

    @GET
    @Path("/context")
    public KnowledgeContextDto context() {
        EffectiveKnowledge effective = knowledgeContext.resolve();
        List<ResolvedValueDto> parameters = new ArrayList<>();
        for (Map.Entry<String, ResolvedKnowledgeValue> entry : effective.valuesByKey().entrySet()) {
            if (!entry.getKey().contains(".")) {
                parameters.add(new ResolvedValueDto(
                        entry.getKey(), entry.getValue().value(), entry.getValue().layer().name()));
            }
        }
        parameters.sort(Comparator.comparing(ResolvedValueDto::key));
        return new KnowledgeContextDto(
                effective.workspaceId(),
                effective.industryId(),
                effective.standardPackId(),
                effective.industryPackVersion(),
                parameters);
    }

    @GET
    @Path("/packs")
    public List<KnowledgePackInfoDto> packs() {
        List<KnowledgePackInfoDto> result = new ArrayList<>();
        KnowledgePack standard = knowledgeRegistry.standardPack();
        result.add(new KnowledgePackInfoDto(standard.packId(), standard.version(), standard.layer().name()));
        for (KnowledgePack industry : knowledgeRegistry.industryPacks().values()) {
            result.add(new KnowledgePackInfoDto(industry.packId(), industry.version(), industry.layer().name()));
        }
        return result;
    }

    @GET
    @Path("/overlays")
    public List<KnowledgeOverlayDto> listOverlays() {
        return overlayService.listOverlays();
    }

    @POST
    @Path("/overlays")
    @Consumes(MediaType.APPLICATION_JSON)
    public KnowledgeOverlayDto upsertOverlay(KnowledgeOverlayUpsertRequest request) {
        return overlayService.upsert(request);
    }

    @DELETE
    @Path("/overlays/{id}")
    public void deleteOverlay(@PathParam("id") Long id) {
        overlayService.delete(id);
    }

    @POST
    @Path("/industry/{industryId}/install")
    public IndustryInstallResultDto installIndustry(@PathParam("industryId") String industryId) {
        return industryInstallService.install(industryId);
    }
}
